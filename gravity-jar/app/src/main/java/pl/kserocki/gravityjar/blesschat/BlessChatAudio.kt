package pl.kserocki.gravityjar.blesschat

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import pl.kserocki.gravityjar.R

/**
 * Low-latency audio feedback for the BlessChat community coupon.
 *
 * Uses [SoundPool] for sub-millisecond playback of short sound effects.
 * Mirrors audio.js from the web prototype — adapted for Android constraints:
 *
 *   Web: bubble pitch 5.0 base + 0.30/notch ramp (impossible in SoundPool, max = 2.0)
 *   Android: bubble pitch 1.0 base + 0.0625/notch ramp → range [1.0 … 2.0] across 16 notches
 *
 * Volumes match the web config directly (0–1 float).
 *
 * Usage:
 *   1. Create once and call [load] (e.g. in Activity.onCreate or a composition-scoped effect).
 *   2. Call [playBubble] / [playClick] from callbacks.
 *   3. Call [release] when done (e.g. onDestroy or DisposableEffect cleanup).
 */
class BlessChatAudio(private val context: Context) {

    companion object {
        // Volume levels from config.js
        private const val BUBBLE_VOLUME = 0.4f
        private const val CLICK_VOLUME = 0.6f

        // Android pitch re-mapping: web was 5.0 base + 0.30/notch.
        // SoundPool max rate = 2.0, so we map [0, 16 notches] → [1.0, 2.0].
        private const val BUBBLE_PITCH_BASE = 1.0f
        private const val BUBBLE_PITCH_RAMP = 0.0625f  // 1.0 / 16 notches
        private const val CLICK_PITCH = 2.0f            // web had 2.0× playback rate
    }

    private var pool: SoundPool? = null
    private var bubbleSoundId = 0
    private var clickSoundId = 0
    private var loaded = false

    /**
     * Load both sounds asynchronously into [SoundPool].
     * Safe to call multiple times — re-load is a no-op if already loaded.
     */
    fun load() {
        if (loaded) return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        pool = SoundPool.Builder()
            .setMaxStreams(8)         // allow rapid overlapping playback during fast dragging
            .setAudioAttributes(attributes)
            .build()
            .also { sp ->
                sp.setOnLoadCompleteListener { _, _, _ -> }
                bubbleSoundId = sp.load(context, R.raw.bubble, 1)
                clickSoundId = sp.load(context, R.raw.click, 1)
            }

        loaded = true
    }

    /**
     * Play the bubble sound — triggered when an avatar drops into the jar.
     *
     * @param notchIndex  Which notch triggered this drop (1-based).
     *                    Pitch scales linearly with notch: 1.0 at notch 1, 2.0 at notch 16.
     */
    fun playBubble(notchIndex: Int) {
        val sp = pool ?: return
        val pitch = (BUBBLE_PITCH_BASE + notchIndex * BUBBLE_PITCH_RAMP).coerceIn(0.5f, 2.0f)
        sp.play(bubbleSoundId, BUBBLE_VOLUME, BUBBLE_VOLUME, 1, 0, pitch)
    }

    /**
     * Play the click sound — triggered when the slider snaps to a new notch.
     */
    fun playClick() {
        val sp = pool ?: return
        sp.play(clickSoundId, CLICK_VOLUME, CLICK_VOLUME, 1, 0, CLICK_PITCH)
    }

    /**
     * Release the [SoundPool] and all loaded sounds.
     * Call from DisposableEffect cleanup or Activity.onDestroy.
     */
    fun release() {
        pool?.release()
        pool = null
        loaded = false
        bubbleSoundId = 0
        clickSoundId = 0
    }
}
