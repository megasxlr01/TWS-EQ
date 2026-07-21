package com.example.earbudseq.audio

import android.app.*
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder

/**
 * Runs the built-in player as a foreground service so playback (and the EQ
 * attached to it) survives the screen turning off / app being backgrounded
 * while connected to Bluetooth earbuds.
 */
class EqService : Service() {

    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    var eqEngine: EqEngine? = null
        private set

    var currentSignature: SoundSignature = SoundSignature.FLAT
        private set

    inner class LocalBinder : Binder() {
        fun getService(): EqService = this@EqService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    /**
     * Started (not just bound) foreground services already survive the app being
     * swiped from recents by default, but we make it explicit here rather than
     * relying on default Service behavior, since some OEM launchers patch this.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Deliberately not calling stopSelf() — keep the EQ + playback alive.
        super.onTaskRemoved(rootIntent)
    }

    private fun startForegroundNotification() {
        val channelId = "eq_playback"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Playback", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Earbuds EQ")
            .setContentText("Applying sound signature")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notification)
    }

    fun loadAndPlay(uri: Uri, onReady: () -> Unit) {
        release()
        mediaPlayer = MediaPlayer().apply {
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setDataSource(applicationContext, uri)
            setOnPreparedListener {
                eqEngine = EqEngine(audioSessionId)
                applySignature(currentSignature)
                start()
                onReady()
            }
            prepareAsync()
        }
    }

    fun togglePlayPause(): Boolean {
        val mp = mediaPlayer ?: return false
        return if (mp.isPlaying) {
            mp.pause(); false
        } else {
            mp.start(); true
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

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

    fun release() {
        eqEngine?.release()
        eqEngine = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }
}
