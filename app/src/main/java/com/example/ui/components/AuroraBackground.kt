package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

@Composable
fun AuroraBackground(color1: Color, color2: Color) {
    val animatedColor1 by animateColorAsState(targetValue = color1, animationSpec = tween(1500), label = "color1")
    val animatedColor2 by animateColorAsState(targetValue = color2, animationSpec = tween(1500), label = "color2")

    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000), RepeatMode.Reverse),
        label = "offset1"
    )
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(6000), RepeatMode.Reverse),
        label = "offset2"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Draw 3 blurred circular blobs with animatedColor1 and animatedColor2
        drawCircle(
            color = animatedColor1.copy(alpha = 0.4f),
            radius = size.width * 0.6f,
            center = Offset(size.width * offset1, size.height * 0.3f)
        )
        drawCircle(
            color = animatedColor2.copy(alpha = 0.35f),
            radius = size.width * 0.5f,
            center = Offset(size.width * (1 - offset1), size.height * 0.7f)
        )
        drawCircle(
            color = animatedColor1.copy(alpha = 0.25f),
            radius = size.width * 0.4f,
            center = Offset(size.width * 0.5f, size.height * offset2)
        )
    }
}
