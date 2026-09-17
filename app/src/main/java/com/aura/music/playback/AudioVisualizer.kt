package com.aura.music.playback

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Live music-reactive FFT data from ExoPlayer's audio session.
 *
 * Android's [Visualizer] observes the output mix — no RECORD_AUDIO permission
 * needed. Exposes [BUCKET_COUNT] smoothed 0..1 magnitudes at ~30fps while
 * music plays. Any failure (unsupported device, dead session) degrades
 * silently to zeros and the UI falls back to its idle animation.
 */
@Singleton
class AudioVisualizer @Inject constructor() {

    private val _buckets = MutableStateFlow(FloatArray(BUCKET_COUNT))
    val buckets: StateFlow<FloatArray> = _buckets.asStateFlow()

    private val smooth = FloatArray(BUCKET_COUNT)
    private var visualizer: Visualizer? = null
    private var attachedSession = 0

    @Synchronized
    fun attach(sessionId: Int) {
        if (sessionId == 0 || sessionId == attachedSession) return
        release()
        try {
            val captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            visualizer = Visualizer(sessionId).apply {
                this.captureSize = captureSize
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) = Unit

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft == null || fft.size < 4) return
                            onFft(fft)
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true
                )
                enabled = true
            }
            attachedSession = sessionId
        } catch (e: Exception) {
            Log.w("AudioVisualizer", "attach failed: ${e.message}")
            release()
        }
    }

    private fun onFft(fft: ByteArray) {
        // FFT layout: pairs of (real, imag) bytes; bin 0 is DC (skip it).
        val binCount = fft.size / 2
        if (binCount < 8) return

        // Music lives in the lower half of the spectrum — focus buckets there
        // instead of wasting most bars on near-silent highs.
        val usableBins = (binCount / 2).coerceAtLeast(8)

        // Exponential bucket edges: fine resolution for bass, coarse for highs.
        for (b in 0 until BUCKET_COUNT) {
            val from = 1 + ((usableBins - 1) * (b.toFloat() / BUCKET_COUNT).pow2()).toInt()
            val to = 1 + ((usableBins - 1) * ((b + 1).toFloat() / BUCKET_COUNT).pow2()).toInt()
            var sum = 0f
            var count = 0
            for (i in from until to.coerceAtLeast(from + 1)) {
                if (i >= usableBins) break
                val re = fft[2 * i].toInt()
                val im = fft[2 * i + 1].toInt()
                sum += sqrt((re * re + im * im).toFloat()) / 128f
                count++
            }
            // Square-root compression lifts the midrange so bars breathe at
            // many heights instead of slamming between 0 and 1.
            val raw = if (count > 0) sum / count else 0f
            val target = sqrt(raw * GAIN).coerceIn(0f, 1f)
            // Fast attack, slow release — the professional look.
            val rate = if (target > smooth[b]) 0.55f else 0.18f
            smooth[b] += (target - smooth[b]) * rate
        }
        _buckets.value = smooth.copyOf()
    }

    private fun Float.pow2(): Float = this * this

    @Synchronized
    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
        attachedSession = 0
    }

    companion object {
        const val BUCKET_COUNT = 24
        private const val GAIN = 1.6f
    }
}
