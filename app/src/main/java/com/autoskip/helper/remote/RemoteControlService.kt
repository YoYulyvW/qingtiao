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
                initWebRTC()
                startCaptureClient()
                startSignaling(signalUrl)
            } catch (e: Exception) {
                log("★ 启动失败：" + e.message)
                e.printStackTrace()
                stopSelf()
            }
        }
        return START_STICKY
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
    private fun setupDataChannel(dc: DataChannel) {
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
    private fun handleControlMessage(json: String) {
        // 简化：JSON {"type":"tap","x":100,"y":200} / {"type":"swipe",...}
        // 具体解析后续实现
        // 这里只打印，防止无实现空转
        log("触控指令：" + json)
        // TODO: 解析 → RootShell.exec("input tap x y")
    }

    // ===== 连接 app_process 的捕获流 =====
    private fun startCaptureClient() {
        log("连接 app_process 捕获端口 $CAPTURE_PORT…")
        captureClient = CaptureClient(CAPTURE_PORT).apply {
            onFrame = { data ->
                // 数据是完整的 H.264 帧（长度前缀已在 CaptureClient 内解析）
                sendFrameToDataChannel(data)
            }
            onError = { err ->
                log("捕获流错误：" + err)
            }
        }
        captureClient?.connect()
    }

    // ===== 把 H.264 帧分片发出 =====
    private fun sendFrameToDataChannel(frame: ByteArray) {
        val dc = dataChannel ?: return
        if (dc.state() != DataChannel.State.OPEN) return

        // 分片（每片 ≤ 16KB）
        var offset = 0
        while (offset < frame.size) {
            val chunkLen = minOf(H264_CHUNK_SIZE, frame.size - offset)
            val buf = ByteBuffer.allocateDirect(chunkLen)
            buf.put(frame, offset, chunkLen)
            buf.flip()
            dc.send(DataChannel.Buffer(buf, true))
            offset += chunkLen
        }
    }

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
