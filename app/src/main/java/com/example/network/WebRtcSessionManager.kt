package com.example.network

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import com.example.model.RemoteInputPacket
import com.example.model.SessionTelemetry
import com.example.model.StreamQuality
import com.example.service.InputPacketDispatcher
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

sealed interface WebRtcSignalEvent {
    data class LocalOffer(val targetId: String, val sdp: String) : WebRtcSignalEvent
    data class LocalAnswer(val targetId: String, val sdp: String) : WebRtcSignalEvent
    data class LocalIceCandidate(
        val targetId: String,
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int
    ) : WebRtcSignalEvent
    data class PeerConnectionStateChanged(val state: PeerConnection.PeerConnectionState) : WebRtcSignalEvent
}

class WebRtcSessionManager(
    private val context: Context,
    private val inputDispatcher: InputPacketDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val eglBase: EglBase = EglBase.create()
    private val peerConnectionFactory: PeerConnectionFactory

    private var peerConnection: PeerConnection? = null
    private var inputDataChannel: DataChannel? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var activeTargetId: String = ""
    private var statsJob: Job? = null
    private var previousBytesTransferred = 0L
    private var previousStatsTimestampMs = 0L

    private val iceLock = Any()
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _dataChannelOpen = MutableStateFlow(false)
    val dataChannelOpen: StateFlow<Boolean> = _dataChannelOpen.asStateFlow()

    private val _telemetry = MutableStateFlow(SessionTelemetry())
    val telemetry: StateFlow<SessionTelemetry> = _telemetry.asStateFlow()

    private val _signalEvents = MutableSharedFlow<WebRtcSignalEvent>(extraBufferCapacity = 64)
    val signalEvents: SharedFlow<WebRtcSignalEvent> = _signalEvents.asSharedFlow()

    init {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private val rtcConfig = PeerConnection.RTCConfiguration(
        listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
    ).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
    }

    fun startControllerSession(targetId: String) {
        closeSession()
        activeTargetId = targetId
        val pc = createPeerConnection(targetId) ?: return
        peerConnection = pc

        pc.addTransceiver(
            org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )

        val dcInit = DataChannel.Init().apply {
            ordered = true
            negotiated = false
        }
        val dc = pc.createDataChannel("inputs_channel", dcInit)
        attachDataChannelObserver(dc)

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc.setLocalDescription(SimpleSdpObserver(), desc)
                scope.launch {
                    _signalEvents.emit(WebRtcSignalEvent.LocalOffer(targetId, desc.description))
                }
            }
        }, constraints)

        startStatsPolling()
    }

    fun startHostSessionWithScreenCapture(
        targetId: String,
        mediaProjectionResultData: Intent,
        quality: StreamQuality,
        onProjectionStopped: () -> Unit
    ) {
        closeSession()
        activeTargetId = targetId
        val pc = createPeerConnection(targetId) ?: return
        peerConnection = pc

        val capturer = ScreenCapturerAndroid(
            mediaProjectionResultData,
            object : MediaProjection.Callback() {
                override fun onStop() {
                    onProjectionStopped()
                }
            }
        )
        videoCapturer = capturer

        val textureHelper = SurfaceTextureHelper.create("AirLinkCaptureThread", eglBase.eglBaseContext)
        surfaceTextureHelper = textureHelper

        val videoSource = peerConnectionFactory.createVideoSource(capturer.isScreencast)
        capturer.initialize(textureHelper, context.applicationContext, videoSource.capturerObserver)
        capturer.startCapture(quality.width, quality.height, quality.fps)

        val localVideoTrack = peerConnectionFactory.createVideoTrack("airlink_screen_v0", videoSource)
        localVideoTrack.setEnabled(true)
        pc.addTrack(localVideoTrack, listOf("airlink_stream"))
        applyVideoSenderParameters(quality)

        startStatsPolling()
    }

    fun handleRemoteOffer(senderId: String, sdp: String) {
        activeTargetId = senderId
        val pc = peerConnection ?: createPeerConnection(senderId)?.also { peerConnection = it } ?: return

        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, sdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                flushPendingIceCandidates(pc)
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(answerDesc: SessionDescription) {
                        pc.setLocalDescription(SimpleSdpObserver(), answerDesc)
                        scope.launch {
                            _signalEvents.emit(
                                WebRtcSignalEvent.LocalAnswer(senderId, answerDesc.description)
                            )
                        }
                    }
                }, MediaConstraints())
            }
        }, remoteDesc)
    }

    fun handleRemoteAnswer(sdp: String) {
        val pc = peerConnection ?: return
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                flushPendingIceCandidates(pc)
            }
        }, remoteDesc)
    }

    fun addRemoteIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val ice = IceCandidate(sdpMid ?: "", sdpMLineIndex, candidate)
        synchronized(iceLock) {
            val pc = peerConnection
            if (pc != null && isRemoteDescriptionSet) {
                pc.addIceCandidate(ice)
            } else {
                pendingIceCandidates.add(ice)
            }
        }
    }

    private fun flushPendingIceCandidates(pc: PeerConnection) {
        synchronized(iceLock) {
            isRemoteDescriptionSet = true
            pendingIceCandidates.forEach { pc.addIceCandidate(it) }
            pendingIceCandidates.clear()
        }
    }

    fun sendInputPacket(packet: RemoteInputPacket): Boolean {
        val dc = inputDataChannel ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false
        val bytes = packet.toJson().toByteArray(StandardCharsets.UTF_8)
        return dc.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
    }

    fun updateCaptureQuality(quality: StreamQuality) {
        try {
            videoCapturer?.changeCaptureFormat(quality.width, quality.height, quality.fps)
            applyVideoSenderParameters(quality)
        } catch (_: Exception) {
        }
    }

    private fun applyVideoSenderParameters(quality: StreamQuality) {
        val pc = peerConnection ?: return
        for (sender in pc.senders) {
            if (sender.track()?.kind() == "video") {
                val params = sender.parameters
                params.encodings.forEach { encoding ->
                    encoding.maxBitrateBps = quality.maxBitrateKbps * 1000
                    encoding.maxFramerate = quality.fps
                }
                sender.parameters = params
            }
        }
    }

    fun closeSession() {
        statsJob?.cancel()
        statsJob = null
        previousBytesTransferred = 0L
        previousStatsTimestampMs = 0L

        synchronized(iceLock) {
            isRemoteDescriptionSet = false
            pendingIceCandidates.clear()
        }

        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {
        }
        videoCapturer?.dispose()
        videoCapturer = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        inputDataChannel?.unregisterObserver()
        inputDataChannel?.close()
        inputDataChannel = null

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        _remoteVideoTrack.value = null
        _dataChannelOpen.value = false
        _telemetry.value = SessionTelemetry()
        activeTargetId = ""
    }

    fun releaseFactory() {
        closeSession()
        peerConnectionFactory.dispose()
        eglBase.release()
    }

    private fun createPeerConnection(targetId: String): PeerConnection? {
        return peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    scope.launch {
                        _signalEvents.emit(
                            WebRtcSignalEvent.LocalIceCandidate(
                                targetId = targetId,
                                candidate = candidate.sdp,
                                sdpMid = candidate.sdpMid,
                                sdpMLineIndex = candidate.sdpMLineIndex
                            )
                        )
                    }
                }

                override fun onDataChannel(channel: DataChannel) {
                    attachDataChannelObserver(channel)
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    val track = transceiver?.receiver?.track()
                    if (track is VideoTrack) {
                        track.setEnabled(true)
                        _remoteVideoTrack.value = track
                    }
                }

                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        track.setEnabled(true)
                        _remoteVideoTrack.value = track
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                    scope.launch {
                        _signalEvents.emit(WebRtcSignalEvent.PeerConnectionStateChanged(newState))
                    }
                }

                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onRenegotiationNeeded() {}
            }
        )
    }

    private fun attachDataChannelObserver(channel: DataChannel) {
        inputDataChannel = channel
        _dataChannelOpen.value = channel.state() == DataChannel.State.OPEN
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                _dataChannelOpen.value = channel.state() == DataChannel.State.OPEN
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                val rawJson = String(bytes, StandardCharsets.UTF_8)
                inputDispatcher.dispatchRawPacket(rawJson)
            }
        })
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                delay(1500L)
                val pc = peerConnection ?: continue
                pc.getStats { report ->
                    var fps = 0
                    var width = 0
                    var height = 0
                    var rttMs = 0
                    var totalBytes = 0L

                    for (stats in report.statsMap.values) {
                        when (stats.type) {
                            "inbound-rtp" -> {
                                if (stats.members["kind"] == "video") {
                                    fps = (stats.members["framesPerSecond"] as? Number)?.toInt() ?: fps
                                    width = (stats.members["frameWidth"] as? Number)?.toInt() ?: width
                                    height = (stats.members["frameHeight"] as? Number)?.toInt() ?: height
                                    totalBytes = (stats.members["bytesReceived"] as? Number)?.toLong() ?: totalBytes
                                }
                            }
                            "outbound-rtp" -> {
                                if (stats.members["kind"] == "video") {
                                    fps = (stats.members["framesPerSecond"] as? Number)?.toInt() ?: fps
                                    width = (stats.members["frameWidth"] as? Number)?.toInt() ?: width
                                    height = (stats.members["frameHeight"] as? Number)?.toInt() ?: height
                                    val sent = (stats.members["bytesSent"] as? Number)?.toLong() ?: 0L
                                    if (totalBytes == 0L) totalBytes = sent
                                }
                            }
                            "candidate-pair" -> {
                                if (stats.members["state"] == "succeeded") {
                                    val rttSec = (stats.members["currentRoundTripTime"] as? Number)?.toDouble() ?: 0.0
                                    if (rttSec > 0) {
                                        rttMs = (rttSec * 1000.0).toInt()
                                    }
                                }
                            }
                        }
                    }

                    val now = System.currentTimeMillis()
                    var bitrateKbps = _telemetry.value.bitrateKbps
                    if (previousStatsTimestampMs > 0L && totalBytes >= previousBytesTransferred) {
                        val elapsedMs = (now - previousStatsTimestampMs).coerceAtLeast(1L)
                        val deltaBytes = totalBytes - previousBytesTransferred
                        bitrateKbps = ((deltaBytes * 8L) / elapsedMs).toInt()
                    }
                    previousBytesTransferred = totalBytes
                    previousStatsTimestampMs = now

                    _telemetry.value = SessionTelemetry(
                        fps = fps,
                        rttMs = rttMs,
                        bitrateKbps = bitrateKbps,
                        width = width,
                        height = height
                    )
                }
            }
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
