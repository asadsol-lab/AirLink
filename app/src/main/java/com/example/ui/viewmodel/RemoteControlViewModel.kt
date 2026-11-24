package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AirLinkDatabase
import com.example.data.local.ConnectedDeviceEntity
import com.example.data.local.DeviceConfig
import com.example.data.local.DevicePreferences
import com.example.model.DevicePlatform
import com.example.model.IncomingCallRequest
import com.example.model.InputControlMode
import com.example.model.RemoteInputPacket
import com.example.model.RemoteSessionState
import com.example.model.SessionTelemetry
import com.example.model.SignalingMessage
import com.example.model.SignalingStatus
import com.example.model.StreamQuality
import com.example.network.SignalingClient
import com.example.network.WebRtcSessionManager
import com.example.network.WebRtcSignalEvent
import com.example.service.InputPacketDispatcher
import com.example.service.RemoteAccessibilityService
import com.example.service.ScreenCaptureService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack

class RemoteControlViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = DevicePreferences(appContext)
    private val historyDao = AirLinkDatabase.getInstance(appContext).deviceHistoryDao()
    private val signalingClient = SignalingClient()

    private val inputDispatcher: InputPacketDispatcher = InputPacketDispatcher(
        onQualityChangeRequested = { requestedQuality ->
            viewModelScope.launch {
                preferences.updateStreamQuality(requestedQuality)
                webRtcManager.updateCaptureQuality(requestedQuality)
                _statusBannerMessage.value = "Stream quality adjusted to ${requestedQuality.label}"
            }
        }
    )

    private val webRtcManager: WebRtcSessionManager = WebRtcSessionManager(appContext, inputDispatcher)

    val deviceConfig: StateFlow<DeviceConfig> = preferences.configFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DeviceConfig(
            deviceId = "000000000",
            securityPin = "0000",
            signalingUrl = DevicePreferences.DEFAULT_SIGNALING_URL,
            streamQuality = StreamQuality.HD_720_60,
            requirePinApproval = true
        )
    )

    val recentDevices: StateFlow<List<ConnectedDeviceEntity>> = historyDao.observeRecentDevices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val signalingStatus: StateFlow<SignalingStatus> = signalingClient.status
    val isAccessibilityEnabled: StateFlow<Boolean> = RemoteAccessibilityService.isRunning
    val isScreenCaptureActive: StateFlow<Boolean> = ScreenCaptureService.isForegroundActive
    val remoteVideoTrack: StateFlow<VideoTrack?> = webRtcManager.remoteVideoTrack
    val dataChannelOpen: StateFlow<Boolean> = webRtcManager.dataChannelOpen
    val telemetry: StateFlow<SessionTelemetry> = webRtcManager.telemetry
    val eglBaseContext: EglBase.Context get() = webRtcManager.eglBase.eglBaseContext

    private val _sessionState = MutableStateFlow<RemoteSessionState>(RemoteSessionState.Idle)
    val sessionState: StateFlow<RemoteSessionState> = _sessionState.asStateFlow()

    private val _incomingCall = MutableStateFlow<IncomingCallRequest?>(null)
    val incomingCall: StateFlow<IncomingCallRequest?> = _incomingCall.asStateFlow()

    private val _targetIdInput = MutableStateFlow("")
    val targetIdInput: StateFlow<String> = _targetIdInput.asStateFlow()

    private val _targetPinInput = MutableStateFlow("")
    val targetPinInput: StateFlow<String> = _targetPinInput.asStateFlow()

    private val _targetPlatform = MutableStateFlow(DevicePlatform.WINDOWS)
    val targetPlatform: StateFlow<DevicePlatform> = _targetPlatform.asStateFlow()

    private val _inputControlMode = MutableStateFlow(InputControlMode.TOUCH_DIRECT)
    val inputControlMode: StateFlow<InputControlMode> = _inputControlMode.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private var activePeerId: String = ""
    private var activePeerName: String = ""
    private var activePeerPlatform: DevicePlatform = DevicePlatform.WINDOWS
    private var pendingHostRequest: IncomingCallRequest? = null
    private var callTimeoutJob: Job? = null

    init {
        viewModelScope.launch {
            val initialized = preferences.ensureInitialized()
            connectToSignaling(initialized.signalingUrl, initialized)
        }

        observeSignalingMessages()
        observeWebRtcEvents()
        observeDataChannelState()
        observeEmergencyDisconnect()
    }

    private fun observeSignalingMessages() {
        viewModelScope.launch {
            signalingClient.incomingMessages.collect { msg ->
                when (msg) {
                    is SignalingMessage.Registered -> {
                        if (msg.deviceId.length == 9) {
                            preferences.updateAssignedDeviceId(msg.deviceId)
                        }
                    }

                    is SignalingMessage.CallRequest -> {
                        val cfg = deviceConfig.value
                        val pinValid = msg.pin == cfg.securityPin
                        if (cfg.requirePinApproval && !pinValid) {
                            signalingClient.send(
                                SignalingMessage.CallReject(
                                    targetId = msg.senderId,
                                    reason = "Invalid Security PIN"
                                )
                            )
                            _statusBannerMessage.value =
                                "Blocked connection from ${msg.callerName} (wrong PIN)"
                        } else {
                            _incomingCall.value = IncomingCallRequest(
                                senderId = msg.senderId,
                                callerName = msg.callerName,
                                callerPlatform = msg.callerPlatform,
                                providedPin = msg.pin,
                                pinMatches = pinValid
                            )
                        }
                    }

                    is SignalingMessage.CallAccept -> {
                        callTimeoutJob?.cancel()
                        activePeerId = msg.senderId
                        activePeerName = msg.accepterName
                        activePeerPlatform = msg.accepterPlatform
                        _sessionState.value = RemoteSessionState.ConnectingPeer(
                            remoteId = msg.senderId,
                            remoteName = msg.accepterName,
                            isHost = false
                        )
                        historyDao.upsertDevice(
                            ConnectedDeviceEntity(
                                deviceId = msg.senderId,
                                deviceName = msg.accepterName,
                                platform = msg.accepterPlatform.wireValue,
                                lastPin = _targetPinInput.value,
                                lastConnectedAt = System.currentTimeMillis()
                            )
                        )
                        webRtcManager.startControllerSession(msg.senderId)
                    }

                    is SignalingMessage.CallReject -> {
                        callTimeoutJob?.cancel()
                        _sessionState.value = RemoteSessionState.Failed(msg.reason)
                        _statusBannerMessage.value = msg.reason
                    }

                    is SignalingMessage.Offer -> {
                        webRtcManager.handleRemoteOffer(msg.senderId, msg.sdp)
                    }

                    is SignalingMessage.Answer -> {
                        webRtcManager.handleRemoteAnswer(msg.sdp)
                    }

                    is SignalingMessage.IceCandidateMessage -> {
                        webRtcManager.addRemoteIceCandidate(
                            candidate = msg.candidate,
                            sdpMid = msg.sdpMid,
                            sdpMLineIndex = msg.sdpMLineIndex
                        )
                    }

                    is SignalingMessage.Disconnect -> {
                        teardownLocalSession()
                        _statusBannerMessage.value = msg.reason
                    }

                    is SignalingMessage.ErrorMessage -> {
                        callTimeoutJob?.cancel()
                        if (_sessionState.value is RemoteSessionState.RequestingAccess) {
                            _sessionState.value = RemoteSessionState.Failed(msg.message)
                        }
                        _statusBannerMessage.value = msg.message
                    }

                    is SignalingMessage.Register -> Unit
                }
            }
        }
    }

    private fun observeWebRtcEvents() {
        viewModelScope.launch {
            webRtcManager.signalEvents.collect { event ->
                when (event) {
                    is WebRtcSignalEvent.LocalOffer -> {
                        signalingClient.send(
                            SignalingMessage.Offer(
                                targetId = event.targetId,
                                sdp = event.sdp
                            )
                        )
                    }

                    is WebRtcSignalEvent.LocalAnswer -> {
                        signalingClient.send(
                            SignalingMessage.Answer(
                                targetId = event.targetId,
                                sdp = event.sdp
                            )
                        )
                    }

                    is WebRtcSignalEvent.LocalIceCandidate -> {
                        signalingClient.send(
                            SignalingMessage.IceCandidateMessage(
                                targetId = event.targetId,
                                candidate = event.candidate,
                                sdpMid = event.sdpMid,
                                sdpMLineIndex = event.sdpMLineIndex
                            )
                        )
                    }

                    is WebRtcSignalEvent.PeerConnectionStateChanged -> {
                        when (event.state) {
                            PeerConnection.PeerConnectionState.CONNECTED -> {
                                val current = _sessionState.value
                                val isHost = (current as? RemoteSessionState.ConnectingPeer)?.isHost ?: false
                                _sessionState.value = RemoteSessionState.Active(
                                    remoteId = activePeerId,
                                    remoteName = activePeerName.ifBlank { activePeerId },
                                    remotePlatform = activePeerPlatform,
                                    isHostSharingScreen = isHost,
                                    dataChannelOpen = webRtcManager.dataChannelOpen.value
                                )
                            }
                            PeerConnection.PeerConnectionState.FAILED,
                            PeerConnection.PeerConnectionState.DISCONNECTED,
                            PeerConnection.PeerConnectionState.CLOSED -> {
                                if (_sessionState.value is RemoteSessionState.Active ||
                                    _sessionState.value is RemoteSessionState.ConnectingPeer
                                ) {
                                    teardownLocalSession()
                                    _statusBannerMessage.value = "Peer connection closed"
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun observeDataChannelState() {
        viewModelScope.launch {
            webRtcManager.dataChannelOpen.collect { isOpen ->
                val current = _sessionState.value
                if (current is RemoteSessionState.Active && current.dataChannelOpen != isOpen) {
                    _sessionState.value = current.copy(dataChannelOpen = isOpen)
                }
            }
        }
    }

    private fun observeEmergencyDisconnect() {
        viewModelScope.launch {
            ScreenCaptureService.emergencyStopEvents.drop(1).collect {
                disconnectSession()
                _statusBannerMessage.value = "Emergency disconnect triggered"
            }
        }
    }

    fun connectToSignaling(url: String, explicitConfig: DeviceConfig? = null) {
        val cfg = explicitConfig ?: deviceConfig.value
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        viewModelScope.launch {
            preferences.updateSignalingUrl(url)
            signalingClient.connect(
                serverUrl = url,
                registration = SignalingMessage.Register(
                    deviceId = cfg.deviceId,
                    deviceName = deviceName,
                    platform = DevicePlatform.ANDROID,
                    pin = cfg.securityPin
                )
            )
        }
    }

    fun regeneratePin() {
        viewModelScope.launch {
            val newPin = preferences.regenerateSecurityPin()
            val cfg = deviceConfig.value
            if (signalingStatus.value is SignalingStatus.Connected) {
                signalingClient.send(
                    SignalingMessage.Register(
                        deviceId = cfg.deviceId,
                        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                        platform = DevicePlatform.ANDROID,
                        pin = newPin
                    )
                )
            }
        }
    }

    fun updateTargetIdInput(value: String) {
        _targetIdInput.value = value.filter { it.isDigit() }.take(9)
    }

    fun updateTargetPinInput(value: String) {
        _targetPinInput.value = value.filter { it.isDigit() }.take(4)
    }

    fun updateTargetPlatform(platform: DevicePlatform) {
        _targetPlatform.value = platform
    }

    fun selectRecentDevice(device: ConnectedDeviceEntity) {
        _targetIdInput.value = device.deviceId
        _targetPinInput.value = device.lastPin
        _targetPlatform.value = DevicePlatform.fromWire(device.platform)
    }

    fun toggleFavoriteDevice(deviceId: String) {
        viewModelScope.launch {
            historyDao.toggleFavorite(deviceId)
        }
    }

    fun deleteDeviceHistory(deviceId: String) {
        viewModelScope.launch {
            historyDao.deleteDevice(deviceId)
        }
    }

    fun initiateRemoteConnection() {
        val targetId = _targetIdInput.value.trim()
        val pin = _targetPinInput.value.trim()
        if (targetId.length != 9) {
            _statusBannerMessage.value = "Enter a valid 9-digit target Device ID"
            return
        }
        if (pin.length != 4) {
            _statusBannerMessage.value = "Enter the target device's 4-digit Security PIN"
            return
        }
        if (signalingStatus.value !is SignalingStatus.Connected) {
            _statusBannerMessage.value = "Connect to the Signaling Server in Setup first"
            return
        }

        _sessionState.value = RemoteSessionState.RequestingAccess(targetId)
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val sent = signalingClient.send(
            SignalingMessage.CallRequest(
                targetId = targetId,
                senderId = deviceConfig.value.deviceId,
                callerName = deviceName,
                callerPlatform = DevicePlatform.ANDROID,
                pin = pin
            )
        )
        if (!sent) {
            _sessionState.value = RemoteSessionState.Failed("Failed to transmit call request")
            return
        }

        callTimeoutJob?.cancel()
        callTimeoutJob = viewModelScope.launch {
            delay(30_000L)
            if (_sessionState.value is RemoteSessionState.RequestingAccess) {
                _sessionState.value = RemoteSessionState.Failed("No response from remote device (timed out)")
            }
        }
    }

    fun approveIncomingCallAndRequestProjection() {
        val call = _incomingCall.value ?: return
        pendingHostRequest = call
        _incomingCall.value = null
        activePeerId = call.senderId
        activePeerName = call.callerName
        activePeerPlatform = call.callerPlatform
        _sessionState.value = RemoteSessionState.AwaitingMediaProjection(
            remoteId = call.senderId,
            remoteName = call.callerName
        )
    }

    fun onMediaProjectionResult(resultCode: Int, data: Intent?) {
        val request = pendingHostRequest
        pendingHostRequest = null
        if (resultCode != Activity.RESULT_OK || data == null || request == null) {
            if (request != null) {
                signalingClient.send(
                    SignalingMessage.CallReject(
                        targetId = request.senderId,
                        reason = "Screen sharing permission declined by host"
                    )
                )
            }
            _sessionState.value = RemoteSessionState.Idle
            return
        }

        viewModelScope.launch {
            ScreenCaptureService.startService(appContext)
            // Wait for foreground service startForeground(MEDIA_PROJECTION) before calling getMediaProjection() on API 34+
            val serviceReady = ScreenCaptureService.isForegroundActive.value ||
                (withTimeoutOrNull(3000L) {
                    ScreenCaptureService.isForegroundActive.first { it }
                } == true)

            if (!serviceReady) {
                signalingClient.send(
                    SignalingMessage.CallReject(
                        targetId = request.senderId,
                        reason = "Unable to start foreground screen capture service"
                    )
                )
                ScreenCaptureService.stopService(appContext)
                _sessionState.value = RemoteSessionState.Failed("Foreground screen capture service failed to start")
                return@launch
            }

            _sessionState.value = RemoteSessionState.ConnectingPeer(
                remoteId = request.senderId,
                remoteName = request.callerName,
                isHost = true
            )

            historyDao.upsertDevice(
                ConnectedDeviceEntity(
                    deviceId = request.senderId,
                    deviceName = request.callerName,
                    platform = request.callerPlatform.wireValue,
                    lastPin = "",
                    lastConnectedAt = System.currentTimeMillis()
                )
            )

            webRtcManager.startHostSessionWithScreenCapture(
                targetId = request.senderId,
                mediaProjectionResultData = data,
                quality = deviceConfig.value.streamQuality,
                onProjectionStopped = {
                    viewModelScope.launch {
                        disconnectSession()
                    }
                }
            )

            val localName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            signalingClient.send(
                SignalingMessage.CallAccept(
                    targetId = request.senderId,
                    accepterName = localName,
                    accepterPlatform = DevicePlatform.ANDROID
                )
            )
        }
    }

    fun denyIncomingCall() {
        val call = _incomingCall.value ?: return
        _incomingCall.value = null
        pendingHostRequest = null
        signalingClient.send(
            SignalingMessage.CallReject(
                targetId = call.senderId,
                reason = "Host declined remote control request"
            )
        )
    }

    fun disconnectSession() {
        callTimeoutJob?.cancel()
        if (activePeerId.isNotBlank()) {
            signalingClient.send(
                SignalingMessage.Disconnect(
                    targetId = activePeerId,
                    reason = "Remote session ended"
                )
            )
        }
        teardownLocalSession()
    }

    private fun teardownLocalSession() {
        callTimeoutJob?.cancel()
        webRtcManager.closeSession()
        ScreenCaptureService.stopService(appContext)
        _sessionState.value = RemoteSessionState.Idle
        activePeerId = ""
        activePeerName = ""
    }

    fun sendInputPacket(packet: RemoteInputPacket) {
        webRtcManager.sendInputPacket(packet)
    }

    fun setStreamQuality(quality: StreamQuality) {
        viewModelScope.launch {
            preferences.updateStreamQuality(quality)
            val current = _sessionState.value
            if (current is RemoteSessionState.Active && !current.isHostSharingScreen) {
                webRtcManager.sendInputPacket(RemoteInputPacket.QualityChange(quality.name))
            } else {
                webRtcManager.updateCaptureQuality(quality)
            }
        }
    }

    fun setInputControlMode(mode: InputControlMode) {
        _inputControlMode.value = mode
    }

    fun setRequirePinApproval(required: Boolean) {
        viewModelScope.launch {
            preferences.updateRequirePinApproval(required)
        }
    }

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun signInWithGoogle(idToken: String, onResult: (Boolean) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        Firebase.auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _statusBannerMessage.value = "Signed in successfully with Google"
                    onResult(true)
                } else {
                    _statusBannerMessage.value = task.exception?.localizedMessage ?: "Google sign-in failed"
                    onResult(false)
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        signalingClient.disconnect()
        webRtcManager.releaseFactory()
    }
}
