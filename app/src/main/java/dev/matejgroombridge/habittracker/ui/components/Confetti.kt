package dev.matejgroombridge.habittracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fires a one-shot burst of falling confetti when [trigger] flips from false
 * to true. Particle count, gravity, and lifetime are tuned for a brief
 * "you did it!" pulse rather than a long shower.
 */
@Composable
fun ConfettiOverlay(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    particleCount: Int = 80,
) {
    // Each call to this composable owns one batch of particles. Re-keying
    // the state on a counter that bumps when `trigger` becomes true gives us
    // a fresh batch every time.
    var burstId by remember { mutableStateOf(0) }
    LaunchedEffect(trigger) {
        if (trigger) burstId++
    }

    if (burstId == 0) return

    val particles = remember(burstId) { generateParticles(particleCount) }
    val progress = remember(burstId) { Animatable(0f) }

    LaunchedEffect(burstId) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 2200,
                easing = androidx.compose.animation.core.LinearEasing,
            ),
        )
        // Tiny grace period before the next burst can render.
        delay(50)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = progress.value
        if (t >= 1f) return@Canvas

        particles.forEach { p ->
            // Position: launched from a horizontal band near the top; the
            // initial velocity carries it outward, gravity pulls it down.
            val tx = p.startXFrac * w
            val ty = -20f
            val vx = p.vx * w
            val vy = p.vy * h
            val gravity = 1.6f * h

            val x = tx + vx * t
            val y = ty + vy * t + 0.5f * gravity * t * t

            // Off-screen culling.
            if (y > h + 40f) return@forEach

            val rotation = p.spin * t * 360f
            val alpha = (1f - t).coerceIn(0f, 1f)

            rotate(degrees = rotation, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - p.width / 2f, y - p.height / 2f),
                    size = androidx.compose.ui.geometry.Size(p.width, p.height),
                )
            }
        }
    }
}

private data class Particle(
    val startXFrac: Float,
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val width: Float,
    val height: Float,
    val color: Color,
)

private fun generateParticles(n: Int): List<Particle> {
    val palette = HabitColors.palette.map { it.accent }
    return List(n) {
        val angle = Random.nextDouble(-PI / 2 - PI / 6, -PI / 2 + PI / 6)
        val speed = Random.nextDouble(0.6, 1.1).toFloat()
        Particle(
            startXFrac = Random.nextFloat() * 0.6f + 0.2f,
            vx = (cos(angle).toFloat()) * speed * 0.8f,
            vy = (sin(angle).toFloat()) * speed * 0.6f,
            spin = if (Random.nextBoolean()) Random.nextFloat() + 0.5f
            else -(Random.nextFloat() + 0.5f),
            width = (6 + Random.nextInt(6)).dp.value * 1.6f,
            height = (10 + Random.nextInt(8)).dp.value * 1.6f,
            color = palette.random(),
        )
    }
}
