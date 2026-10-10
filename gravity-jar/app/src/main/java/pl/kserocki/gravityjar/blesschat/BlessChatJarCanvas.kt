package pl.kserocki.gravityjar.blesschat

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withRotation
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.launch
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyType
import pl.kserocki.gravityjar.R
import pl.kserocki.gravityjar.ui.designsystem.PreviewWrapper
import pl.kserocki.gravityjar.ui.designsystem.AppTheme
import pl.kserocki.gravityjar.data.TipJarDonor
import kotlin.math.pow
import kotlin.math.sin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath

/** Avatar physics body + animation state. */
class AvatarState(
    val data: TipJarDonor,
    val body: Body,
    val engine: BlessChatPhysicsEngine,
) {
    val xPx get() = engine.xPx(body)
    val yPx get() = engine.yPx(body)
    val angleDeg get() = Math.toDegrees(body.angle.toDouble()).toFloat()

    var opacity: Float by mutableFloatStateOf(0f)
    var fadeStartMs: Long? = 0L          // null = done, 0L = not latched yet
    var targetOpacity: Float = 1f        // 0.5 = pending, 1.0 = confirmed
    var fadeOutStartMs: Long? = null
    var fadeOutStartOpacity: Float = 1f
    var popStartMs: Long? = null
    var physScale: Float by mutableFloatStateOf(1f)
    var isDead: Boolean by mutableStateOf(false)
    var circularShader: BitmapShader? by mutableStateOf(null)
}

/** Bridges [BlessChatJarCanvas] with the parent screen. Use [rememberBlessChatJarState]. */
class BlessChatJarState constructor() {
    internal var engine: BlessChatPhysicsEngine? = null
        private set
    internal var densityScale: Float = 3f
    internal val avatarStates: SnapshotStateList<AvatarState> = mutableStateListOf()
    internal var isMaxed by mutableStateOf(false)
    internal var wakeSignal by mutableIntStateOf(0)

    /** Called every time an avatar actually drops into the jar (after stagger delay).
     *  Parameter = 1-based running drop index (for pitch scaling). */
    var onDrop: ((dropIndex: Int) -> Unit)? = null
    private var dropCounter: Int = 0

    internal data class QueuedDrop(val data: TipJarDonor, val pending: Boolean, val scattered: Boolean)

    /** Pre-engine queue — drops that arrived before the engine was ready. */
    private val pendingDropQueue = mutableListOf<QueuedDrop>()

    /** Stagger queue — drops processed one-per-tick in the frame loop. */
    internal val staggerQueue = mutableListOf<QueuedDrop>()
    internal var lastStaggerDropMs: Long = 0L

    /** Sets the engine + density and flushes queued drops. */
    internal fun initEngine(eng: BlessChatPhysicsEngine, density: Float) {
        densityScale = density
        engine = eng
        if (pendingDropQueue.isNotEmpty()) {
            pendingDropQueue.forEach { dropInternal(eng, it.data, it.pending, it.scattered) }
            pendingDropQueue.clear()
        }
    }

    /** Drop a new avatar immediately. [pending] = half-opacity; [scattered] = random placement. */
    fun drop(data: TipJarDonor, pending: Boolean = false, scattered: Boolean = false) {
        val eng = engine
        if (eng == null) {
            pendingDropQueue.add(QueuedDrop(data, pending, scattered))
            return
        }
        dropInternal(eng, data, pending, scattered)
    }

    /**
     * Enqueue an avatar drop to be staggered over time by the frame loop.
     * Use this for slider/subscription drops so avatars appear one-by-one.
     */
    fun queueDrop(data: TipJarDonor, pending: Boolean = false, scattered: Boolean = false) {
        staggerQueue.add(QueuedDrop(data, pending, scattered))
        wake()
    }

    /** Process one queued drop if enough time has passed since the last one. */
    internal fun processStaggerQueue(nowMs: Long) {
        if (staggerQueue.isEmpty()) return
        if (nowMs - lastStaggerDropMs < BlessChatConfig.DROP_STAGGER_MS) return
        val eng = engine ?: return
        val next = staggerQueue.removeAt(0)
        dropInternal(eng, next.data, next.pending, next.scattered)
        lastStaggerDropMs = nowMs
    }

    private fun dropInternal(
        eng: BlessChatPhysicsEngine,
        data: TipJarDonor,
        pending: Boolean,
        scattered: Boolean,
    ) {
        val body = if (scattered) {
            eng.addScatteredBody()
        } else {
            val rimPx = BlessChatConfig.DROP_RIM_OFFSET_DP * densityScale
            eng.addDropBody(rimOffsetPx = rimPx)
        } ?: return
        val state = AvatarState(data = data, body = body, engine = eng)
        if (pending) state.targetOpacity = 0.5f
        // Scattered bodies still fade in (fadeStartMs = 0L triggers the animation).
        avatarStates.add(state)
        dropCounter++
        onDrop?.invoke(dropCounter)
        wake()
    }

    /** Confirm all pending (half-opacity) avatars with pop + fade-to-full animation. */
    fun confirmPending() {
        val now = System.nanoTime() / 1_000_000L
        var idx = 0
        for (state in avatarStates) {
            if (state.targetOpacity < 1f && !state.isDead && state.fadeOutStartMs == null) {
                val staggerMs = idx * BlessChatConfig.CONFIRM_POP_STAGGER_MS
                state.targetOpacity = 1f
                state.fadeStartMs = now + staggerMs      // fade from current to 1.0
                state.popStartMs = now + staggerMs       // scale pop
                idx++
            }
        }
        wake()
    }

    /**
     * Remove the most-recently-added [count] pending avatars.
     * First drains the stagger queue (cheapest), then fades out already-spawned bodies.
     */
    fun removePending(count: Int) {
        var remaining = count

        // 1. Remove from stagger queue first (not yet spawned — free).
        while (remaining > 0 && staggerQueue.isNotEmpty()) {
            val removed = staggerQueue.indexOfLast { it.pending }
            if (removed >= 0) {
                staggerQueue.removeAt(removed)
                remaining--
            } else break
        }

        // 2. Fade out already-spawned pending bodies.
        if (remaining > 0) {
            val now = System.nanoTime() / 1_000_000L
            for (i in avatarStates.indices.reversed()) {
                if (remaining <= 0) break
                val state = avatarStates[i]
                if (state.targetOpacity < 1f && !state.isDead && state.fadeOutStartMs == null) {
                    state.fadeOutStartOpacity = state.targetOpacity
                    state.fadeStartMs = null
                    state.fadeOutStartMs = now
                    state.body.type = BodyType.STATIC
                    remaining--
                }
            }
        }
        wake()
    }

    /** Remove all pending avatars (queue + spawned). */
    fun removePendingAll() {
        staggerQueue.removeAll { it.pending }
        removePending(avatarStates.count { it.targetOpacity < 1f && !it.isDead })
    }

    /** Remove all avatars with a fade-out animation. */
    fun removeAllDropped() {
        staggerQueue.clear()
        val now = System.nanoTime() / 1_000_000L
        for (state in avatarStates) {
            if (!state.isDead && state.fadeOutStartMs == null) {
                state.fadeOutStartOpacity = state.targetOpacity
                state.fadeStartMs = null
                state.fadeOutStartMs = now
                state.body.type = BodyType.STATIC
            }
        }
        wake()
    }

    /** True when at least one pending avatar exists (in queue or spawned). */
    fun hasPending(): Boolean =
        staggerQueue.any { it.pending } ||
        avatarStates.any { it.targetOpacity < 1f && !it.isDead && it.fadeOutStartMs == null }

    /** Confirm a single pending avatar with pop animation. */
    fun confirmOnePending(): Boolean {
        val pending = avatarStates.firstOrNull {
            it.targetOpacity < 1f && !it.isDead && it.fadeOutStartMs == null
        } ?: return false
        val now = System.nanoTime() / 1_000_000L
        pending.targetOpacity = 1f
        pending.fadeStartMs = now
        pending.popStartMs = now
        wake()
        return true
    }

    /** Enable/disable jiggle impulses when goal is maxed. */
    fun setMaxed(maxed: Boolean) {
        isMaxed = maxed
        if (maxed) wake()
    }

    private fun wake() {
        wakeSignal++
    }
}

@Composable
fun rememberBlessChatJarState() = remember { BlessChatJarState() }

/** Physics-based avatar jar backed by JBox2D. */
@Composable
fun BlessChatJarCanvas(
    modifier: Modifier = Modifier,
    state: BlessChatJarState = rememberBlessChatJarState(),
    canvasSize: Dp = BlessChatConfig.FALLBACK_CANVAS_SIZE_DP,
    avatarRadius: Dp = BlessChatConfig.computeAvatarRadius(
        BlessChatConfig.FALLBACK_CANVAS_SIZE_DP * BlessChatConfig.BOWL_RATIO, 20,
    ),
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    val canvasSizePx = with(density) { canvasSize.toPx() }
    val avatarRadiusPx = with(density) { avatarRadius.toPx() }
    val bowlRadiusPx = canvasSizePx * BlessChatConfig.BOWL_RATIO
    val cx = canvasSizePx / 2f
    val cy = canvasSizePx / 2f

    val engine = remember(canvasSizePx, avatarRadiusPx) {
        BlessChatPhysicsEngine(
            bowlCxPx = cx,
            bowlCyPx = cy,
            bowlRadiusPx = bowlRadiusPx,
            avatarRadiusPx = avatarRadiusPx,
        ).also { eng ->
            state.initEngine(eng, density.density)
        }
    }

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

    val avatarStates = state.avatarStates

    LaunchedEffect(avatarStates.map { it.data.avatarUrl }) {
        for (av in avatarStates) {
            val url = av.data.avatarUrl ?: continue
            if (av.circularShader != null) continue
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
                        av.circularShader = buildCircularShader(bmp, avatarRadiusPx)
                    }
                }
            }
        }
    }

    var isAtRest by remember { mutableStateOf(false) }

    LaunchedEffect(engine) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val wakeSignal = state.wakeSignal
                if (isAtRest && wakeSignal >= 0) isAtRest = false

                val dtSec = if (lastNanos == 0L) 0f
                else (nanos - lastNanos) / 1_000_000_000f
                lastNanos = nanos

                if (!isAtRest) {
                    if (state.isMaxed) engine.jiggle()
                    engine.step(dtSec)
                }

                val nowMs = nanos / 1_000_000L

                // Process stagger queue — drop one avatar per tick interval.
                state.processStaggerQueue(nowMs)

                var hasFading = false

                for (av in avatarStates) {
                    if (av.isDead) continue

                    // Fade-out (always `continue` so fade-in never overrides).
                    val foStart = av.fadeOutStartMs
                    if (foStart != null) {
                        hasFading = true
                        val t = ((nowMs - foStart).toFloat() / BlessChatConfig.FADE_OUT_DURATION_MS).coerceIn(0f, 1f)
                        av.opacity = av.fadeOutStartOpacity * (1f - t).pow(3)
                        av.physScale = 1f - 0.3f * t
                        if (t >= 1f) av.isDead = true
                        continue
                    }

                    val popStart = av.popStartMs
                    if (popStart != null) {
                        hasFading = true
                        val elapsed = nowMs - popStart
                        if (elapsed >= 0L) {
                            val t = (elapsed.toFloat() / BlessChatConfig.CONFIRM_POP_DURATION_MS)
                                .coerceIn(0f, 1f)
                            val popScale = 1f + BlessChatConfig.CONFIRM_POP_SCALE *
                                    sin((t * Math.PI)).toFloat() *
                                    (1f - t.pow(3))
                            val prev = av.physScale
                            if (popScale != prev && prev > 0f) {
                                av.body.type = BodyType.DYNAMIC
                            }
                            av.physScale = popScale
                            if (t >= 1f) {
                                av.popStartMs = null
                                av.physScale = 1f
                            }
                        }
                    }

                    val fiStart = av.fadeStartMs ?: continue
                    hasFading = true
                    if (fiStart == 0L) {
                        av.fadeStartMs = nowMs
                        continue
                    }
                    val elapsed = nowMs - fiStart
                    if (elapsed < 0L) continue
                    val t = (elapsed.toFloat() / BlessChatConfig.FADE_IN_DURATION_MS).coerceIn(0f, 1f)
                    av.opacity += (av.targetOpacity - av.opacity) * (1f - (1f - t).pow(3))
                    if (t >= 1f) av.fadeStartMs = null
                }

                val dead = avatarStates.filter { it.isDead }
                if (dead.isNotEmpty()) {
                    for (av in dead) engine.removeBody(av.body)
                    avatarStates.removeAll { it.isDead }
                }

                if (!state.isMaxed && !hasFading && state.staggerQueue.isEmpty()) {
                    isAtRest = engine.isAtRest()
                }
            }
        }
    }

    val fallbackAvatarColor = AppTheme.colors.overMedia.foreground.a26

    Canvas(modifier = modifier.size(canvasSize)) {
        val bowlClip = ComposePath().apply {
            addOval(Rect(center = Offset(cx, cy), radius = bowlRadiusPx))
        }
        clipPath(bowlClip) {
            for (av in avatarStates) {
                if (!av.isDead) drawAvatar(
                    state = av,
                    radiusPx = avatarRadiusPx,
                    defaultShader = defaultShader,
                    fallbackColor = fallbackAvatarColor,
                )
            }
        }
    }
}

private fun DrawScope.drawAvatar(
    state: AvatarState,
    radiusPx: Float,
    defaultShader: BitmapShader?,
    fallbackColor: Color,
) {
    val x = state.xPx
    val y = state.yPx
    val scale = state.physScale.coerceAtLeast(0.01f)
    val r = radiusPx * scale
    val alpha = state.opacity.coerceIn(0f, 1f)
    if (alpha <= 0f) return

    val canvas = drawContext.canvas.nativeCanvas
    val angle = state.angleDeg

    canvas.withRotation(angle, x, y) {
        val shader = state.circularShader ?: defaultShader
        if (shader != null) {
            shader.setLocalMatrix(Matrix().also { it.setTranslate(x - r, y - r) })
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.shader = shader
                this.alpha = (alpha * 255).toInt()
            }
            drawCircle(x, y, r, paint)
        } else {
            drawCircle(
                color = fallbackColor.copy(alpha = alpha),
                radius = r,
                center = Offset(x, y),
            )
        }

    }
}

private fun buildCircularShader(bitmap: Bitmap, radiusPx: Float): BitmapShader {
    val side = (radiusPx * 2).toInt().coerceAtLeast(1)
    val scaled = bitmap.scale(side, side)
    return BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
}

@Preview(widthDp = 320, heightDp = 320, showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
private fun BlessChatJarCanvasPreview() {
    PreviewWrapper {
        BlessChatJarCanvas()
    }
}
