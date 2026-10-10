package pl.kserocki.gravityjar.blesschat

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.launch
import org.jbox2d.dynamics.Body
import pl.kserocki.gravityjar.R
import pl.kserocki.gravityjar.data.PriceFormatter
import pl.kserocki.gravityjar.ui.designsystem.Dimens
import pl.kserocki.gravityjar.ui.designsystem.AppTheme
import pl.kserocki.gravityjar.ui.designsystem.appFontFamily
import pl.kserocki.gravityjar.data.BlessChatRemoteConfig
import pl.kserocki.gravityjar.data.DEFAULT_CURRENCY
import pl.kserocki.gravityjar.data.TipJarDonor
import kotlin.math.PI

/** Total canvas — extra room beyond the 48dp circle for the orbiting label. */
private val CanvasSizeDp = 64.dp

/** Centre-line of the progress ring — matches the 48dp container radius exactly. */
private val RingRadiusDp = Dimens.corner24

/** Stroke width of the progress arc. */
private val RingStrokeDp = Dimens.strokeL

/** Radius at which the orbiting label text is drawn. */
private val LabelRadiusDp = Dimens.corner28

/** Sharp drop shadow: #08080842 — black at 26%, offset (1,1), no blur. */
private const val SHADOW_ARGB = 0x42_08_08_08

private class MiniAvatarBody(
    val avatarId: String,
    val donor: TipJarDonor,
    val body: Body,
) {
    var circularShader: BitmapShader? = null
}

/** Compact jar widget with physics avatars, progress ring, and orbiting label. */
@Composable
fun BlessChatMiniJar(
    modifier: Modifier = Modifier,
    roomId: String,
    onClick: () -> Unit = {},
    viewModel: BlessChatViewModel = blessChatViewModel(roomId),
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    val tipJar by viewModel.tipJar.collectAsStateWithLifecycle()
    val blessChatConfig by viewModel.blessChatConfig.collectAsStateWithLifecycle()

    if (tipJar == null) return

    val confirmedAmount = tipJar?.currentAmount ?: 0
    val goalAmount = tipJar?.targetAmount?.coerceAtLeast(1) ?: BlessChatConfig.GOAL_PRICE
    val currency = tipJar?.currency ?: DEFAULT_CURRENCY
    val stepPrice = blessChatConfig.feeForCurrency(currency)?.stepAmount
        ?: BlessChatRemoteConfig.DEFAULT_STEP_AMOUNT
    val avatars by viewModel.expandedAvatars.collectAsStateWithLifecycle()

    val rawFraction = (confirmedAmount.toFloat() / goalAmount).coerceIn(0f, 1f)
    var peakFraction by remember { mutableStateOf(0f) }
    if (rawFraction > peakFraction) peakFraction = rawFraction
    val targetFraction = peakFraction

    val confirmedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 600),
        label = "arcProgress",
    )
    val isGoalReached = confirmedAmount >= goalAmount || peakFraction >= 1f
    val label = if (isGoalReached) {
        stringResource(R.string.live_room_blesschat_stats_goal_reached_label)
    } else {
        val confirmedText = PriceFormatter.formatPrice(confirmedAmount, currency)
        val goalText = PriceFormatter.formatPrice(goalAmount, currency)
        "$confirmedText / $goalText"
    }

    val labelRotation by rememberInfiniteTransition(label = "miniJarOrbit")
        .animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(10_000, easing = LinearEasing)),
            label = "orbitAngle",
        )

    val clipRadiusDp = RingRadiusDp - RingStrokeDp / 2
    val maxAvatars = (goalAmount / stepPrice).coerceAtLeast(1)
    val avatarRadiusDp = BlessChatConfig.computeAvatarRadius(clipRadiusDp, maxAvatars)
        .coerceAtMost(clipRadiusDp * BlessChatConfig.MINI_AVATAR_MAX_RATIO)

    val canvasPx = with(density) { CanvasSizeDp.toPx() }
    val clipRadiusPx = with(density) { clipRadiusDp.toPx() }
    val physicsBowlRadiusPx = clipRadiusPx + 6f
    val avatarRadiusPx = with(density) { avatarRadiusDp.toPx() }
    val sideInt = (avatarRadiusPx * 2).toInt().coerceAtLeast(1)

    val avatarPlaceholderRes = AppTheme.assets.avatarPlaceholder
    val defaultShader: BitmapShader? = remember(context, sideInt, avatarPlaceholderRes) {
        val drawable = ContextCompat.getDrawable(context, avatarPlaceholderRes)
            ?: return@remember null
        val bmp = createBitmap(sideInt, sideInt)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, sideInt, sideInt)
        drawable.draw(canvas)
        BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    val cx = canvasPx / 2f
    val cy = canvasPx / 2f

    val engine = remember(canvasPx, avatarRadiusPx) {
        BlessChatPhysicsEngine(
            bowlCxPx = cx,
            bowlCyPx = cy,
            bowlRadiusPx = physicsBowlRadiusPx,
            avatarRadiusPx = avatarRadiusPx,
        )
    }

    val bodies = remember { mutableStateListOf<MiniAvatarBody>() }
    var isAtRest by remember { mutableStateOf(false) }
    var isMaxed by remember { mutableStateOf(false) }
    var frameNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isGoalReached) {
        isMaxed = isGoalReached
        if (isGoalReached) isAtRest = false
    }

    /** Sync expanded avatars → physics bodies using unique avatarId per dot.
     *  Once goal is reached we never remove bodies so the mini jar stays full. */
    LaunchedEffect(avatars) {
        val existingIds = bodies.map { it.avatarId }.toSet()
        val incomingIds = avatars.map { it.avatarId }.toSet()

        if (!isGoalReached) {
            val toRemove = bodies.filter { it.avatarId !in incomingIds }
            toRemove.forEach { engine.removeBody(it.body) }
            bodies.removeAll { it.avatarId !in incomingIds }
        }

        val isInitialSync = existingIds.isEmpty()
        for (av in avatars) {
            if (av.avatarId !in existingIds) {
                val body = if (isInitialSync) {
                    engine.addScatteredBody()
                } else {
                    engine.addDropBody(
                        spreadXPx = physicsBowlRadiusPx * 0.5f,
                        rimOffsetPx = avatarRadiusPx,
                    )
                } ?: continue
                bodies.add(MiniAvatarBody(av.avatarId, av.donor, body))
            }
        }

        isAtRest = false
    }

    LaunchedEffect(engine) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastNanos == 0L) 0f else (nanos - lastNanos) / 1_000_000_000f
                lastNanos = nanos
                if (!isAtRest) {
                    if (isMaxed) engine.jiggle(BlessChatConfig.MINI_JIGGLE_FORCE_PX_S)
                    engine.step(dt)
                    frameNanos = nanos     // signals Canvas to recompose
                    isAtRest = if (isMaxed) false else engine.isAtRest()
                }
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val tick = frameNanos

    LaunchedEffect(bodies.map { it.avatarId }) {
        for (mb in bodies) {
            val url = mb.donor.avatarUrl ?: continue
            if (mb.circularShader != null) continue
            launch {
                val req = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(sideInt, sideInt)
                    .build()
                val result = context.imageLoader.execute(req)
                if (result is SuccessResult) {
                    val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        mb.circularShader = buildMiniCircularShader(bmp, avatarRadiusPx)
                        isAtRest = false
                    }
                }
            }
        }
    }

    val ringR = with(density) { RingRadiusDp.toPx() }
    val ringSt = with(density) { RingStrokeDp.toPx() }
    val labelR = with(density) { LabelRadiusDp.toPx() }
    val shadowOffsetPx = with(density) { 1.dp.toPx() }

    val ringBorderColor = AppTheme.colors.overMedia.foreground.a26
    val confirmedColor = AppTheme.colors.fg.brand.primary
    val labelTextColor = AppTheme.colors.overMedia.foreground.a76

    val fontFamilyResolver = LocalFontFamilyResolver.current
    val labelTypeface: Typeface = remember(fontFamilyResolver) {
        fontFamilyResolver.resolve(
            fontFamily = appFontFamily,
            fontWeight = FontWeight(650),
            fontStyle = FontStyle.Normal,
            fontSynthesis = FontSynthesis.All,
        ).value as Typeface
    }

    Canvas(
        modifier = modifier
            .size(CanvasSizeDp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    viewModel.trackMiniJarTapped()
                    onClick()
                },
            ),
    ) {
        val bowlClip = Path().apply {
            addOval(Rect(center = Offset(cx, cy), radius = clipRadiusPx))
        }
        clipPath(bowlClip) {
            val nCanvas = drawContext.canvas.nativeCanvas
            for (mb in bodies) {
                val x = engine.xPx(mb.body)
                val y = engine.yPx(mb.body)
                val angle = Math.toDegrees(mb.body.angle.toDouble()).toFloat()

                nCanvas.withRotation(angle, x, y) {
                    val shader = mb.circularShader ?: defaultShader
                    if (shader != null) {
                        shader.setLocalMatrix(Matrix().also { m ->
                            m.setTranslate(x - avatarRadiusPx, y - avatarRadiusPx)
                        })
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
                        drawCircle(x, y, avatarRadiusPx, paint)
                    } else {
                        drawCircle(
                            color = ringBorderColor,
                            radius = avatarRadiusPx,
                            center = Offset(x, y),
                        )
                    }

                }
            }
        }

        drawMiniJarRing(
            cx = cx,
            cy = cy,
            ringR = ringR,
            ringSt = ringSt,
            labelR = labelR,
            shadowOffsetPx = shadowOffsetPx,
            confirmedFraction = confirmedFraction,
            labelRotation = labelRotation,
            label = label,
            labelTypeface = labelTypeface,
            density = density,
            ringBorderColor = ringBorderColor,
            confirmedColor = confirmedColor,
            labelTextColor = labelTextColor,
        )
    }
}

/** Shared drawing: gradient overlay, border ring, progress arc, and orbiting label. */
private fun DrawScope.drawMiniJarRing(
    cx: Float,
    cy: Float,
    ringR: Float,
    ringSt: Float,
    labelR: Float,
    shadowOffsetPx: Float,
    confirmedFraction: Float,
    labelRotation: Float,
    label: String,
    labelTypeface: Typeface,
    density: Density,
    ringBorderColor: Color,
    confirmedColor: Color,
    labelTextColor: Color,
) {
    // 1dp white border ring (full-track).
    drawCircle(
        color = ringBorderColor,
        radius = ringR,
        center = Offset(cx, cy),
        style = Stroke(width = ringSt),
    )

    val arcTopLeft = Offset(cx - ringR, cy - ringR)
    val arcSize = Size(ringR * 2f, ringR * 2f)

    if (confirmedFraction > 0f) {
        drawArc(
            color = confirmedColor,
            startAngle = -90f,
            sweepAngle = 360f * confirmedFraction,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = ringSt, cap = StrokeCap.Round),
        )
    }

    val nativeCanvas = drawContext.canvas.nativeCanvas
    val labelTextSize = with(density) { 8.dp.toPx() }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelTextColor.toArgb()
        textSize = labelTextSize
        typeface = labelTypeface
        textAlign = Paint.Align.LEFT
        letterSpacing = 0.02f
    }
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SHADOW_ARGB
        textSize = labelTextSize
        typeface = labelTypeface
        textAlign = Paint.Align.LEFT
        letterSpacing = 0.02f
    }

    val circumference = (2.0 * PI * labelR).toFloat()
    val textWidth = textPaint.measureText(label)
    val hOffset = (circumference - textWidth) / 2f

    val textPath = android.graphics.Path().also { p ->
        p.addArc(
            RectF(cx - labelR, cy - labelR, cx + labelR, cy + labelR),
            90f, 360f,
        )
    }

    nativeCanvas.withRotation(labelRotation, cx, cy) {
        withSave {
            translate(shadowOffsetPx, shadowOffsetPx)
            drawTextOnPath(label, textPath, hOffset, 0f, shadowPaint)
        }
        drawTextOnPath(label, textPath, hOffset, 0f, textPaint)
    }
}

private fun buildMiniCircularShader(bitmap: Bitmap, radiusPx: Float): BitmapShader {
    val side = (radiusPx * 2).toInt().coerceAtLeast(1)
    val scaled = bitmap.scale(side, side)
    return BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
}

// region Previews

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewMiniJarInProgress() {
    AppTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            MiniJarPreviewContent(fraction = 0.25f, label = "£2.50 / £10.00")
            MiniJarPreviewContent(fraction = 0.60f, label = "£6.00 / £10.00")
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewMiniJarComplete() {
    AppTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            MiniJarPreviewContent(fraction = 0.90f, label = "£9.00 / £10.00")
            MiniJarPreviewContent(fraction = 1.0f, label = "£10.00 / £10.00")
        }
    }
}

@Composable
private fun MiniJarPreviewContent(
    fraction: Float,
    label: String,
) {
    val density = LocalDensity.current

    val labelRotation by rememberInfiniteTransition(label = "previewOrbit")
        .animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(10_000, easing = LinearEasing)),
            label = "previewAngle",
        )

    val canvasPx = with(density) { CanvasSizeDp.toPx() }
    val ringR = with(density) { RingRadiusDp.toPx() }
    val ringSt = with(density) { RingStrokeDp.toPx() }
    val labelR = with(density) { LabelRadiusDp.toPx() }
    val shadowOffsetPx = with(density) { 1.dp.toPx() }
    val cx = canvasPx / 2f
    val cy = canvasPx / 2f

    val fontFamilyResolver = LocalFontFamilyResolver.current
    val labelTypeface: Typeface = remember(fontFamilyResolver) {
        fontFamilyResolver.resolve(
            fontFamily = appFontFamily,
            fontWeight = FontWeight(650),
            fontStyle = FontStyle.Normal,
            fontSynthesis = FontSynthesis.All,
        ).value as Typeface
    }

    val ringBorderColor = AppTheme.colors.overMedia.foreground.a26
    val confirmedColor = AppTheme.colors.fg.brand.primary
    val labelTextColor = AppTheme.colors.overMedia.foreground.a76

    Canvas(modifier = Modifier.size(CanvasSizeDp)) {
        drawMiniJarRing(
            cx = cx,
            cy = cy,
            ringR = ringR,
            ringSt = ringSt,
            labelR = labelR,
            shadowOffsetPx = shadowOffsetPx,
            confirmedFraction = fraction,
            labelRotation = labelRotation,
            label = label,
            labelTypeface = labelTypeface,
            density = density,
            ringBorderColor = ringBorderColor,
            confirmedColor = confirmedColor,
            labelTextColor = labelTextColor,
        )
    }
}