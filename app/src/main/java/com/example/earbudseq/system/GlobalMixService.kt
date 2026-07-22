package com.example.earbudseq.system

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.example.earbudseq.audio.BandMapper
import com.example.earbudseq.audio.EqEngine
import com.example.earbudseq.audio.SoundSignature

/**
 * The app's only system-wide EQ path: attaches Equalizer/BassBoost/Virtualizer/
 * LoudnessEnhancer directly to audio session 0 — Android's global output mix.
 * Requires root (see RootManager) since regular apps generally can't rely on
 * session-0 effects actually reaching hardware output.
 *
 * Tradeoffs to be aware of:
 *   + No consent popup, ever
 *   + Low latency (it's an insert effect, not capture+replay)
 *   + Not affected by apps that set ALLOW_CAPTURE_BY_NONE (that flag only blocks
 *     audio *capture*, not insert effects on the shared output mix)
 *   - Whether session-0 effects actually reach Bluetooth output depends on the
 *     device's audio HAL / OEM audio policy — reliable on many AOSP-close devices,
 *     inconsistent on some heavily-customized ones (common on budget/regional
 *     Chinese phone skins).
 */
class GlobalMixService : Service() {

    companion object {
        /** Set by BootReceiver right before starting the service after a reboot. */
        var pendingBootSignature: SoundSignature? = null
    }

    private val binder = LocalBinder()
    private var eqEngine: EqEngine? = null
    private var currentSignature: SoundSignature = SoundSignature.FLAT

    inner class LocalBinder : Binder() {
        fun getService(): GlobalMixService = this@GlobalMixService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        // If we were started by BootReceiver (no activity bound yet), start processing
        // immediately with the last-used preset rather than waiting for the app to open.
        pendingBootSignature?.let { sig ->
            start(sig)
            pendingBootSignature = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the global-mix EQ attached after the app is swiped from recents.
        super.onTaskRemoved(rootIntent)
    }

    private fun startForegroundNotification() {
        val channelId = "global_mix_eq"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Global mix EQ", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Global Mix EQ active (root)")
            .setContentText("Equalizer attached to system output mix")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(3, notification)
    }

    fun start(initialSignature: SoundSignature): Boolean {
        if (eqEngine != null) return true
        // Session 0 = AudioManager.AUDIO_SESSION_ID_GENERATE is NOT this; 0 is the
        // reserved global output mix session id.
        val engine = EqEngine(0)
        if (engine.equalizer == null) {
            engine.release()
            return false
        }
        eqEngine = engine
        currentSignature = initialSignature
        applySignature(initialSignature)
        return true
    }

    fun applySignature(signature: SoundSignature) {
        currentSignature = signature
        val eq = eqEngine ?: return
        if (eq.numberOfBands > 0) {
            val gains = BandMapper.mapToDeviceBands(
                signature, eq.centerFrequenciesHz, eq.bandLevelRangeMillibel
            )
            eq.applyBandGains(gains)
        }
        eq.setBassBoostStrength(signature.bassBoost)
        eq.setVirtualizerStrength(signature.virtualizer)
    }

    fun applyCustomBandGainsDb(gainsDb: FloatArray) {
        val eq = eqEngine ?: return
        val millibel = ShortArray(gainsDb.size) { (gainsDb[it] * 100).toInt().toShort() }
        eq.applyBandGains(millibel)
    }

    /** Adjusts a single band without touching the others — used by curve-drag edits. */
    fun applySingleBandGainDb(band: Int, gainDb: Float) {
        eqEngine?.applyBandGain(band, (gainDb * 100).toInt().toShort())
    }

    fun setBassBoostPercent(percent: Int) {
        eqEngine?.setBassBoostStrength((percent.coerceIn(0, 100) * 10).toShort())
    }

    fun setVirtualizerPercent(percent: Int) {
        eqEngine?.setVirtualizerStrength((percent.coerceIn(0, 100) * 10).toShort())
    }

    fun setLoudnessPercent(percent: Int) {
        eqEngine?.setLoudnessPercent(percent)
    }

    fun setBassBoostEnabled(enabled: Boolean) = eqEngine?.setBassBoostEnabled(enabled)
    fun setVirtualizerEnabled(enabled: Boolean) = eqEngine?.setVirtualizerEnabled(enabled)
    fun setLoudnessEnabled(enabled: Boolean) = eqEngine?.setLoudnessEnabled(enabled)

    fun getEqEngine(): EqEngine? = eqEngine

    fun isActive(): Boolean = eqEngine != null

    fun stop() {
        eqEngine?.release()
        eqEngine = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        eqEngine?.release()
        eqEngine = null
        super.onDestroy()
    }
}
