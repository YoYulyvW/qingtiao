package com.autoskip.helper.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.autoskip.helper.service.DramaDebug
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SoftwareVideoDecoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 远程控制服务（前台 Service）。
 *
 * 流程：
 *   1. 启动时连接 app_process 的 TCP（读 H.264 流）
 *   2. WebRTC 初始化：创建 PeerConnection + DataChannel
 *   3. WebSocket 连服务端信令（/ws/signal?deviceId=X&role=device）
 *   4. iOS 端发起 → 服务端转发 Offer → 本机 SetRemoteDescription
 *   5. 本机 CreateAnswer → 发送回 iOS → ICE 交换
 *   6. DataChannel 打开后：把 H.264 帧分片发给 iOS
 *   7. 接收 DataChannel 的触控指令 → su input
 *
 * 关键设计：
 *   - 用 DataChannel 而不是 VideoTrack（VideoTrack 不接受外部 H.264）
 *   - DataChannel 配置 unordered + maxRetransmits=0（不重传，接近 UDP）
 *   - H.264 帧按 ~16KB 分片（UDP MTU 限制）
 */
class RemoteControlService : Service() {
    companion object {
        private const val TAG = "RemoteControlService"
        private const val CHANNEL_ID = "rc_service"
        private const val NOTIF_ID = 1001
        private const val CAPTURE_PORT = 27183
        private const val H264_CHUNK_SIZE = 16 * 1024

        @Volatile private var instance: RemoteControlService? = null
        fun isRunning(): Boolean = instance != null
    }

    private val worker = Executors.newFixedThreadPool(4)
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var signalingClient: SignalingClient? = null
    private var captureClient: CaptureClient? = null
    private var deviceId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("deviceId") ?: ""
        val signalUrl = intent?.getStringExtra("signalUrl") ?: ""
        val turnJson = intent?.getStringExtra("turnConfig") ?: ""

        if (id.isEmpty() || signalUrl.isEmpty()) {
            Log.e(TAG, "缺少 deviceId 或 signalUrl")
            stopSelf()
            return START_NOT_STICKY
        }

        deviceId = id
        startForeground(NOTIF_ID, buildNotification("远程控制已启动"))
        log("启动：device=$id signal=$signalUrl")

        worker.execute {
            try {
                // 1) 通过 su 后台启动 app_process 捕获进程
                startCaptureProcess()
                // 2) 等 2 秒让捕获进程初始化（app_process 启动 + VirtualDisplay 创建）
                Thread.sleep(2000)
                // 3) 初始化 WebRTC
                initWebRTC()
                // 4) 连接本地 TCP（读 H.264 流）
                startCaptureClient()
                // 5) 连接信令
                startSignaling(signalUrl)
            } catch (e: Exception) {
                log("★ 启动失败：" + e.message)
                e.printStackTrace()
                stopSelf()
            }
        }
        return START_STICKY
    }

    /**
     * ★ 通过 su 启动 app_process 捕获进程（生产模式：port=27183，非 POC）。
     *
     * 关键点：
     *   - 用 & 让 app_process 在后台运行，su 立即返回
     *   - app_process 输出的日志写到 logcat（Log.i）而不是 stdout
     *   - 该进程独立于本 Service 生命周期（Service 停止时需手动 kill）
     *
     * 关于 APK 路径：CLASSPATH 需指向当前安装的 APK（动态路径）
     */
    private fun startCaptureProcess() {
        // 拿到 APK 路径
        val apkPath = try {
            packageManager.getApplicationInfo(packageName, 0).sourceDir
        } catch (e: Exception) {
            log("★ 获取 APK 路径失败：" + e.message)
            ""
        }
        if (apkPath.isEmpty()) return

        // 检查是否已有捕获进程在跑（通过 TCP 端口探测）
        if (isPortOpen(27183)) {
            log("捕获端口已占用，跳过启动（可能已有进程）")
            return
        }

        val cmd = "CLASSPATH=$apkPath app_process /system/bin com.autoskip.helper.capture.CaptureMain port=27183 >/dev/null 2>&1 &"
        log("启动 app_process：$cmd")
        // 用 su -c 执行，加 & 后台运行（不阻塞）
        val r = com.autoskip.helper.root.RootShell.exec(cmd, 5000)
        log("su 返回：退出码=${r.exitCode} stdout=${r.stdout.take(200)}")
    }

    /** 探测本地端口是否已打开 */
    private fun isPortOpen(port: Int): Boolean {
        return try {
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress("127.0.0.1", port), 500)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    // ===== WebRTC 初始化 =====
    private fun initWebRTC() {
        log("初始化 WebRTC…")
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val opts = PeerConnectionFactory.Options()
        val f = PeerConnectionFactory.builder()
            .setOptions(opts)
            .setVideoDecoderFactory(SoftwareVideoDecoderFactory())
            .setVideoEncoderFactory(SoftwareVideoEncoderFactory())
            .createPeerConnectionFactory()
        factory = f
        log("WebRTC 已初始化")
    }

    // ===== 创建 PeerConnection =====
    private fun createPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        log("创建 PeerConnection，ICE 服务器数：${iceServers.size}")
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peerConnection = factory?.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) {
                log("ICE 候选：" + c.sdpMid + " " + c.sdpMLineIndex)
                signalingClient?.sendIce(c)
            }
            override fun onDataChannel(dc: DataChannel) {
                log("收到 DataChannel：" + dc.label())
                setupDataChannel(dc)
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                log("ICE 状态：" + state)
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                log("连接状态：" + state)
            }
            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(change: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<out MediaStream>) {}
        })

        // 创建数据通道（发送 H.264 用）
        val dcInit = DataChannel.Init().apply {
            ordered = false
            maxRetransmits = 0   // 不重传，丢帧不阻塞
            maxRetransmitTimeMs = -1
        }
        dataChannel = peerConnection?.createDataChannel("h264", dcInit)
        setupDataChannel(dataChannel!!)
        log("DataChannel(h264) 已创建")
    }

    // ===== DataChannel 事件 =====
    @Volatile private var dcSetup = false
    private fun setupDataChannel(dc: DataChannel) {
        if (dcSetup) return   // ★ 避免重复注册 observer
        dcSetup = true
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                log("DataChannel 状态：" + dc.state())
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                // 接收 iOS 的触控指令（JSON 文本或二进制）
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                val text = String(data, Charsets.UTF_8)
                log("收到消息：" + text.take(200))
                handleControlMessage(text)
            }
        })
    }

    // ===== 处理触控指令 =====
    //
    // iOS 端通过 DataChannel 发 JSON：
    //   {"type":"tap","x":100,"y":200}
    //   {"type":"swipe","x1":100,"y1":200,"x2":300,"y2":400,"durationMs":300}
    //   {"type":"key","code":"HOME|BACK|RECENT"}
    //   {"type":"text","text":"你好"}
    //
    // 坐标归一化：iOS 端把 [0,1] 坐标映射到屏幕像素后发来（绝对值）
    //
    // 性能考量：
    //   - 每次 su 启动 ~50ms，滑动会有明显延迟
    //   - 优化：滑动用 input swipe 单命令（不是 DOWN-MOVE-UP 三步）
    //   - 后续若需要"跟手"滑动，改用 sendevent 直写事件节点
    private fun handleControlMessage(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            val type = obj.optString("type")
            when (type) {
                "tap" -> {
                    val x = obj.optInt("x")
                    val y = obj.optInt("y")
                    worker.execute {
                        val ok = com.autoskip.helper.root.RootShell.execSilently("input tap $x $y", 3000)
                        log("tap($x,$y) → $ok")
                    }
                }
                "swipe" -> {
                    val x1 = obj.optInt("x1")
                    val y1 = obj.optInt("y1")
                    val x2 = obj.optInt("x2")
                    val y2 = obj.optInt("y2")
                    val dur = obj.optInt("durationMs", 300).coerceIn(50, 3000)
                    worker.execute {
                        val ok = com.autoskip.helper.root.RootShell.execSilently(
                            "input swipe $x1 $y1 $x2 $y2 $dur", 5000
                        )
                        log("swipe($x1,$y1 → $x2,$y2, ${dur}ms) → $ok")
                    }
                }
                "key" -> {
                    val code = obj.optString("code")
                    val keyEvent = when (code) {
                        "HOME" -> "KEYCODE_HOME"
                        "BACK" -> "KEYCODE_BACK"
                        "RECENT" -> "KEYCODE_APP_SWITCH"
                        "POWER" -> "KEYCODE_POWER"
                        "WAKEUP" -> "KEYCODE_WAKEUP"
                        else -> null
                    }
                    if (keyEvent != null) {
                        worker.execute {
                            val ok = com.autoskip.helper.root.RootShell.execSilently(
                                "input keyevent $keyEvent", 3000
                            )
                            log("key($code) → $ok")
                        }
                    }
                }
                "text" -> {
                    val text = obj.optString("text")
                    if (text.isNotEmpty()) {
                        // 注意：input text 只支持 ASCII；中文需用 ADBKeyBoard 或 IME
                        // 简化：转义空格
                        val escaped = text.replace(" ", "%s")
                        worker.execute {
                            val ok = com.autoskip.helper.root.RootShell.execSilently(
                                "input text '$escaped'", 5000
                            )
                            log("text($text) → $ok")
                        }
                    }
                }
                else -> log("未知触控类型：$type")
            }
        } catch (e: Exception) {
            log("解析触控失败：" + e.message + " raw=" + json.take(100))
        }
    }

    // ===== 连接 app_process 的捕获流 =====
    private fun startCaptureClient() {
        log("连接 app_process 捕获端口 $CAPTURE_PORT…")
        captureClient = CaptureClient(CAPTURE_PORT).apply {
            onFrame = { frame ->
                sendFrameToDataChannel(frame)
            }
            onError = { err ->
                log("捕获流错误：" + err)
            }
        }
        captureClient?.connect()
    }

    // ===== 把 H.264 帧分片发出 =====
    //
    // ★ DataChannel 协议（主进程 → iOS）：
    //   [4B frame_id][4B pts_lo][1B flags][2B chunk_idx][2B chunk_total][2B chunk_len][N B data]
    //   = 15 字节头 + 数据
    //
    // 简化说明：
    //   - pts_us 只发低 32 位（每 71 分钟回绕一次，iOS 端检测大跳跃自行校正）
    //   - flags 与 CaptureServer 一致（bit0=关键帧，bit1=codec config）
    //   - chunk_idx 从 0 开始，chunk_total 是本帧总片数
    //
    // iOS 端拼帧：按 frame_id 聚合，收齐 chunk_total 片后交给解码器
    private fun sendFrameToDataChannel(frame: CaptureClient.Frame) {
        val dc = dataChannel ?: return
        if (dc.state() != DataChannel.State.OPEN) return

        val frameId = nextFrameId++  // 自增
        val ptsLo = (frame.ptsUs and 0xFFFFFFFFL).toInt()
        val totalChunks = ((frame.data.size + H264_CHUNK_SIZE - 1) / H264_CHUNK_SIZE).coerceAtLeast(1)

        var chunkIdx = 0
        var offset = 0
        while (offset < frame.data.size) {
            val chunkLen = minOf(H264_CHUNK_SIZE, frame.data.size - offset)
            val headerLen = 4 + 4 + 1 + 2 + 2 + 2   // 15
            val buf = ByteBuffer.allocateDirect(headerLen + chunkLen)
            buf.putInt(frameId)
            buf.putInt(ptsLo)
            buf.put(frame.flags.toByte())
            buf.putShort(chunkIdx.toShort())
            buf.putShort(totalChunks.toShort())
            buf.putShort(chunkLen.toShort())
            buf.put(frame.data, offset, chunkLen)
            buf.flip()
            dc.send(DataChannel.Buffer(buf, true))
            offset += chunkLen
            chunkIdx++
        }
    }
    private var nextFrameId: Int = 1

    // ===== 连接信令 WebSocket =====
    private fun startSignaling(url: String) {
        signalingClient = SignalingClient(url, deviceId, object : SignalingClient.Callback {
            override fun onConnected() {
                log("信令已连接")
            }
            override fun onOffer(sdp: String) {
                log("收到 Offer（${sdp.length} 字节）")
                // 解析 ICE 服务器？暂时用默认（可从信令配置下发）
                // 这里先创建 PeerConnection（空 ICE 服务器，靠 TURN 配置后续下发）
                if (peerConnection == null) {
                    createPeerConnection(emptyList())
                }
                val desc = SessionDescription(SessionDescription.Type.OFFER, sdp)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(s: SessionDescription?) {}
                    override fun onSetSuccess() {
                        log("RemoteDescription 设置成功，创建 Answer…")
                        createAnswer()
                    }
                    override fun onCreateFailure(e: String?) {
                        log("★ RemoteDescription 失败：" + e)
                    }
                    override fun onSetFailure(e: String?) {
                        log("★ setRemoteDescription 失败：" + e)
                    }
                }, desc)
            }
            override fun onIce(candidate: IceCandidate) {
                peerConnection?.addIceCandidate(candidate)
            }
            override fun onClose() {
                log("信令断开")
            }
            override fun onError(err: String) {
                log("★ 信令错误：" + err)
            }
        })
        signalingClient?.connect()
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) return
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(s: SessionDescription?) {}
                    override fun onSetSuccess() {
                        log("Answer 设置成功，发送…")
                        signalingClient?.sendAnswer(sdp.description)
                    }
                    override fun onCreateFailure(e: String?) {}
                    override fun onSetFailure(e: String?) {
                        log("★ setLocalDescription 失败：" + e)
                    }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(e: String?) {
                log("★ createAnswer 失败：" + e)
            }
            override fun onSetFailure(e: String?) {}
        }, constraints)
    }

    // ===== 工具 =====
    private fun log(s: String) {
        Log.i(TAG, s)
        DramaDebug.add("[远控] " + s)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("远程控制")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "远程控制", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        instance = null
        log("服务停止")
        try { dataChannel?.close() } catch (_: Exception) {}
        try { peerConnection?.close() } catch (_: Exception) {}
        try { factory?.dispose() } catch (_: Exception) {}
        try { signalingClient?.close() } catch (_: Exception) {}
        try { captureClient?.close() } catch (_: Exception) {}
        worker.shutdownNow()
        super.onDestroy()
    }
}
