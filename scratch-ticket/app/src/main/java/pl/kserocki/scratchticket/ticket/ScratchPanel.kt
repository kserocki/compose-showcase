package pl.kserocki.scratchticket.ticket

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import pl.kserocki.scratchticket.motion.DeviceTilt
import pl.kserocki.scratchticket.ui.theme.TicketColors

private const val GRID_COLUMNS = 24
private const val GRID_ROWS = 8
private const val REVEAL_THRESHOLD = 0.65f

/**
 * A scratch-off surface: [content] sits underneath a holographic foil that the user wipes away
 * with their finger. Once [REVEAL_THRESHOLD] of the surface is cleared the rest of the foil
 * dissolves and [onRevealed] fires.
 *
 * @param enabled whether the foil accepts scratches yet.
 * @param revealed restores the fully-revealed state, e.g. after recomposition from saved state.
 * @param tilt current device tilt, drives the foil's iridescence.
 */
@Composable
fun ScratchPanel(
    enabled: Boolean,
    revealed: Boolean,
    tilt: () -> DeviceTilt,
    onRevealed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentOnRevealed by rememberUpdatedState(onRevealed)
    val foilPainter = rememberHoloFoilPainter()
    val brushRadius = with(density) { 15.dp.toPx() }

    val strokes = remember { mutableStateListOf<Path>() }
    var strokeVersion by remember { mutableIntStateOf(0) }
    val clearedCells = remember { BooleanArray(GRID_COLUMNS * GRID_ROWS) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var hasScratched by remember { mutableStateOf(false) }
    val foilAlpha = remember { Animatable(if (revealed) 0f else 1f) }

    LaunchedEffect(revealed) {
        if (revealed && foilAlpha.value > 0f) {
            foilAlpha.animateTo(0f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
    }

    fun markCleared(point: Offset): Float {
        if (panelSize.width == 0 || panelSize.height == 0) return 0f
        val cellW = panelSize.width / GRID_COLUMNS.toFloat()
        val cellH = panelSize.height / GRID_ROWS.toFloat()
        for (row in 0 until GRID_ROWS) {
            for (col in 0 until GRID_COLUMNS) {
                val center = Offset((col + 0.5f) * cellW, (row + 0.5f) * cellH)
                if ((center - point).getDistance() <= brushRadius) {
                    clearedCells[row * GRID_COLUMNS + col] = true
                }
            }
        }
        return clearedCells.count { it } / clearedCells.size.toFloat()
    }

    Box(modifier = modifier.clip(RoundedCornerShape(10.dp))) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)

        if (foilAlpha.value > 0f) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .pointerInput(enabled, revealed) {
                        panelSize = size
                        if (!enabled || revealed) return@pointerInput
                        var revealFired = false
                        var lastHapticAt = 0f
                        var travelled = 0f
                        detectDragGestures(
                            onDragStart = { start ->
                                hasScratched = true
                                strokes += Path().apply { moveTo(start.x, start.y) }
                                strokeVersion++
                                markCleared(start)
                            },
                            onDrag = { change, delta ->
                                val point = change.position
                                strokes.lastOrNull()?.lineTo(point.x, point.y)
                                strokeVersion++
                                travelled += delta.getDistance()
                                if (travelled - lastHapticAt > brushRadius * 2.5f) {
                                    lastHapticAt = travelled
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                if (!revealFired && markCleared(point) >= REVEAL_THRESHOLD) {
                                    revealFired = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnRevealed()
                                }
                            },
                        )
                    },
            ) {
                strokeVersion
                with(foilPainter) { paint(tilt(), alpha = foilAlpha.value) }
                strokes.forEach { path ->
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = Stroke(width = brushRadius * 2, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        blendMode = BlendMode.Clear,
                    )
                }
            }
        }

        if (enabled && !revealed && !hasScratched) {
            ScratchHint(Modifier.fillMaxSize())
        }
    }
}

private const val HINT_CYCLE_MS = 3400f
private const val HINT_PRESS_END = 150f
private const val HINT_SCRUB_END = 750f
private const val HINT_LIFT_END = 900f
private const val HINT_HOLD_END = 2100f
private const val HINT_FADE_END = 2350f

/**
 * A ghost finger that scrubs a short diagonal streak into the top-right corner of the foil,
 * lifts, and lets the streak fade before looping, inviting the first scratch.
 */
@Composable
private fun ScratchHint(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "scratchHint")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = HINT_CYCLE_MS,
        animationSpec = infiniteRepeatable(tween(HINT_CYCLE_MS.toInt(), easing = LinearEasing)),
        label = "scratchHintClock",
    )

    Canvas(modifier) {
        val t = clock
        val h = size.height
        val start = Offset(size.width - h * 0.62f, h * 0.17f)
        val end = Offset(size.width - h * 0.22f, h * 0.6f)
        fun pointAt(fraction: Float) = start + (end - start) * fraction

        val scrub = ((t - HINT_PRESS_END) / (HINT_SCRUB_END - HINT_PRESS_END)).coerceIn(0f, 1f)
        val fingerProgress = when {
            scrub < 0.4f -> scrub / 0.4f
            scrub < 0.65f -> 1f - (scrub - 0.4f) / 0.25f * 0.65f
            else -> 0.35f + (scrub - 0.65f) / 0.35f * 0.65f
        }
        val streakHead = if (scrub < 0.4f) fingerProgress else 1f
        val fade = ((t - HINT_HOLD_END) / (HINT_FADE_END - HINT_HOLD_END)).coerceIn(0f, 1f)
        val streakTail = FastOutSlowInEasing.transform(fade) * 0.6f

        if (t > HINT_PRESS_END && t < HINT_FADE_END && streakHead > streakTail) {
            drawLine(
                color = TicketColors.Paper.copy(alpha = 0.85f * (1f - fade)),
                start = pointAt(streakTail),
                end = pointAt(streakHead),
                strokeWidth = h * 0.13f,
                cap = StrokeCap.Round,
            )
        }

        val fingerAlpha = when {
            t < HINT_PRESS_END -> t / HINT_PRESS_END
            t < HINT_SCRUB_END -> 1f
            t < HINT_LIFT_END -> 1f - (t - HINT_SCRUB_END) / (HINT_LIFT_END - HINT_SCRUB_END)
            else -> 0f
        }
        if (fingerAlpha > 0f) {
            val finger = pointAt(fingerProgress)
            drawCircle(Color.Black, radius = h * 0.3f, center = finger, alpha = 0.1f * fingerAlpha)
            drawCircle(Color.Black, radius = h * 0.22f, center = finger, alpha = 0.22f * fingerAlpha)
        }
    }
}
