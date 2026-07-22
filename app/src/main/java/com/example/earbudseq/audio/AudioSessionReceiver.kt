package com.example.earbudseq.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log
import com.example.earbudseq.PrefsStore

/**
 * Some third-party music apps (PowerAmp, certain local players) broadcast
 * their audio session id so system-wide EQ apps can attach to it. This is
 * NOT supported by Spotify, YouTube Music, or most streaming apps — those
 * only expose EQ via their own in-app settings, if at all.
 */
class AudioSessionReceiver : BroadcastReceiver() {

    private val tag = "AudioSessionReceiver"
    // Keeping engines alive per session id for as long as the session is open.
    private val activeEngines = mutableMapOf<Int, EqEngine>()

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (sessionId == -1) return
        val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown"

        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.i(tag, "Opening EQ session $sessionId for $pkg")
                val engine = EqEngine(sessionId)
                activeEngines[sessionId] = engine
                val signature = PrefsStore.loadSignature(context)
                if (engine.numberOfBands > 0) {
                    val gains = BandMapper.mapToDeviceBands(
                        signature, engine.centerFrequenciesHz, engine.bandLevelRangeMillibel
                    )
                    engine.applyBandGains(gains)
                }
                engine.setBassBoostEnabled(signature.bassEnabled)
                engine.setBassBoostStrength(signature.bassBoost)
                engine.setVirtualizerEnabled(signature.virtualizerEnabled)
                engine.setVirtualizerStrength(signature.virtualizer)
                engine.setLoudnessEnabled(signature.loudnessEnabled)
                engine.setLoudnessPercent(signature.loudness)
            }

            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.i(tag, "Closing EQ session $sessionId for $pkg")
                activeEngines.remove(sessionId)?.release()
            }
        }
    }
}
