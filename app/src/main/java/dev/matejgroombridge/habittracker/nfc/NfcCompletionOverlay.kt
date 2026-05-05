package dev.matejgroombridge.habittracker.nfc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.ui.components.ConfettiOverlay
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import dev.matejgroombridge.habittracker.ui.theme.HabitIcons

/**
 * The fancy "habit completed" overlay rendered on top of the user's previous
 * app when the NFC action is set to [dev.matejgroombridge.habittracker.data.settings.NfcAction.Overlay].
 *
 * Layered on top of a translucent scrim:
 *  - A confetti shower across the whole screen.
 *  - A pulsing radial glow behind the habit icon.
 *  - A spring-scaled "✓" badge using the habit's colour + icon.
 *  - The habit's name with a soft fade/slide-in.
 *
 * Designed to feel celebratory but disappear quickly — the auto-dismiss
 * timing lives on the activity (~1.8s).
 */
@Composable
fun NfcCompletionOverlay(habit: Habit?, visible: Boolean) {
    val colorEntry = HabitColors.entry(habit?.colorKey ?: Habit.DEFAULT_COLOR_KEY)
    val iconEntry = HabitIcons.entry(habit?.iconKey ?: Habit.DEFAULT_ICON_KEY)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Translucent dark scrim so the overlay reads regardless of what
            // app was previously on screen.
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        // Confetti: trigger as soon as the overlay first composes.
        ConfettiOverlay(trigger = visible, particleCount = 350)

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(220)) +
                scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut(animationSpec = tween(200)) +
                scaleOut(targetScale = 0.85f, animationSpec = tween(200)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp),
            ) {
                PulsingIconBadge(
                    accent = colorEntry.accent,
                    icon = iconEntry.icon,
                )
                Spacer(Modifier.height(28.dp))
                CompletedCard(habitName = habit?.name ?: "Habit completed", accent = colorEntry.accent)
            }
        }
    }
}

@Composable
private fun PulsingIconBadge(
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    // Subtle pulse on the surrounding glow — small enough that it feels
    // alive without being distracting in such a short-lived overlay.
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-scale",
    )

    // One-shot "burst" outward when first appearing — independent of the
    // gentle pulse above.
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        burst.animateTo(1f, animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        // Outer expanding burst ring.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2f
            val r = maxRadius * burst.value
            val alpha = (1f - burst.value).coerceIn(0f, 1f) * 0.55f
            drawCircle(
                color = accent.copy(alpha = alpha),
                radius = r,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
        // Pulsing radial glow behind the badge.
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(pulse)
                .clip(RoundedCornerShape(80.dp))
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.55f),
                            accent.copy(alpha = 0.0f),
                        ),
                    ),
                ),
        )
        // Solid badge in the centre with the habit's icon and a green check
        // ribbon on the corner.
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.size(56.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 36.dp, bottom = 36.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF4CAF50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun CompletedCard(habitName: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 12.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Text(
                text = "Nice work!",
                style = MaterialTheme.typography.labelLarge,
                color = accent.copy(alpha = 1f).let {
                    // Darken accent slightly for legibility on white.
                    Color(it.red * 0.6f, it.green * 0.6f, it.blue * 0.6f)
                },
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = habitName,
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF1A1A1A),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Marked complete for today",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF555555),
            )
        }
    }
}
