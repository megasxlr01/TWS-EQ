package com.example.earbudseq.audio

import kotlin.math.ln

/**
 * Cheap earbuds' actual chipset-level EQ is opaque, but the phone's Equalizer
 * effect on different devices exposes different numbers of bands (commonly
 * 5, sometimes 4 or 6) at different center frequencies. This interpolates our
 * fixed reference curve onto whatever bands the current device actually has,
 * in log-frequency space (matches how the ear perceives pitch spacing).
 */
object BandMapper {

    fun mapToDeviceBands(
        signature: SoundSignature,
        deviceCenterFreqsHz: IntArray,
        deviceRangeMillibel: IntArray
    ): ShortArray {
        val refFreqs = SoundSignature.referenceFrequencies
        val refGains = signature.bandGainsDb

        return ShortArray(deviceCenterFreqsHz.size) { i ->
            val freq = deviceCenterFreqsHz[i].coerceAtLeast(1)
            val gainDb = interpolateLog(freq, refFreqs, refGains)
            val millibel = (gainDb * 100).toInt()
            millibel.coerceIn(deviceRangeMillibel[0], deviceRangeMillibel[1]).toShort()
        }
    }

    private fun interpolateLog(freqHz: Int, refFreqs: IntArray, refGains: FloatArray): Float {
        val x = ln(freqHz.toDouble())
        if (x <= ln(refFreqs.first().toDouble())) return refGains.first()
        if (x >= ln(refFreqs.last().toDouble())) return refGains.last()

        for (i in 0 until refFreqs.size - 1) {
            val x0 = ln(refFreqs[i].toDouble())
            val x1 = ln(refFreqs[i + 1].toDouble())
            if (x in x0..x1) {
                val t = ((x - x0) / (x1 - x0)).toFloat()
                return refGains[i] + t * (refGains[i + 1] - refGains[i])
            }
        }
        return 0f
    }
}
