package com.example.network

import com.example.model.SignalingMessage
import com.example.model.SignalingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class SignalingClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var activeServerUrl: String = ""
    private var localRegistrationPayload: SignalingMessage.Register? = null
    private var shouldMaintainConnection = false
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null

    private val _status = MutableStateFlow<SignalingStatus>(SignalingStatus.Disconnected)
    val status: StateFlow<SignalingStatus> = _status.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<SignalingMessage> = _incomingMessages.asSharedFlow()

    fun connect(serverUrl: String, registration: SignalingMessage.Register) {
        val normalizedUrl = serverUrl.trim()
        if (normalizedUrl.isEmpty() || (!normalizedUrl.startsWith("ws://") && !normalizedUrl.startsWith("wss://"))) {
            shouldMaintainConnection = false
            _status.value = SignalingStatus.Error("Invalid WebSocket URL (must start with ws:// or wss://)")
            return
        }

        reconnectJob?.cancel()
        webSocket?.close(1000, "Reconnecting")
        webSocket = null

        activeServerUrl = normalizedUrl
        localRegistrationPayload = registration
        shouldMaintainConnection = true
        reconnectAttempt = 0
        openSocket(normalizedUrl, registration)
    }

    private fun openSocket(serverUrl: String, registration: SignalingMessage.Register) {
        _status.value = SignalingStatus.Connecting

        val request = try {
            Request.Builder().url(serverUrl).build()
        } catch (e: Exception) {
            shouldMaintainConnection = false
            _status.value = SignalingStatus.Error("Malformed URL: ${e.message}")
            return
        }

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                webSocket.send(registration.toJson())
                _status.value = SignalingStatus.Connected(
                    serverUrl = activeServerUrl,
                    registeredId = registration.deviceId
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = SignalingMessage.fromJson(text) ?: return
                if (parsed is SignalingMessage.Registered && parsed.deviceId.isNotBlank()) {
                    _status.value = SignalingStatus.Connected(
                        serverUrl = activeServerUrl,
                        registeredId = parsed.deviceId
                    )
                }
                scope.launch {
                    _incomingMessages.emit(parsed)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                _status.value = SignalingStatus.Disconnected
                scheduleReconnectIfNeeded()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _status.value = SignalingStatus.Disconnected
                scheduleReconnectIfNeeded()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _status.value = SignalingStatus.Error(
                    t.message ?: "Unable to reach signaling server at $activeServerUrl"
                )
                scheduleReconnectIfNeeded()
            }
        })
    }

    private fun scheduleReconnectIfNeeded() {
        if (!shouldMaintainConnection) return
        val reg = localRegistrationPayload ?: return
        val url = activeServerUrl
        if (url.isBlank()) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoffMs = (2000L shl reconnectAttempt.coerceAtMost(3)).coerceAtMost(15000L)
            reconnectAttempt++
            delay(backoffMs)
            if (shouldMaintainConnection) {
                openSocket(url, reg)
            }
        }
    }

    fun send(message: SignalingMessage): Boolean {
        val ws = webSocket ?: return false
        return ws.send(message.toJson())
    }

    fun disconnect() {
        shouldMaintainConnection = false
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Client closing")
        webSocket = null
        _status.value = SignalingStatus.Disconnected
    }
}
