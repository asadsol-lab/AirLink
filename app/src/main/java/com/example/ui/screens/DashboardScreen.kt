package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ConnectedDeviceEntity
import com.example.data.local.DeviceConfig
import com.example.model.DevicePlatform
import com.example.model.RemoteSessionState
import com.example.model.SignalingStatus
import com.example.ui.components.StatusIndicatorBadge
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalGreen

@Composable
fun DashboardScreen(
    deviceConfig: DeviceConfig,
    signalingStatus: SignalingStatus,
    sessionState: RemoteSessionState,
    isAccessibilityEnabled: Boolean,
    isScreenCaptureActive: Boolean,
    targetIdInput: String,
    targetPinInput: String,
    targetPlatform: DevicePlatform,
    recentDevices: List<ConnectedDeviceEntity>,
    onTargetIdChange: (String) -> Unit,
    onTargetPinChange: (String) -> Unit,
    onTargetPlatformChange: (DevicePlatform) -> Unit,
    onConnectClick: () -> Unit,
    onRegeneratePin: () -> Unit,
    onSelectRecentDevice: (ConnectedDeviceEntity) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeleteRecentDevice: (String) -> Unit,
    onOpenSetup: () -> Unit,
    onNotifyCopied: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLocalPin by remember { mutableStateOf(true) }
    val isConnecting = sessionState is RemoteSessionState.RequestingAccess ||
        sessionState is RemoteSessionState.ConnectingPeer

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "AirLink",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Low-Latency P2P Remote Control",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusIndicatorBadge(
                        status = signalingStatus,
                        modifier = Modifier
                            .clickable(onClick = onOpenSetup)
                            .testTag("signaling_status_badge")
                    )
                }
            }

            // Local Device Identity & PIN Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    ElectricCyan.copy(alpha = 0.14f),
                                    CobaltBlue.copy(alpha = 0.10f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    ElectricCyan.copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.outline
                                )
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PhoneAndroid,
                                        contentDescription = "Local Android device",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = stringResource(R.string.my_device_title),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = android.os.Build.MODEL,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(
                                    onClick = { showLocalPin = !showLocalPin },
                                    modifier = Modifier.testTag("toggle_pin_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (showLocalPin) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = "Toggle PIN visibility"
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText("AirLink ID", deviceConfig.deviceId)
                                        )
                                        onNotifyCopied(context.getString(R.string.copied_snackbar))
                                    },
                                    modifier = Modifier.testTag("copy_device_id_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = stringResource(R.string.copy_credentials),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.device_id_label).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = deviceConfig.deviceId.chunked(3).joinToString(" "),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.security_pin_label).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (showLocalPin) deviceConfig.securityPin else "••••",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    IconButton(
                                        onClick = onRegeneratePin,
                                        modifier = Modifier.testTag("regenerate_pin_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = stringResource(R.string.regenerate_pin),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Host readiness pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReadinessPill(
                                icon = Icons.Filled.TouchApp,
                                label = if (isAccessibilityEnabled) "Touch Control Ready" else "Enable Touch Access",
                                active = isAccessibilityEnabled,
                                onClick = onOpenSetup,
                                modifier = Modifier.weight(1f)
                            )
                            ReadinessPill(
                                icon = Icons.Filled.CastConnected,
                                label = if (isScreenCaptureActive) "Sharing Display" else "Mirror Ready",
                                active = isScreenCaptureActive,
                                onClick = onOpenSetup,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Connect to Remote Target Box
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.connect_device_title),
                            style = MaterialTheme.typography.titleLarge
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DevicePlatform.entries.forEach { platform ->
                                FilterChip(
                                    selected = targetPlatform == platform,
                                    onClick = { onTargetPlatformChange(platform) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (platform == DevicePlatform.WINDOWS) {
                                                Icons.Filled.Computer
                                            } else {
                                                Icons.Filled.PhoneAndroid
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    label = { Text(platform.label) },
                                    modifier = Modifier.testTag("platform_chip_${platform.wireValue}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = targetIdInput,
                                onValueChange = onTargetIdChange,
                                label = { Text(stringResource(R.string.target_id_hint)) },
                                placeholder = { Text("123456789") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                leadingIcon = {
                                    Icon(Icons.Filled.Devices, contentDescription = null)
                                },
                                modifier = Modifier
                                    .weight(1.7f)
                                    .testTag("target_id_input")
                            )

                            OutlinedTextField(
                                value = targetPinInput,
                                onValueChange = onTargetPinChange,
                                label = { Text(stringResource(R.string.target_pin_hint)) },
                                placeholder = { Text("4821") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                leadingIcon = {
                                    Icon(Icons.Filled.Lock, contentDescription = null)
                                },
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("target_pin_input")
                            )
                        }

                        AnimatedVisibility(visible = sessionState is RemoteSessionState.Failed) {
                            val err = (sessionState as? RemoteSessionState.Failed)?.reason.orEmpty()
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = onConnectClick,
                            enabled = !isConnecting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("connect_session_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.connecting_action))
                            } else {
                                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.connect_action))
                            }
                        }
                    }
                }
            }

            // Recent Devices Header
            item {
                Text(
                    text = stringResource(R.string.recent_devices_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (recentDevices.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = stringResource(R.string.recent_devices_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recentDevices, key = { it.deviceId }) { device ->
                    RecentDeviceCard(
                        device = device,
                        onSelect = { onSelectRecentDevice(device) },
                        onToggleFavorite = { onToggleFavorite(device.deviceId) },
                        onDelete = { onDeleteRecentDevice(device.deviceId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadinessPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (active) SignalGreen else SignalAmber
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentDeviceCard(
    device: ConnectedDeviceEntity,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val platform = DevicePlatform.fromWire(device.platform)
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        device.lastConnectedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("recent_device_${device.deviceId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (platform == DevicePlatform.WINDOWS) {
                        Icons.Filled.Computer
                    } else {
                        Icons.Filled.PhoneAndroid
                    },
                    contentDescription = platform.label,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column {
                Text(
                    text = device.deviceName.ifBlank { platform.label },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${device.deviceId.chunked(3).joinToString(" ")} • $relativeTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (device.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Favorite device",
                    tint = if (device.isFavorite) SignalAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Remove device",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
