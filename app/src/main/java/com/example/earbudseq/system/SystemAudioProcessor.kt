package com.example.earbudseq.system

import android.annotation.SuppressLint
import android.media.*
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import com.example.earbudseq.audio.BandMapper
import com.example.earbudseq.audio.EqEngine
import com.example.earbudseq.audio.SoundSignature
import kotlin.concurrent.thread

/**
 * Captures the system's mixed audio output (everything playing at USAGE_MEDIA /
 * USAGE_GAME / USAGE_UNKNOWN etc), and re-plays it through a fresh AudioTrack that
 * has the Equalizer/BassBoost/Virtualizer attached to its session.
 *
 * Requires CAPTURE_AUDIO_OUTPUT to already be granted (see ShizukuManager) — without
 * it, Android still allows the capture but does NOT mute the original source, so you'd
 * hear both the original and processed audio simultaneously.
 *
 * Caveat: individual apps can opt out of capture via
 * AudioAttributes.Builder#setAllowedCapturePolicy(ALLOW_CAPTURE_BY_NONE), in which case
 * their audio is silently excluded from what we capture. This is an app-side choice we
 * can't override without root.
 */
@SuppressLint("MissingPermission")
class SystemAudioProcessor(
    private val mediaProjection: MediaProjection
) {
    private val tag = "SystemAudioProcessor"

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var eqEngine: EqEngine? = null
    private var captureThread: Thread? = null
    @Volatile private var running = false

    private val sampleRate = 48000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_STEREO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_STEREO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    val currentSignature: SoundSignature
        get() = _currentSignature
    private var _currentSignature: SoundSignature = SoundSignature.FLAT

    fun start(initialSignature: SoundSignature) {
        if (running) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.e(tag, "AudioPlaybackCapture requires API 29+")
            return
        }
        _currentSignature = initialSignature

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val minBufIn = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, encoding)
        val format = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfigIn)
            .build()

        audioRecord = try {
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBufIn * 4)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
        } catch (e: Exception) {
            Log.e(tag, "Failed to create capture AudioRecord: ${e.message}", e)
            null
        }

        val minBufOut = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, encoding)
        val outFormat = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfigOut)
            .build()

        audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(outFormat)
                .setBufferSizeInBytes(minBufOut * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(tag, "Failed to create playback AudioTrack: ${e.message}", e)
            null
        }

        val record = audioRecord
        val track = audioTrack
        if (record == null || track == null) {
            Log.e(tag, "Capture setup failed; aborting start().")
            return
        }

        eqEngine = EqEngine(track.audioSessionId)
        applySignature(_currentSignature)

        running = true
        record.startRecording()
        track.play()

        captureThread = thread(name = "SystemAudioCaptureThread") {
            val bufSize = minBufIn.coerceAtLeast(minBufOut)
            val buffer = ShortArray(bufSize / 2)
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    track.write(buffer, 0, read)
                }
            }
        }
    }

    fun applySignature(signature: SoundSignature) {
        _currentSignature = signature
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

    fun getEqEngine(): EqEngine? = eqEngine

    fun stop() {
        running = false
        captureThread?.join(500)
        captureThread = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null

        try {
            audioTrack?.stop()
        } catch (_: Exception) {}
        audioTrack?.release()
        audioTrack = null

        eqEngine?.release()
        eqEngine = null
    }
}
