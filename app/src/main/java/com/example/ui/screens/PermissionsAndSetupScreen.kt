package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.DeviceConfig
import com.example.model.SignalingStatus
import com.example.model.StreamQuality
import com.example.ui.components.StatusIndicatorBadge
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalGreen

@Composable
fun PermissionsAndSetupScreen(
    deviceConfig: DeviceConfig,
    signalingStatus: SignalingStatus,
    isAccessibilityEnabled: Boolean,
    onConnectSignaling: (String) -> Unit,
    onQualityChange: (StreamQuality) -> Unit,
    onRequirePinApprovalChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var serverUrlInput by remember(deviceConfig.signalingUrl) {
        mutableStateOf(deviceConfig.signalingUrl)
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasNotificationPermission = granted }
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Setup & Permissions",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Signaling relay & Android control engines",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusIndicatorBadge(status = signalingStatus)
                }
            }

            // Signaling Server Configuration
            item {
                SetupSectionCard(
                    icon = Icons.Filled.Dns,
                    title = "WebSocket Signaling Relay",
                    subtitle = "Runs server.js for 9-digit Device IDs and WebRTC SDP/ICE exchange"
                ) {
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Signaling Server URL") },
                        placeholder = { Text("ws://192.168.1.100:8080") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signaling_url_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = serverUrlInput == "ws://10.0.2.2:8080",
                            onClick = { serverUrlInput = "ws://10.0.2.2:8080" },
                            label = { Text("Emulator Host") }
                        )
                        FilterChip(
                            selected = serverUrlInput == "ws://127.0.0.1:8080",
                            onClick = { serverUrlInput = "ws://127.0.0.1:8080" },
                            label = { Text("USB / ADB Local") }
                        )
                    }

                    if (signalingStatus is SignalingStatus.Error) {
                        Text(
                            text = signalingStatus.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = { onConnectSignaling(serverUrlInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("connect_signaling_button")
                    ) {
                        Text("Connect to Relay Server")
                    }
                }
            }

            // Accessibility Service Readiness
            item {
                SetupSectionCard(
                    icon = Icons.Filled.AccessibilityNew,
                    title = "Accessibility Touch Control",
                    subtitle = if (isAccessibilityEnabled) {
                        "Active — PC/Remote clicks, swipes, and navigation gestures are enabled."
                    } else {
                        "Required so a remote PC or phone can tap and scroll this device without root."
                    },
                    statusBadge = isAccessibilityEnabled
                ) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_accessibility_settings_button")
                    ) {
                        Text(
                            if (isAccessibilityEnabled) {
                                "Manage Accessibility Settings"
                            } else {
                                "Enable AirLink Touch Control"
                            }
                        )
                    }
                }
            }

            // Notification Permission (for MediaProjection Foreground Service)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    SetupSectionCard(
                        icon = Icons.Filled.NotificationsActive,
                        title = "Foreground Stream Notification",
                        subtitle = "Shows live screen-sharing status with an instant Emergency Disconnect button.",
                        statusBadge = hasNotificationPermission
                    ) {
                        if (!hasNotificationPermission) {
                            Button(
                                onClick = {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("request_notification_permission_button")
                            ) {
                                Text("Allow Stream Notifications")
                            }
                        } else {
                            Text(
                                text = "Notification permission granted",
                                style = MaterialTheme.typography.labelMedium,
                                color = SignalGreen
                            )
                        }
                    }
                }
            }

            // Stream Quality Preset
            item {
                SetupSectionCard(
                    icon = Icons.Filled.Speed,
                    title = "Default Video Stream Profile",
                    subtitle = "Hardware-accelerated H.264 / VP8 MediaProjection capture"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StreamQuality.entries.forEach { preset ->
                            FilterChip(
                                selected = deviceConfig.streamQuality == preset,
                                onClick = { onQualityChange(preset) },
                                label = {
                                    Text("${preset.label} (${preset.maxBitrateKbps / 1000.0} Mbps max)")
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Security Policy
            item {
                SetupSectionCard(
                    icon = Icons.Filled.Security,
                    title = "Security & PIN Gate",
                    subtitle = "DTLS-SRTP encryption is always enforced on WebRTC P2P streams."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Auto-reject invalid 4-digit PINs",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = deviceConfig.requirePinApproval,
                            onCheckedChange = onRequirePinApprovalChange,
                            modifier = Modifier.testTag("require_pin_switch")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    statusBadge: Boolean? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (statusBadge != null) {
                Icon(
                    imageVector = if (statusBadge) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                    contentDescription = if (statusBadge) "Enabled" else "Action needed",
                    tint = if (statusBadge) SignalGreen else SignalAmber,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}
