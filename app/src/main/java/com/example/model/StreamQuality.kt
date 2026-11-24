package com.example.model

enum class StreamQuality(
    val label: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val maxBitrateKbps: Int
) {
    HD_1080_60("1080p • 60 FPS", 1920, 1080, 60, 8000),
    HD_720_60("720p • 60 FPS", 1280, 720, 60, 4500),
    SD_480_30("480p • 30 FPS", 854, 480, 30, 1800)
}

enum class InputControlMode(val label: String) {
    TOUCH_DIRECT("Direct Touch"),
    TRACKPAD("Trackpad Cursor")
}

sealed interface SignalingStatus {
    data object Disconnected : SignalingStatus
    data object Connecting : SignalingStatus
    data class Connected(val serverUrl: String, val registeredId: String) : SignalingStatus
    data class Error(val message: String) : SignalingStatus
}

sealed interface RemoteSessionState {
    data object Idle : RemoteSessionState
    data class RequestingAccess(val targetId: String) : RemoteSessionState
    data class AwaitingMediaProjection(val remoteId: String, val remoteName: String) : RemoteSessionState
    data class ConnectingPeer(val remoteId: String, val remoteName: String, val isHost: Boolean) : RemoteSessionState
    data class Active(
        val remoteId: String,
        val remoteName: String,
        val remotePlatform: DevicePlatform,
        val isHostSharingScreen: Boolean,
        val dataChannelOpen: Boolean
    ) : RemoteSessionState
    data class Failed(val reason: String) : RemoteSessionState
}

data class IncomingCallRequest(
    val senderId: String,
    val callerName: String,
    val callerPlatform: DevicePlatform,
    val providedPin: String,
    val pinMatches: Boolean
)

data class SessionTelemetry(
    val fps: Int = 0,
    val rttMs: Int = 0,
    val bitrateKbps: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)
