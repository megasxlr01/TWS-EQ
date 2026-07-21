package com.example.earbudseq.system

import android.app.*
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.example.earbudseq.audio.SoundSignature

class SystemCaptureService : Service() {

    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private var processor: SystemAudioProcessor? = null

    inner class LocalBinder : Binder() {
        fun getService(): SystemCaptureService = this@SystemCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep capturing/processing after the app is swiped from recents.
        super.onTaskRemoved(rootIntent)
    }

    private fun startForegroundNotification() {
        val channelId = "system_eq"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "System-wide EQ", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("System-wide EQ active")
            .setContentText("Reshaping all device audio")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(2, notification)
    }

    /** resultCode/data come from the MediaProjection consent dialog result. */
    fun startCapture(resultCode: Int, data: Intent, initialSignature: SoundSignature) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = manager.getMediaProjection(resultCode, data)
        val projection = mediaProjection ?: return
        processor = SystemAudioProcessor(projection).also {
            it.start(initialSignature)
        }
    }

    fun applySignature(signature: SoundSignature) {
        processor?.applySignature(signature)
    }

    fun applyCustomBandGainsDb(gainsDb: FloatArray) {
        processor?.applyCustomBandGainsDb(gainsDb)
    }

    fun isCapturing(): Boolean = processor != null

    fun stopCapture() {
        processor?.stop()
        processor = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onDestroy() {
        stopCapture()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
