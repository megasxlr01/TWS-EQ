package com.example.earbudseq.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * Wraps Equalizer / BassBoost / Virtualizer for a single Android audio session.
 * One instance = one active playback session getting shaped.
 */
class EqEngine(audioSessionId: Int) {

    private val tag = "EqEngine"

    var equalizer: Equalizer? = null
        private set
    var bassBoost: BassBoost? = null
        private set
    var virtualizer: Virtualizer? = null
        private set

    val numberOfBands: Short
    val bandLevelRangeMillibel: IntArray
    val centerFrequenciesHz: IntArray

    init {
        equalizer = try {
            Equalizer(0, audioSessionId).apply { enabled = true }
        } catch (e: Exception) {
            Log.w(tag, "Equalizer unavailable for session $audioSessionId: ${e.message}")
            null
        }
        bassBoost = try {
            BassBoost(0, audioSessionId).apply { enabled = true }
        } catch (e: Exception) {
            Log.w(tag, "BassBoost unavailable: ${e.message}")
            null
        }
        virtualizer = try {
            Virtualizer(0, audioSessionId).apply { enabled = true }
        } catch (e: Exception) {
            Log.w(tag, "Virtualizer unavailable: ${e.message}")
            null
        }

        numberOfBands = equalizer?.numberOfBands ?: 0
        bandLevelRangeMillibel = equalizer?.bandLevelRange?.let { intArrayOf(it[0].toInt(), it[1].toInt()) }
            ?: intArrayOf(-1500, 1500)
        centerFrequenciesHz = if (numberOfBands > 0) {
            IntArray(numberOfBands.toInt()) { i ->
                (equalizer?.getCenterFreq(i.toShort()) ?: 0) / 1000
            }
        } else IntArray(0)
    }

    /** gainsMillibel size must equal numberOfBands. */
    fun applyBandGains(gainsMillibel: ShortArray) {
        val eq = equalizer ?: return
        for (band in 0 until numberOfBands) {
            val clamped = gainsMillibel.getOrElse(band) { 0 }
                .coerceIn(bandLevelRangeMillibel[0].toShort(), bandLevelRangeMillibel[1].toShort())
            try {
                eq.setBandLevel(band.toShort(), clamped)
            } catch (e: Exception) {
                Log.w(tag, "setBandLevel failed band=$band: ${e.message}")
            }
        }
    }

    /** 0-1000 per BassBoost.setStrength spec (0 = off, 1000 = max). */
    fun setBassBoostStrength(strength: Short) {
        try {
            bassBoost?.setStrength(strength.coerceIn(0, 1000))
        } catch (e: Exception) {
            Log.w(tag, "BassBoost strength failed: ${e.message}")
        }
    }

    /** 0-1000, widens stereo image — useful to compensate for cheap earbuds' narrow soundstage. */
    fun setVirtualizerStrength(strength: Short) {
        try {
            virtualizer?.setStrength(strength.coerceIn(0, 1000))
        } catch (e: Exception) {
            Log.w(tag, "Virtualizer strength failed: ${e.message}")
        }
    }

    fun release() {
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        equalizer = null
        bassBoost = null
        virtualizer = null
    }
}
