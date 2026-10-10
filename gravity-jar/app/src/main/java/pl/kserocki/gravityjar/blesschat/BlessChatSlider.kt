package pl.kserocki.gravityjar.blesschat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.kserocki.gravityjar.data.PriceFormatter
import pl.kserocki.gravityjar.ui.designsystem.Dimens
import pl.kserocki.gravityjar.ui.designsystem.AppTheme
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val PILL_WIDTH_DP = 16.dp
private val PILL_HEIGHT_DP = 40.dp
private val NOTCH_WIDTH_DP = 3.dp
private val NOTCH_ACTIVE_DP = 16.dp  // height when the slider has passed this notch
private val NOTCH_TALL_DP = 10.dp    // inactive tall notch (whole-£ positions)
private val NOTCH_SHORT_DP = 4.dp    // inactive short notch (half-£ positions)
private val NOTCH_RADIUS_DP = 2.dp
private val TRACK_PADDING_DP = 8.dp   // horizontal inset (mirrors web's 8px notch padding)
private val TRACK_HEIGHT_DP = 40.dp   // pill + notch area height
private val SLIDER_HEIGHT_DP = 80.dp  // total: TRACK_HEIGHT_DP + tooltip area above

/**
 * Notch-based drag slider matching the BlessChat web prototype.
 *
 * - 21 notches (£0–£10 in £0.50 steps); whole-£ notches taller than half-£
 * - Active notches (left of pill) grow to [NOTCH_ACTIVE_DP] with a 120 ms spring
 * - Pill snaps to nearest £0.50; handle travel capped at [dragMax]
 * - Speech-bubble tooltip with downward caret appears above the pill while value > 0 or dragging
 *
 * [value], [dragMax] are read-only — the caller owns state and updates them via callbacks.
 */
@Composable
fun BlessChatSlider(
    value: Float,
    goalPrice: Int,
    stepPrice: Int,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    modifier: Modifier = Modifier,
    dragMax: Float = goalPrice.toFloat(),
    currency: String = "GBP",
    enabled: Boolean = true,
    isWaving: Boolean = false,
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }

    val totalSteps = (goalPrice / stepPrice)
    val totalNotches = totalSteps + 1
    val activeNotchIndex = (value / stepPrice).roundToInt()

    // Dual-sine wave shimmer: 4 s cycle, freq1=2.8, freq2=1.6 (HANDOFF.md spec)
    // Random phase offset per notch so the wave looks organic, not a uniform line.
    val wavePhases = remember(totalNotches) {
        List(totalNotches) { Random.nextFloat() * 2f * PI.toFloat() }
    }
    val waveTransition = rememberInfiniteTransition(label = "sliderWave")
    val waveT by waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waveT",
    )

    val showTooltip = isDragging || value > 0f
    val tooltipAlpha by animateFloatAsState(
        targetValue = if (showTooltip) 1f else 0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "tooltipAlpha",
    )
    val tooltipScale by animateFloatAsState(
        targetValue = if (showTooltip) 1f else 0.9f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "tooltipScale",
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(SLIDER_HEIGHT_DP)) {
        val trackWidthDp: Dp = maxWidth
        val trackWidthPx = with(density) { trackWidthDp.toPx() }
        val paddingPx = with(density) { TRACK_PADDING_DP.toPx() }
        val usableWidthPx = (trackWidthPx - paddingPx * 2f).coerceAtLeast(1f)

        // Maps screen-x (px) → snapped £ value clamped to [dragMax]
        fun xToValue(xPx: Float): Float {
            val relX = (xPx - paddingPx).coerceIn(0f, usableWidthPx)
            val raw = (relX / usableWidthPx) * goalPrice
            return (Math.round(raw / stepPrice.toFloat()) * stepPrice)
                .toFloat()
                .coerceIn(0f, dragMax)
        }

        var dragValue by remember { mutableFloatStateOf(value) }

        // Pill centre x-position (dp) — drives pill placement and tooltip centering
        val fraction = (value / goalPrice).coerceIn(0f, 1f)
        val pillCentreX: Dp = TRACK_PADDING_DP + (trackWidthDp - TRACK_PADDING_DP * 2f) * fraction
        val pillOffsetX: Dp = pillCentreX - PILL_WIDTH_DP / 2f

        val dragModifier = Modifier.pointerInput(dragMax, usableWidthPx) {
            detectDragGestures(
                onDragStart = { offset ->
                    isDragging = true
                    dragValue = xToValue(offset.x)
                    onStart()
                    onDrag(dragValue)
                },
                onDrag = { change, _ ->
                    val newVal = xToValue(change.position.x)
                    if (newVal != dragValue) {
                        dragValue = newVal
                        onDrag(newVal)
                    }
                },
                onDragEnd = {
                    isDragging = false
                    onRelease(dragValue)
                },
                onDragCancel = {
                    isDragging = false
                },
            )
        }
        val tapModifier = Modifier.pointerInput(dragMax, usableWidthPx) {
            detectTapGestures { offset ->
                val tapped = xToValue(offset.x)
                onStart()
                onDrag(tapped)
                onRelease(tapped)
            }
        }

        val interactive = enabled && !isWaving
        val disabledAlpha = if (enabled) 1f else 0.5f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SLIDER_HEIGHT_DP)
                .then(if (interactive) dragModifier else Modifier)
                .then(if (interactive) tapModifier else Modifier),
        ) {
            // Track area: pill + notches anchored to the bottom of the gesture Box.
            // Pill and notches are both vertically centred within this 40dp region.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TRACK_HEIGHT_DP)
                    .align(Alignment.BottomCenter),
            ) {
                // CenterVertically: all notches share the same mid-line as the pill.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TRACK_PADDING_DP)
                        .align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in 0 until totalNotches) {
                        val isTall = (i % 2 == 0)
                        val isActive = i <= activeNotchIndex

                        // Dual-sine wave value for this notch (0..1).
                        // Random phase per notch → organic, non-uniform look.
                        val waveValue = if (isWaving) {
                            val phase = wavePhases[i].toDouble()
                            val t = waveT.toDouble()
                            val w1 = sin(2.0 * PI * 2.8 * t + phase)
                            val w2 = sin(2.0 * PI * 1.6 * t + phase)
                            ((w1 + w2 + 2.0) / 4.0).toFloat() // normalised 0..1
                        } else 0f

                        // Active notches grow to NOTCH_ACTIVE_DP, inactive keep their pattern
                        val baseTargetHeight = if (isActive) NOTCH_ACTIVE_DP
                                               else if (isTall) NOTCH_TALL_DP
                                               else NOTCH_SHORT_DP
                        val animatedBaseHeight by animateDpAsState(
                            targetValue = baseTargetHeight,
                            animationSpec = tween(120, easing = FastOutSlowInEasing),
                            label = "notch_$i",
                        )
                        // When waving: height oscillates between SHORT and ACTIVE
                        val notchHeight = if (isWaving) {
                            val min = NOTCH_SHORT_DP.value
                            val max = NOTCH_ACTIVE_DP.value
                            (min + (max - min) * waveValue).dp
                        } else {
                            animatedBaseHeight
                        }

                        // fg.neutral.primary for active notches; 26% opacity for inactive
                        // When waving: alpha oscillates between 0.15 and 0.6
                        val notchColor = if (isWaving) {
                            AppTheme.colors.fg.neutral.primary.copy(alpha = 0.15f + 0.45f * waveValue)
                        } else if (isActive) {
                            AppTheme.colors.fg.neutral.primary
                        } else {
                            AppTheme.colors.fg.neutral.primary.copy(alpha = 0.26f)
                        }

                        Box(
                            modifier = Modifier
                                .width(NOTCH_WIDTH_DP)
                                .height(notchHeight)
                                .clip(RoundedCornerShape(NOTCH_RADIUS_DP))
                                .background(notchColor),
                        )

                        if (i < totalNotches - 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Pill hidden when waving (only notch shimmer is visible).
                if (!isWaving) {
                    Box(
                        modifier = Modifier
                            .size(PILL_WIDTH_DP, PILL_HEIGHT_DP)
                            .align(Alignment.CenterStart)
                            .offset(x = pillOffsetX)
                            .graphicsLayer { alpha = disabledAlpha }
                            .clip(RoundedCornerShape(100.dp))
                            // fg.neutral.primary = near-white (#F7F7F8)
                            .background(AppTheme.colors.fg.neutral.primary),
                    )
                }
            }

            // Speech-bubble tooltip: hidden when waving — only notch shimmer is visible.
            if (tooltipAlpha > 0.01f && !isWaving) {
                SpeechBubbleTooltip(
                    text = PriceFormatter.formatPrice(value.roundToInt(), currency, minimumFractionDigits = 2),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                val centreXPx = with(density) { pillCentreX.toPx() }
                                placeable.place(
                                    x = (centreXPx - placeable.width / 2f).roundToInt(),
                                    y = 0,
                                )
                            }
                        }
                        .graphicsLayer {
                            alpha = tooltipAlpha * disabledAlpha
                            scaleX = tooltipScale
                            scaleY = tooltipScale
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        },
                )
            }
        }
    }
}

/**
 * Light rounded bubble with a small downward-pointing triangle caret at the bottom.
 * Designed to sit above the slider pill with the caret pointing at the pill centre.
 * Figma: container-inverted-primary (#F7F7F8) bg, inverted-primary-foreground (#080808) text.
 */
@Composable
private fun SpeechBubbleTooltip(text: String, modifier: Modifier = Modifier) {
    val caretColor = AppTheme.colors.bgInt.light.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                // bgInt.light.primary = #F7F7F8 — Figma: container-inverted-primary
                .background(AppTheme.colors.bgInt.light.primary, RoundedCornerShape(Dimens.corner20))
                .padding(horizontal = Dimens.padding10, vertical = Dimens.padding6),
        ) {
            Text(
                text = text,
                // fgInt.light.primary = #080808 — Figma: inverted-primary-foreground
                style = AppTheme.typography.subheadline.copy(color = AppTheme.colors.fgInt.light.primary),
            )
        }
        // Triangle caret pointing downward
        Canvas(
            modifier = Modifier.size(width = Dimens.padding12, height = Dimens.padding6),
        ) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path, caretColor, style = Fill)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141415, widthDp = 360)
@Composable
private fun BlessChatSliderPreview() {
    var value by remember { mutableFloatStateOf(250f) }
    BlessChatSlider(
        value = value,
        goalPrice = BlessChatConfig.GOAL_PRICE,
        stepPrice = BlessChatConfig.STEP_PRICE,
        onStart = {},
        onDrag = { value = it },
        onRelease = {},
        modifier = Modifier.padding(horizontal = Dimens.padding20),
    )
}
