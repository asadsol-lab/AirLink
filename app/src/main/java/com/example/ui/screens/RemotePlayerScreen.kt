package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.DevicePlatform
import com.example.model.InputControlMode
import com.example.model.RemoteInputPacket
import com.example.model.RemoteSessionState
import com.example.model.SessionTelemetry
import com.example.model.StreamQuality
import com.example.ui.components.VirtualKeyboardSheet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalRed
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.math.abs

private fun computeFittedVideoRect(viewport: IntSize, frameW: Int, frameH: Int): Rect {
    val vw = viewport.width.coerceAtLeast(1).toFloat()
    val vh = viewport.height.coerceAtLeast(1).toFloat()
    val aspect = if (frameW > 0 && frameH > 0) frameW.toFloat() / frameH.toFloat() else (16f / 9f)
    val viewAspect = vw / vh
    return if (viewAspect > aspect) {
        val renderedW = vh * aspect
        val left = (vw - renderedW) / 2f
        Rect(left = left, top = 0f, right = left + renderedW, bottom = vh)
    } else {
        val renderedH = vw / aspect
        val top = (vh - renderedH) / 2f
        Rect(left = 0f, top = top, right = vw, bottom = top + renderedH)
    }
}

private fun normalizeToVideoRect(offset: Offset, rect: Rect): Pair<Float, Float> {
    val nx = ((offset.x - rect.left) / rect.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val ny = ((offset.y - rect.top) / rect.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
    return nx to ny
}

@Composable
fun RemotePlayerScreen(
    sessionState: RemoteSessionState,
    remoteVideoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    telemetry: SessionTelemetry,
    currentQuality: StreamQuality,
    inputControlMode: InputControlMode,
    isAccessibilityEnabled: Boolean,
    onSendInput: (RemoteInputPacket) -> Unit,
    onQualityChange: (StreamQuality) -> Unit,
    onInputModeToggle: (InputControlMode) -> Unit,
    onDisconnect: () -> Unit,
    onGoToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showKeyboard by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var dragLockActive by remember { mutableStateOf(false) }
    var videoViewportSize by remember { mutableStateOf(IntSize(1, 1)) }
    var cursorNormX by remember { mutableFloatStateOf(0.5f) }
    var cursorNormY by remember { mutableFloatStateOf(0.5f) }

    val hasLiveSession = sessionState is RemoteSessionState.Active ||
        sessionState is RemoteSessionState.ConnectingPeer

    BackHandler(enabled = hasLiveSession) {
        if (showKeyboard) {
            showKeyboard = false
        } else {
            onDisconnect()
        }
    }

    val videoRect = remember(videoViewportSize, telemetry.width, telemetry.height) {
        computeFittedVideoRect(videoViewportSize, telemetry.width, telemetry.height)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (sessionState) {
            is RemoteSessionState.Active -> {
                if (sessionState.isHostSharingScreen) {
                    HostSharingOverlay(
                        remoteName = sessionState.remoteName,
                        remoteId = sessionState.remoteId,
                        telemetry = telemetry,
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        onDisconnect = onDisconnect
                    )
                } else {
                    if (remoteVideoTrack != null) {
                        WebRtcVideoSurface(
                            videoTrack = remoteVideoTrack,
                            eglBaseContext = eglBaseContext,
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { videoViewportSize = it }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { videoViewportSize = it },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Waiting for remote video frames…",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }

                    // Interactive Touch, Two-Finger Scroll & Trackpad Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // Two-finger scroll detector
                            .pointerInput(videoRect) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var prevCentroid: Offset? = null
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        if (pressed.size >= 2) {
                                            val cx = pressed.map { it.position.x }.average().toFloat()
                                            val cy = pressed.map { it.position.y }.average().toFloat()
                                            val centroid = Offset(cx, cy)
                                            val prev = prevCentroid
                                            if (prev != null) {
                                                val dx = (centroid.x - prev.x) / 40f
                                                val dy = (centroid.y - prev.y) / 40f
                                                if (abs(dx) > 0.05f || abs(dy) > 0.05f) {
                                                    val (nx, ny) = normalizeToVideoRect(centroid, videoRect)
                                                    onSendInput(RemoteInputPacket.MouseScroll(dx, dy, nx, ny))
                                                }
                                            }
                                            prevCentroid = centroid
                                            pressed.forEach { it.consume() }
                                        } else {
                                            prevCentroid = null
                                        }
                                    }
                                }
                            }
                            .pointerInput(inputControlMode, videoRect) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val (nx, ny) = if (inputControlMode == InputControlMode.TOUCH_DIRECT) {
                                            normalizeToVideoRect(offset, videoRect)
                                        } else {
                                            cursorNormX to cursorNormY
                                        }
                                        cursorNormX = nx
                                        cursorNormY = ny
                                        onSendInput(RemoteInputPacket.MouseMove(nx, ny))
                                        onSendInput(RemoteInputPacket.MouseClick("left", "click", nx, ny))
                                    },
                                    onLongPress = { offset ->
                                        val (nx, ny) = if (inputControlMode == InputControlMode.TOUCH_DIRECT) {
                                            normalizeToVideoRect(offset, videoRect)
                                        } else {
                                            cursorNormX to cursorNormY
                                        }
                                        cursorNormX = nx
                                        cursorNormY = ny
                                        onSendInput(RemoteInputPacket.MouseMove(nx, ny))
                                        onSendInput(RemoteInputPacket.MouseClick("right", "click", nx, ny))
                                    }
                                )
                            }
                            .pointerInput(inputControlMode, videoRect, dragLockActive) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        if (inputControlMode == InputControlMode.TOUCH_DIRECT) {
                                            val (nx, ny) = normalizeToVideoRect(offset, videoRect)
                                            cursorNormX = nx
                                            cursorNormY = ny
                                            onSendInput(RemoteInputPacket.MouseMove(nx, ny))
                                            onSendInput(RemoteInputPacket.MouseClick("left", "down", nx, ny))
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (inputControlMode == InputControlMode.TOUCH_DIRECT) {
                                            val (nx, ny) = normalizeToVideoRect(change.position, videoRect)
                                            cursorNormX = nx
                                            cursorNormY = ny
                                        } else {
                                            val rw = videoRect.width.coerceAtLeast(1f)
                                            val rh = videoRect.height.coerceAtLeast(1f)
                                            cursorNormX = (cursorNormX + (dragAmount.x / rw) * 1.4f).coerceIn(0f, 1f)
                                            cursorNormY = (cursorNormY + (dragAmount.y / rh) * 1.4f).coerceIn(0f, 1f)
                                        }
                                        onSendInput(RemoteInputPacket.MouseMove(cursorNormX, cursorNormY))
                                    },
                                    onDragEnd = {
                                        if (inputControlMode == InputControlMode.TOUCH_DIRECT && !dragLockActive) {
                                            onSendInput(
                                                RemoteInputPacket.MouseClick(
                                                    button = "left",
                                                    action = "up",
                                                    x = cursorNormX,
                                                    y = cursorNormY
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                            .testTag("remote_video_touch_surface")
                    ) {
                        if (inputControlMode == InputControlMode.TRACKPAD) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cx = videoRect.left + cursorNormX * videoRect.width
                                val cy = videoRect.top + cursorNormY * videoRect.height
                                drawCircle(
                                    color = ElectricCyan.copy(alpha = 0.25f),
                                    radius = 18.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                                drawCircle(
                                    color = ElectricCyan,
                                    radius = 6.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                            }
                        }
                    }

                    // Top Floating Control Toolbar
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(12.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xCC0F1623),
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 6.dp)) {
                                Text(
                                    text = sessionState.remoteName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "${telemetry.fps} FPS • ${telemetry.rttMs} ms • ${telemetry.bitrateKbps} kbps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SignalGreen
                                )
                            }

                            IconButton(
                                onClick = {
                                    val next = if (inputControlMode == InputControlMode.TOUCH_DIRECT) {
                                        InputControlMode.TRACKPAD
                                    } else {
                                        if (dragLockActive) {
                                            dragLockActive = false
                                            onSendInput(RemoteInputPacket.MouseClick("left", "up", cursorNormX, cursorNormY))
                                        }
                                        InputControlMode.TOUCH_DIRECT
                                    }
                                    onInputModeToggle(next)
                                },
                                modifier = Modifier.testTag("toggle_input_mode_button")
                            ) {
                                Icon(
                                    imageVector = if (inputControlMode == InputControlMode.TOUCH_DIRECT) {
                                        Icons.Filled.TouchApp
                                    } else {
                                        Icons.Filled.Mouse
                                    },
                                    contentDescription = inputControlMode.label,
                                    tint = ElectricCyan
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showQualityMenu = true },
                                    modifier = Modifier.testTag("quality_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Speed,
                                        contentDescription = "Stream quality",
                                        tint = Color.White
                                    )
                                }
                                DropdownMenu(
                                    expanded = showQualityMenu,
                                    onDismissRequest = { showQualityMenu = false }
                                ) {
                                    StreamQuality.entries.forEach { preset ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (preset == currentQuality) {
                                                        "✓ ${preset.label}"
                                                    } else {
                                                        preset.label
                                                    }
                                                )
                                            },
                                            onClick = {
                                                showQualityMenu = false
                                                onQualityChange(preset)
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { showKeyboard = !showKeyboard },
                                modifier = Modifier.testTag("toggle_keyboard_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Keyboard,
                                    contentDescription = "Toggle virtual keyboard",
                                    tint = if (showKeyboard) ElectricCyan else Color.White
                                )
                            }

                            IconButton(
                                onClick = onDisconnect,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(SignalRed.copy(alpha = 0.2f))
                                    .testTag("disconnect_session_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CallEnd,
                                    contentDescription = stringResource(R.string.emergency_disconnect),
                                    tint = SignalRed
                                )
                            }
                        }
                    }

                    // Trackpad Quick Action Bar (Left Click, Drag Lock, Scroll Up/Down, Right Click)
                    if (inputControlMode == InputControlMode.TRACKPAD && !showKeyboard) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = if (sessionState.remotePlatform == DevicePlatform.ANDROID) 76.dp else 20.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xCC0F1623)
                        ) {
                            Row(
                                modifier = Modifier
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        onSendInput(RemoteInputPacket.MouseClick("left", "click", cursorNormX, cursorNormY))
                                    },
                                    label = { Text("L-Click") }
                                )
                                FilterChip(
                                    selected = dragLockActive,
                                    onClick = {
                                        dragLockActive = !dragLockActive
                                        onSendInput(
                                            RemoteInputPacket.MouseClick(
                                                button = "left",
                                                action = if (dragLockActive) "down" else "up",
                                                x = cursorNormX,
                                                y = cursorNormY
                                            )
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.PanTool, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = { Text("Drag") }
                                )
                                IconButton(
                                    onClick = {
                                        onSendInput(RemoteInputPacket.MouseScroll(0f, 1.2f, cursorNormX, cursorNormY))
                                    }
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Scroll up", tint = Color.White)
                                }
                                IconButton(
                                    onClick = {
                                        onSendInput(RemoteInputPacket.MouseScroll(0f, -1.2f, cursorNormX, cursorNormY))
                                    }
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Scroll down", tint = Color.White)
                                }
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        onSendInput(RemoteInputPacket.MouseClick("right", "click", cursorNormX, cursorNormY))
                                    },
                                    label = { Text("R-Click") }
                                )
                            }
                        }
                    }

                    // Bottom Android Navigation Bar Shortcuts (when controlling an Android peer)
                    if (sessionState.remotePlatform == DevicePlatform.ANDROID && !showKeyboard) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 18.dp),
                            shape = RoundedCornerShape(50),
                            color = Color(0xCC0F1623)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconButton(onClick = { onSendInput(RemoteInputPacket.SystemAction("back")) }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Remote Back",
                                        tint = Color.White
                                    )
                                }
                                IconButton(onClick = { onSendInput(RemoteInputPacket.SystemAction("home")) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Home,
                                        contentDescription = "Remote Home",
                                        tint = Color.White
                                    )
                                }
                                IconButton(onClick = { onSendInput(RemoteInputPacket.SystemAction("recents")) }) {
                                    Icon(
                                        imageVector = Icons.Filled.ViewCarousel,
                                        contentDescription = "Remote Recents",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Virtual Keyboard Sheet
                    AnimatedVisibility(
                        visible = showKeyboard,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    ) {
                        VirtualKeyboardSheet(
                            onSendPacket = onSendInput,
                            onClose = { showKeyboard = false }
                        )
                    }
                }
            }

            is RemoteSessionState.ConnectingPeer -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CastConnected,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Negotiating P2P WebRTC Tunnel…",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Peer: ${sessionState.remoteName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        Button(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.buttonColors(containerColor = SignalRed)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CastConnected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = "No Active Remote Stream",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Start a session from the Dashboard using a 9-digit Device ID and PIN, or accept an incoming control request.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = onGoToDashboard,
                            modifier = Modifier.testTag("go_to_dashboard_button")
                        ) {
                            Text("Open Dashboard")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostSharingOverlay(
    remoteName: String,
    remoteId: String,
    telemetry: SessionTelemetry,
    isAccessibilityEnabled: Boolean,
    onDisconnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, SignalGreen.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CastConnected,
                contentDescription = null,
                tint = SignalGreen,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Screen Stream Active",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "$remoteName ($remoteId) is connected via DTLS-SRTP WebRTC.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${telemetry.fps} FPS • ${telemetry.bitrateKbps} kbps • Touch Engine: ${if (isAccessibilityEnabled) "ON" else "View-Only"}",
                style = MaterialTheme.typography.labelMedium,
                color = SignalGreen
            )
            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(containerColor = SignalRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("host_emergency_disconnect_button")
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.emergency_disconnect))
            }
        }
    }
}

@Composable
private fun WebRtcVideoSurface(
    videoTrack: VideoTrack,
    eglBaseContext: EglBase.Context,
    modifier: Modifier = Modifier
) {
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    DisposableEffect(videoTrack, rendererRef) {
        val renderer = rendererRef
        if (renderer != null) {
            videoTrack.addSink(renderer)
        }
        onDispose {
            if (renderer != null) {
                videoTrack.removeSink(renderer)
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                setEnableHardwareScaler(true)
                setMirror(false)
                rendererRef = this
            }
        },
        onRelease = { renderer ->
            videoTrack.removeSink(renderer)
            renderer.release()
            rendererRef = null
        }
    )
}
