package pl.kserocki.gravityjar.blesschat

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.kserocki.gravityjar.data.TipJarDonor
import pl.kserocki.gravityjar.ui.designsystem.Motion
import kotlin.math.sqrt

/** Single avatar dot in the jar, identified by [avatarId] = `"userId_perUserIndex"`. */
data class ExpandedAvatar(
    val avatarId: String,
    val donor: TipJarDonor,
)

/**
 * All tunable constants for the community coupon jar.
 * Mirrors config.js from the web prototype, translated to Android/dp units.
 */
object BlessChatConfig {

    const val GOAL_PRICE = 1000
    const val STEP_PRICE = 50

    // ── Layout ratios (derived from original 200dp design) ─────────────────────

    /** Fallback canvas size for @Preview composables. */
    val FALLBACK_CANVAS_SIZE_DP = 200.dp

    /** Bowl clip radius / canvas size (original: 73 / 200). */
    const val BOWL_RATIO = 0.365f

    /** Progress-arc centre-line radius / canvas size (original: 80 / 200). */
    const val ARC_RING_RATIO = 0.4f

    /** Progress-arc stroke width / canvas size (original: 10 / 200). */
    const val ARC_STROKE_RATIO = 0.05f

    // ── Avatar sizing ──────────────────────────────────────────────────────────

    /** Circle-packing efficiency factor — 0.55 is conservative for JBox2D settling. */
    private const val PACKING_FACTOR = 0.55f

    /** Extra 0 % headroom so avatars have room to jiggle even at full goal. */
    private const val AVATAR_COUNT_BUFFER = 1f

    /** Smallest an avatar can be relative to the bowl radius. */
    private const val AVATAR_MIN_RATIO = 0.06f

    /** Largest an avatar can be relative to the bowl radius (full-size jar). */
    private const val AVATAR_MAX_RATIO = 0.25f

    /** Tighter avatar size cap for the mini jar (vs 0.25 in the full jar). */
    const val MINI_AVATAR_MAX_RATIO = 0.18f

    /**
     * Compute the ideal avatar radius for [maxAvatars] circles inside a bowl of
     * radius [bowlRadius]. Adds a 20 % buffer so avatars can still move when the
     * goal is fully reached.
     *
     * Formula: `r = R × √(packing / (N × 1.2))`
     */
    fun computeAvatarRadius(bowlRadius: Dp, maxAvatars: Int): Dp {
        val n = (maxAvatars.coerceAtLeast(1) * AVATAR_COUNT_BUFFER).toInt().coerceAtLeast(1)
        val r = bowlRadius * sqrt(PACKING_FACTOR / n)
        return r.coerceIn(bowlRadius * AVATAR_MIN_RATIO, bowlRadius * AVATAR_MAX_RATIO)
    }

    // ── Physics ─────────────────────────────────────────────────────────────────

    /** Downward gravity (m/s²) — cranked up for a snappy visual feel in a small bowl. */
    const val GRAVITY = 30f

    /** Air resistance — very low so avatars accelerate freely. */
    const val LINEAR_DAMPING = 0.1f

    /** Rotational resistance on dynamic bodies. */
    const val ANGULAR_DAMPING = 0.3f

    /** Mass density for avatar fixture shapes. */
    const val BODY_DENSITY = 1.0f

    /** Surface friction between avatar bodies. */
    const val BODY_FRICTION = 0.2f

    /** Bounce factor — low so avatars settle quickly without excessive bouncing. */
    const val BODY_RESTITUTION = 0.25f

    /** Random horizontal impulse range when the goal is fully maxed. */
    const val JIGGLE_FORCE_PX_S = 30f

    /** Softer jiggle for the mini jar — subtle bounce only. */
    const val MINI_JIGGLE_FORCE_PX_S = 2f

    /** How far inside the rim (dp) new drop-avatars spawn — positive = inside the bowl. */
    const val DROP_RIM_OFFSET_DP = 4f

    // ── Animation ───────────────────────────────────────────────────────────────

    /** Fade-in duration when an avatar drops into the jar. */
    const val FADE_IN_DURATION_MS = Motion.T3

    /** Fade-out duration when an avatar is removed from the jar. */
    const val FADE_OUT_DURATION_MS = Motion.T2

    const val CONFIRM_POP_SCALE = 0.7f    // overshoot above normal scale
    const val CONFIRM_POP_DURATION_MS = Motion.T1_5
    const val CONFIRM_POP_STAGGER_MS = 10L

    /** Delay between consecutive avatar drops in the stagger queue (ms). */
    const val DROP_STAGGER_MS = 50L

    /** Duration of the jar scale-down + fade-out animation before dismiss (ms). */
    const val JAR_DISMISS_ANIM_MS = 400

    /** Target scale when the jar dismisses (1.0 → this). */
    const val JAR_DISMISS_SCALE = 0.7f
}
