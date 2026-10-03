package com.autoskip.helper.remote

import android.util.Log
import org.json.JSONObject
import org.webrtc.IceCandidate
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * 信令 WebSocket 客户端（OKHttp）。
 *
 * 连：ws://server/ws/signal?deviceId=X&role=device
 * 收发 JSON 消息：
 *   { "type": "offer|answer|ice|control|state", "deviceId": "X", "payload": {...} }
 */
class SignalingClient(
    private val url: String,
    private val deviceId: String,
    private val callback: Callback,
) {
    interface Callback {
        fun onConnected()
        fun onOffer(sdp: String)
        fun onIce(candidate: IceCandidate)
        fun onClose()
        fun onError(err: String)
    }

    companion object { private const val TAG = "SignalingClient" }

    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)  // WebSocket 长连接
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null

    fun connect() {
        val fullUrl = "$url?deviceId=$deviceId&role=device"
        Log.i(TAG, "连接信令：$fullUrl")
        val req = Request.Builder().url(fullUrl).build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "信令已连接")
                callback.onConnected()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i(TAG, "信令消息：" + text.take(300))
                try {
                    val obj = JSONObject(text)
                    when (obj.optString("type")) {
                        "offer" -> {
                            val payload = obj.optJSONObject("payload")
                            val sdp = payload?.optString("sdp") ?: ""
                            if (sdp.isNotEmpty()) callback.onOffer(sdp)
                        }
                        "ice" -> {
                            val p = obj.optJSONObject("payload") ?: return
                            val cand = IceCandidate(
                                p.optString("sdpMid"),
                                p.optInt("sdpMLineIndex"),
                                p.optString("candidate")
                            )
                            callback.onIce(cand)
                        }
                        "peer_left", "close" -> callback.onClose()
                        "error" -> callback.onError(obj.optString("error"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析消息失败", e)
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "信令失败", t)
                callback.onError(t.message ?: "WebSocket 失败")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                callback.onClose()
            }
        })
    }

    /** 发送 Answer（本机作答） */
    fun sendAnswer(sdp: String) {
        val payload = JSONObject().put("sdp", sdp)
        send("answer", payload)
    }

    /** 发送 ICE 候选 */
    fun sendIce(c: IceCandidate) {
        val payload = JSONObject()
            .put("sdpMid", c.sdpMid)
            .put("sdpMLineIndex", c.sdpMLineIndex)
            .put("candidate", c.sdp)
        send("ice", payload)
    }

    private fun send(type: String, payload: JSONObject) {
        val msg = JSONObject()
            .put("type", type)
            .put("deviceId", deviceId)
            .put("from", "device")
            .put("payload", payload)
        ws?.send(msg.toString())
    }

    fun close() {
        try { ws?.close(1000, "bye") } catch (_: Exception) {}
        ws = null
    }
}
