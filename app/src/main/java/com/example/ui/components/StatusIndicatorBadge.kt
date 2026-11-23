package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.SignalingStatus
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalRed

@Composable
fun StatusIndicatorBadge(
    status: SignalingStatus,
    modifier: Modifier = Modifier
) {
    val (dotColor, label) = when (status) {
        is SignalingStatus.Connected -> SignalGreen to "Relay Online"
        is SignalingStatus.Connecting -> SignalAmber to "Connecting"
        is SignalingStatus.Error -> SignalRed to "Relay Offline"
        is SignalingStatus.Disconnected -> SignalRed to "Disconnected"
    }

    val animatedColor by animateColorAsState(targetValue = dotColor, label = "statusColor")
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status is SignalingStatus.Connected || status is SignalingStatus.Connecting) 0.35f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(animatedColor.copy(alpha = 0.14f))
            .border(1.dp, animatedColor.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(pulseAlpha)
                .clip(CircleShape)
                .background(animatedColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = animatedColor
        )
    }
}
