package com.example.earbudseq

import android.content.Context
import com.example.earbudseq.audio.SoundSignature

object PrefsStore {
    private const val PREFS = "earbuds_eq_prefs"
    private const val KEY_SIGNATURE = "signature"
    private const val KEY_GLOBAL_MIX_ENABLED = "global_mix_enabled"
    private const val KEY_IS_CUSTOM_CURVE = "is_custom_curve"
    private const val KEY_CUSTOM_GAINS = "custom_gains_db"
    private const val KEY_BASS_PERCENT = "bass_percent"
    private const val KEY_BASS_ENABLED = "bass_enabled"
    private const val KEY_LOUDNESS_PERCENT = "loudness_percent"
    private const val KEY_LOUDNESS_ENABLED = "loudness_enabled"
    private const val KEY_VIRTUALIZER_PERCENT = "virtualizer_percent"
    private const val KEY_VIRTUALIZER_ENABLED = "virtualizer_enabled"
    private const val KEY_VOLUME = "volume"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveSignature(context: Context, signature: SoundSignature) {
        prefs(context).edit()
            .putString(KEY_SIGNATURE, signature.name)
            .putBoolean(KEY_IS_CUSTOM_CURVE, false)
            .apply()
    }

    fun loadSignature(context: Context): SoundSignature {
        val name = prefs(context).getString(KEY_SIGNATURE, SoundSignature.FLAT.name)
        return try {
            SoundSignature.valueOf(name ?: SoundSignature.FLAT.name)
        } catch (e: Exception) {
            SoundSignature.FLAT
        }
    }

    fun isCustomCurve(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_CUSTOM_CURVE, false)

    fun saveCustomCurve(context: Context, gainsDb: FloatArray) {
        prefs(context).edit()
            .putBoolean(KEY_IS_CUSTOM_CURVE, true)
            .putString(KEY_CUSTOM_GAINS, gainsDb.joinToString(","))
            .apply()
    }

    fun loadCustomCurve(context: Context): FloatArray? {
        val raw = prefs(context).getString(KEY_CUSTOM_GAINS, null) ?: return null
        return try {
            raw.split(",").map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            null
        }
    }

    /** Whether Global Mix EQ (root mode) should auto-restart after a reboot. */
    fun setGlobalMixEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GLOBAL_MIX_ENABLED, enabled).apply()
    }

    fun isGlobalMixEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GLOBAL_MIX_ENABLED, false)

    // Per-effect dial state (percent 0-100, enabled flag), each defaulting to the
    // current preset's baked-in value the first time it's read.

    fun getBassPercent(context: Context, default: Int) =
        prefs(context).getInt(KEY_BASS_PERCENT, default)
    fun setBassPercent(context: Context, percent: Int) =
        prefs(context).edit().putInt(KEY_BASS_PERCENT, percent).apply()

    fun getBassEnabled(context: Context) = prefs(context).getBoolean(KEY_BASS_ENABLED, true)
    fun setBassEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_BASS_ENABLED, enabled).apply()

    fun getLoudnessPercent(context: Context, default: Int) =
        prefs(context).getInt(KEY_LOUDNESS_PERCENT, default)
    fun setLoudnessPercent(context: Context, percent: Int) =
        prefs(context).edit().putInt(KEY_LOUDNESS_PERCENT, percent).apply()

    fun getLoudnessEnabled(context: Context) = prefs(context).getBoolean(KEY_LOUDNESS_ENABLED, true)
    fun setLoudnessEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_LOUDNESS_ENABLED, enabled).apply()

    fun getVirtualizerPercent(context: Context, default: Int) =
        prefs(context).getInt(KEY_VIRTUALIZER_PERCENT, default)
    fun setVirtualizerPercent(context: Context, percent: Int) =
        prefs(context).edit().putInt(KEY_VIRTUALIZER_PERCENT, percent).apply()

    fun getVirtualizerEnabled(context: Context) = prefs(context).getBoolean(KEY_VIRTUALIZER_ENABLED, true)
    fun setVirtualizerEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_VIRTUALIZER_ENABLED, enabled).apply()

    fun getVolume(context: Context, default: Int) = prefs(context).getInt(KEY_VOLUME, default)
    fun setVolume(context: Context, volume: Int) =
        prefs(context).edit().putInt(KEY_VOLUME, volume).apply()
}
