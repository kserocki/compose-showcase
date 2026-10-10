package pl.kserocki.gravityjar.blesschat

import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * JBox2D-backed physics engine for the BlessChat avatar jar.
 *
 * Coordinate system: screen-space (Y points down).
 * Gravity is Vec2(0, 9.8) so bodies fall in the +Y (downward) direction.
 *
 * All public coordinates are in **screen pixels**.
 * Internally, everything is in Box2D metres (PTM = pixels per metre).
 *
 * Bowl boundary: 36 small static CircleShape bodies around the rim —
 * identical approach to the web prototype's Matter.js implementation.
 */
class BlessChatPhysicsEngine(
    /** Screen-pixel X of the bowl centre. */
    val bowlCxPx: Float,
    /** Screen-pixel Y of the bowl centre. */
    val bowlCyPx: Float,
    /** Inner radius of the circular bowl boundary (px). */
    val bowlRadiusPx: Float,
    /** Radius of each avatar circle (px). */
    val avatarRadiusPx: Float,
) {
    companion object {
        /** Pixels per Box2D metre. Tune if simulation feels too fast/slow. */
        const val PTM = 100f
    }

    // ── World ─────────────────────────────────────────────────────────────────

    private val world = World(Vec2(0f, BlessChatConfig.GRAVITY)).also {
        it.setAllowSleep(true)
    }

    /** All dynamic avatar bodies, in insertion order. */
    val bodies = mutableListOf<Body>()

    init {
        runCatching { buildBowl() }
            .onFailure { Timber.e(it, "BlessChatPhysicsEngine: failed to build bowl boundary") }
    }

    // ── Bowl boundary ─────────────────────────────────────────────────────────

    /**
     * Creates 36 tiny static circle bodies arranged around the bowl rim.
     * Exactly mirrors the web prototype's 36-segment static boundary.
     *
     * Wall radius (6 px) chosen so adjacent wall circles overlap enough
     * to prevent 18 px avatar circles from slipping through.
     *   chord between centres  ≈ 2π × 109 / 36 ≈ 19 px
     *   max gap between walls  = 19 − 2×6 = 7 px  << avatar Ø 36 px  ✓
     */
    private fun buildBowl() {
        val segments = 36
        val rM = bowlRadiusPx / PTM
        val cxM = bowlCxPx / PTM
        val cyM = bowlCyPx / PTM
        val wallRadiusM = 6f / PTM

        for (i in 0 until segments) {
            val angle = (i.toFloat() / segments) * 2.0 * PI
            val wx = cxM + cos(angle).toFloat() * rM
            val wy = cyM + sin(angle).toFloat() * rM

            val def = BodyDef().also {
                it.type = BodyType.STATIC
                it.position = Vec2(wx, wy)
            }
            val wallBody = world.createBody(def)
            val shape = CircleShape().also { it.m_radius = wallRadiusM }
            wallBody.createFixture(shape, 0f)
        }
    }

    // ── Avatar body creation ──────────────────────────────────────────────────

    /**
     * Drops a new avatar from 12 o'clock (top of the bowl).
     *
     * @param spreadXPx   Horizontal randomness (±px). 0 = use default (40 % of bowl radius).
     * @param rimOffsetPx How far inside the rim (px) the avatar spawns.
     */
    fun addDropBody(spreadXPx: Float = 0f, rimOffsetPx: Float = 4f): Body? = runCatching {
        // Default spread = 30 % of bowl radius — tight enough for a natural top drop.
        val effectiveSpread = if (spreadXPx > 0f) spreadXPx else bowlRadiusPx * 0.3f
        val nudge = (Random.nextFloat() * 2f - 1f) * effectiveSpread
        val spawnXPx = bowlCxPx + nudge
        // Small Y jitter (0 – 1 avatar radius) so simultaneous drops don't overlap.
        val yJitter = Random.nextFloat() * avatarRadiusPx
        val spawnYPx = bowlCyPx - bowlRadiusPx + avatarRadiusPx + rimOffsetPx + yJitter
        val body = createAvatarBody(spawnXPx, spawnYPx)
        // Moderate horizontal push + strong downward velocity for a snappy drop.
        body.linearVelocity = Vec2(
            (Random.nextFloat() - 0.5f) * 2f,      // ±1 m/s horizontal
            2f + Random.nextFloat() * 2f,            // 2–4 m/s downward
        )
        body
    }.onFailure { Timber.e(it, "BlessChatPhysicsEngine: addDropBody failed") }.getOrNull()

    /**
     * Places a pre-existing avatar at a random position scattered inside the
     * lower-centre portion of the bowl with a small downward initial velocity
     * so bodies settle naturally on first load.
     */
    fun addScatteredBody(): Body? = runCatching {
        val maxR = (bowlRadiusPx - avatarRadiusPx) * 0.55f
        val angle = Random.nextFloat() * 2f * PI.toFloat()
        val dist = Random.nextFloat() * maxR
        val xPx = bowlCxPx + cos(angle) * dist * 0.8f
        val yPx = bowlCyPx + abs(sin(angle)) * dist * 0.5f + 10f

        val body = createAvatarBody(
            xPx = xPx.coerceIn(bowlCxPx - maxR, bowlCxPx + maxR),
            yPx = yPx.coerceIn(bowlCyPx - bowlRadiusPx + avatarRadiusPx, bowlCyPx + maxR),
        )
        // Small downward nudge so initial avatars fall into place
        body.linearVelocity = Vec2(0f, 1f)
        body
    }.onFailure { Timber.e(it, "BlessChatPhysicsEngine: addScatteredBody failed") }.getOrNull()

    private fun createAvatarBody(xPx: Float, yPx: Float): Body {
        val def = BodyDef().also { d ->
            d.type = BodyType.DYNAMIC
            d.position = Vec2(xPx / PTM, yPx / PTM)
            d.linearDamping = BlessChatConfig.LINEAR_DAMPING
            d.angularDamping = BlessChatConfig.ANGULAR_DAMPING
            d.bullet = true // prevents tunnelling at high speed
        }
        val body = world.createBody(def)

        val shape = CircleShape().also { it.m_radius = avatarRadiusPx / PTM }
        val fixtureDef = FixtureDef().also { f ->
            f.shape = shape
            f.density = BlessChatConfig.BODY_DENSITY
            f.friction = BlessChatConfig.BODY_FRICTION
            f.restitution = BlessChatConfig.BODY_RESTITUTION
        }
        body.createFixture(fixtureDef)
        bodies.add(body)
        return body
    }

    fun removeBody(body: Body) {
        runCatching {
            bodies.remove(body)
            world.destroyBody(body)
        }.onFailure { Timber.e(it, "BlessChatPhysicsEngine: removeBody failed") }
    }

    /**
     * Advance the simulation by [dtSec] seconds.
     * Capped at 33 ms to avoid large time steps on frame drops.
     *
     * @param velocityIterations  Constraint solver iterations (8 is Box2D default).
     * @param positionIterations  Position correction iterations (3 is Box2D default).
     */
    fun step(dtSec: Float, velocityIterations: Int = 8, positionIterations: Int = 3) {
        runCatching {
            world.step(dtSec.coerceAtMost(0.033f), velocityIterations, positionIterations)
        }.onFailure { Timber.e(it, "BlessChatPhysicsEngine: step failed") }
    }

    /**
     * Apply random impulses to all dynamic bodies — used when the goal is maxed.
     */
    fun jiggle(forcePxS: Float = BlessChatConfig.JIGGLE_FORCE_PX_S) {
        runCatching {
            val forceN = forcePxS / PTM
            for (body in bodies) {
                if (body.type == BodyType.DYNAMIC) {
                    val impulse = Vec2(
                        (Random.nextFloat() * 2f - 1f) * forceN,
                        (Random.nextFloat() * 2f - 1f) * forceN,
                    )
                    body.applyLinearImpulse(impulse, body.worldCenter)
                }
            }
        }.onFailure { Timber.e(it, "BlessChatPhysicsEngine: jiggle failed") }
    }

    fun xPx(body: Body): Float = runCatching { body.position.x * PTM }
        .onFailure { Timber.e(it, "BlessChatPhysicsEngine: xPx failed") }
        .getOrDefault(bowlCxPx)

    fun yPx(body: Body): Float = runCatching { body.position.y * PTM }
        .onFailure { Timber.e(it, "BlessChatPhysicsEngine: yPx failed") }
        .getOrDefault(bowlCyPx)

    /** True when every dynamic body is sleeping — safe to pause the render loop. */
    fun isAtRest(): Boolean = runCatching { bodies.none { it.type == BodyType.DYNAMIC && it.isAwake } }
        .onFailure { Timber.e(it, "BlessChatPhysicsEngine: isAtRest failed") }
        .getOrDefault(true)
}
