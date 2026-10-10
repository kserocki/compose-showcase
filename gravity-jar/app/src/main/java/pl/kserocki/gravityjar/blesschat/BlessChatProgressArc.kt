package pl.kserocki.gravityjar.blesschat

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import pl.kserocki.gravityjar.ui.designsystem.Dimens
import pl.kserocki.gravityjar.ui.designsystem.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val EaseOutQuint = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** CSS ease-in-out — used for the white arc pulse (HANDOFF.md spec). */
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private data class FlameParticle(
    val startMs: Long,
    val durationMs: Float, // 400–800 ms
    val delayMs: Float, // 0–150 ms start delay
    val spawnDx: Float, // horizontal offset from emitter center, px
    val rise: Float, // upward travel over lifetime, px
    val sway: Float, // horizontal drift over lifetime, px
    val radiusPx: Float, // initial dot radius, px
    val color: Color
)

/**
 * Full-circle progress arc with a floating amount badge that orbits the rim.
 *
 * Layer order (back → front):
 *   1. Dark track ring (full 360°, background)
 *   2. White arc — total progress (confirmed + pending) — drawn BELOW purple
 *   3. Purple arc — confirmed progress — drawn ON TOP of white
 *   4. Flame particles — emitted from arc tip when goal is maxed
 *   5. Amount badge — positioned on the white arc tip, animates with [animTotal]
 *
 * @param confirmedFraction  Purple arc: confirmed tip total ÷ goal (0–1).
 * @param totalFraction      White arc: (confirmed + pending) ÷ goal (0–1).
 * @param isDragging         Switches arc animations to 80 ms linear for snappy tracking.
 * @param amountText         Text shown on the orbiting badge, e.g. "£2.50".
 * @param canvasSize         Side length of the square container (matches the jar canvas).
 */
@Composable
fun BlessChatProgressArc(
    modifier: Modifier = Modifier,
    confirmedFraction: Float,
    totalFraction: Float,
    isDragging: Boolean,
    amountText: String,
    avatarUrl: String? = null,
    canvasSize: Dp = BlessChatConfig.FALLBACK_CANVAS_SIZE_DP,
) {
    val arcRingRadius = canvasSize * BlessChatConfig.ARC_RING_RATIO
    val arcStroke = canvasSize * BlessChatConfig.ARC_STROKE_RATIO

    val arcColorTrack = AppTheme.colors.container.neutral.primary
    val arcColorAccent = AppTheme.colors.fg.brand.primary
    val arcColorWhite = AppTheme.colors.fg.neutral.primary
    val flameHot = AppTheme.colors.decorative.gold
    val flameWarm = AppTheme.colors.decorative.mandarine

    val whiteSpec = if (isDragging) tween<Float>(80, easing = LinearEasing) else tween(300, easing = EaseOutQuint)
    val purpleSpec = if (isDragging) tween<Float>(80, easing = LinearEasing) else tween(350, easing = EaseOutQuint)
    val badgeSpec = tween<Float>(200, easing = FastOutSlowInEasing)

    val animTotal by animateFloatAsState(
        targetValue = totalFraction.coerceIn(0f, 1f),
        animationSpec = whiteSpec,
        label = "arcTotal",
    )
    val animConfirmed by animateFloatAsState(
        targetValue = confirmedFraction.coerceIn(0f, 1f),
        animationSpec = purpleSpec,
        label = "arcConfirmed",
    )
    val badgeAlpha by animateFloatAsState(
        targetValue = if (totalFraction > 0f) 1f else 0f,
        animationSpec = badgeSpec,
        label = "badgeAlpha",
    )

    // Two always-running transitions — one slow (normal), one fast (goal maxed).
    // Switching between them by selecting the right State avoids restarting a single
    // transition when the duration changes, which can cause animation glitches.
    // Spec: HANDOFF.md — 2.5 s normal / 1.25 s maxed, CSS ease-in-out alternate.
    val slowPulse = rememberInfiniteTransition(label = "arcPulseSlow")
    val slowAlpha by slowPulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "slowAlpha",
    )

    val fastPulse = rememberInfiniteTransition(label = "arcPulseFast")
    val fastAlpha by fastPulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(625, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fastAlpha",
    )

    // Use fast pulse when white arc fills the entire goal ring (totalFraction ≥ 1)
    val pulseAlpha = if (totalFraction >= 1f) fastAlpha else slowAlpha
    val whiteArcAlpha = if (totalFraction > 0f) pulseAlpha else 1f

    // Badge center follows the white arc tip.
    // Angle -90° = 12 o'clock start; increases clockwise (screen Y-down convention).
    val badgeAngleRad = ((-90.0 + 360.0 * animTotal) * PI / 180.0).toFloat()

    // Flame particles — emitted from the arc tip whenever either arc reaches 100% of the goal.
    // HANDOFF.md spec: 400–800 ms, ease-out, 6–9 wisps per burst, 250 ms interval.
    val shouldEmit = totalFraction >= 1f || confirmedFraction >= 1f
    val particles = remember { mutableStateListOf<FlameParticle>() }
    var frameTime by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current

    LaunchedEffect(shouldEmit) {
        if (!shouldEmit && particles.isEmpty()) return@LaunchedEffect

        var nextEmitMs = Long.MIN_VALUE // emit on very first frame
        while (true) {
            withFrameMillis { frameMs ->
                frameTime = frameMs

                if (shouldEmit && frameMs >= nextEmitMs) {
                    val count = 6 + Random.nextInt(4) // 6–9 wisps
                    val dpToPx = density.density
                    repeat(count) {
                        val mix = Random.nextFloat()
                        particles.add(
                            FlameParticle(
                                startMs = frameMs,
                                durationMs = 400f + Random.nextFloat() * 400f,
                                delayMs = Random.nextFloat() * 150f,
                                spawnDx = (Random.nextFloat() - 0.5f) * 30f * dpToPx,
                                rise = (25f + Random.nextFloat() * 35f) * dpToPx,
                                sway = (Random.nextFloat() - 0.5f) * 20f * dpToPx,
                                radiusPx = (2f + Random.nextFloat() * 2.5f) * dpToPx,
                                color = Color(
                                    red = flameWarm.red + (flameHot.red - flameWarm.red) * mix,
                                    green = flameWarm.green + (flameHot.green - flameWarm.green) * mix,
                                    blue = flameWarm.blue + (flameHot.blue - flameWarm.blue) * mix,
                                ),
                            )
                        )
                    }
                    nextEmitMs = frameMs + 250L
                }

                // Remove particles whose full lifetime (delay + duration + buffer) has elapsed
                if (particles.isNotEmpty()) {
                    particles.removeAll { p ->
                        frameMs - p.startMs > (p.durationMs + p.delayMs).toLong() + 200L
                    }
                }
            }
            if (!shouldEmit && particles.isEmpty()) break
        }
    }

    // contentAlignment = Center so that Modifier.offset shifts the badge CENTER
    // to (containerCenter + offset), not its top-left corner.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(canvasSize),
    ) {
        Canvas(modifier = Modifier.size(canvasSize)) {
            val r = arcRingRadius.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            val topLeft = Offset(cx - r, cy - r)
            val arcSize = Size(r * 2f, r * 2f)
            val strokeW = arcStroke.toPx()

            // Background track (full circle, no round cap)
            drawArc(
                color = arcColorTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW),
            )

            // White arc (total / pending) — BENEATH purple
            if (animTotal > 0f) {
                drawArc(
                    color = arcColorWhite.copy(alpha = whiteArcAlpha),
                    startAngle = -90f,
                    sweepAngle = animTotal * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )
            }

            // Purple arc (confirmed) — ON TOP of white
            if (animConfirmed > 0f) {
                drawArc(
                    color = arcColorAccent,
                    startAngle = -90f,
                    sweepAngle = animConfirmed * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )
            }

            // Flame particles emitted from the arc tip when goal is maxed
            if (particles.isNotEmpty()) {
                val now = frameTime
                val emitX = cx + r * cos(badgeAngleRad)
                val emitY = cy + r * sin(badgeAngleRad)

                for (p in particles) {
                    val elapsed = (now - p.startMs).toFloat() - p.delayMs
                    if (elapsed < 0f) continue   // still in delay

                    val t = (elapsed / p.durationMs).coerceIn(0f, 1f)
                    val te = 1f - (1f - t) * (1f - t) * (1f - t) // ease-out cubic

                    // opacity: rises 0.9→1 in first 40%, fades 1→0 in last 60%
                    val alpha = if (t < 0.4f) {
                        0.9f + 0.1f * (t / 0.4f)
                    } else {
                        (1f - (t - 0.4f) / 0.6f).coerceAtLeast(0f)
                    }

                    val particleRadius = p.radiusPx * (1f - te * 0.8f) // shrinks to 20%
                    val px = emitX + p.spawnDx + p.sway * te
                    val py = emitY - p.rise * te   // negative Y = upward

                    // Soft outer glow (simulates CSS box-shadow on the particle)
                    drawCircle(
                        color = p.color.copy(alpha = alpha * 0.35f),
                        radius = particleRadius * 2.5f,
                        center = Offset(px, py),
                    )
                    // Core dot
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = particleRadius,
                        center = Offset(px, py),
                    )
                }
            }
        }

        AmountBadge(
            text = amountText,
            avatarUrl = avatarUrl,
            modifier = Modifier
                .alpha(badgeAlpha)
                .offset(
                    x = (arcRingRadius.value * cos(badgeAngleRad)).dp,
                    y = (arcRingRadius.value * sin(badgeAngleRad)).dp,
                ),
        )
    }
}

@Composable
private fun AmountBadge(
    modifier: Modifier = Modifier,
    text: String,
    avatarUrl: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(100.dp))
            .background(AppTheme.colors.container.neutral.primary)
            .padding(Dimens.padding4),
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape),
            )
        }
        Text(
            text = text,
            // footnoteBold = 13 sp W650 — matches Figma Tip Tag spec
            style = AppTheme.typography.footnoteBold,
            modifier = Modifier.padding(horizontal = Dimens.padding4),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141415, widthDp = 320, heightDp = 320)
@Composable
private fun BlessChatProgressArcPreview() {
    BlessChatProgressArc(
        confirmedFraction = 0.2f,
        totalFraction = 0.45f,
        isDragging = false,
        amountText = "£4.50",
    )
}
