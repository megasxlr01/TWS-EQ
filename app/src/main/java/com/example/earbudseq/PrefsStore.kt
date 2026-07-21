package com.example.earbudseq

import android.content.Context
import com.example.earbudseq.audio.SoundSignature

object PrefsStore {
    private const val PREFS = "earbuds_eq_prefs"
    private const val KEY_SIGNATURE = "signature"
    private const val KEY_GLOBAL_MIX_ENABLED = "global_mix_enabled"

    fun saveSignature(context: Context, signature: SoundSignature) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SIGNATURE, signature.name)
            .apply()
    }

    fun loadSignature(context: Context): SoundSignature {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SIGNATURE, SoundSignature.FLAT.name)
        return try {
            SoundSignature.valueOf(name ?: SoundSignature.FLAT.name)
        } catch (e: Exception) {
            SoundSignature.FLAT
        }
    }

    /** Whether Global Mix EQ (root mode) should auto-restart after a reboot. */
    fun setGlobalMixEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GLOBAL_MIX_ENABLED, enabled)
            .apply()
    }

    fun isGlobalMixEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_GLOBAL_MIX_ENABLED, false)
    }
}
