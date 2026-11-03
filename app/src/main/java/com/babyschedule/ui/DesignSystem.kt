package com.babyschedule.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.babyschedule.ui.theme.SeriesColors
import kotlin.random.Random

/*
 * Small design system for buttons:
 *  - PrimaryButton: the one main action on a screen (solid, primary colour).
 *  - SecondaryButton: everything else (outlined, secondary colour).
 */

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (subtitle != null) 28.dp else 20.dp))
            Spacer(Modifier.width(if (subtitle != null) 12.dp else 8.dp))
        }
        if (subtitle != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, maxLines = 1)
    }
}

private class ConfettiPiece(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val drift: Float,
    val spin: Float,
    val size: Float,
    val color: Color,
)

/**
 * A short burst of falling confetti, drawn over the screen. Each new [trigger]
 * value plays it once; it never blocks touches.
 */
@Composable
fun ConfettiOverlay(trigger: Int) {
    if (trigger == 0) return
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val pieces = remember(trigger) {
        val r = Random(trigger)
        val palette = listOf(primary, secondary, SeriesColors.Left, SeriesColors.Bottle, SeriesColors.Wet, SeriesColors.Mixed)
        List(90) {
            ConfettiPiece(
                x = r.nextFloat(),
                delay = r.nextFloat() * 0.25f,
                speed = 0.8f + r.nextFloat() * 0.6f,
                drift = (r.nextFloat() - 0.5f) * 0.3f,
                spin = (r.nextFloat() - 0.5f) * 900f,
                size = 6f + r.nextFloat() * 6f,
                color = palette[r.nextInt(palette.size)],
            )
        }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.animateTo(1f, tween(durationMillis = 2600, easing = LinearEasing))
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        pieces.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -20f + local * local * size.height * 1.1f * p.speed
            val x = p.x * size.width + p.drift * size.width * local
            val alpha = if (local > 0.8f) (1f - local) / 0.2f else 1f
            val w = p.size.dp.toPx()
            rotate(p.spin * local, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - w / 2f, y - w / 4f),
                    size = Size(w, w / 2f),
                )
            }
        }
    }
}
