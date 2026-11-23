package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DevicePlatform
import com.example.model.IncomingCallRequest
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalRed

@Composable
fun AccessAuthorizationDialog(
    request: IncomingCallRequest,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = "Security authorization",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.auth_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${request.callerName} wants to view and control your screen.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (request.callerPlatform == DevicePlatform.WINDOWS) {
                            Icons.Filled.Computer
                        } else {
                            Icons.Filled.PhoneAndroid
                        },
                        contentDescription = request.callerPlatform.label,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = request.callerPlatform.label,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "ID: ${request.senderId.chunked(3).joinToString(" ")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = if (request.pinMatches) {
                        "Security PIN verified. Accepting will start encrypted screen sharing."
                    } else {
                        "Manual confirmation required before sharing your screen."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (request.pinMatches) SignalGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAllow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("auth_allow_button")
            ) {
                Text(stringResource(R.string.auth_dialog_allow))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDeny,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalRed),
                modifier = Modifier.testTag("auth_deny_button")
            ) {
                Text(stringResource(R.string.auth_dialog_deny))
            }
        }
    )
}
