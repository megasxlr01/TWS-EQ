package com.example.earbudseq

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.earbudseq.audio.BandMapper
import com.example.earbudseq.audio.EqEngine
import com.example.earbudseq.audio.SoundSignature
import com.example.earbudseq.databinding.ActivityMainBinding
import com.example.earbudseq.system.GlobalMixService
import com.example.earbudseq.system.RootManager
import com.google.android.material.chip.Chip

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var globalMixService: GlobalMixService? = null
    private var globalMixBound = false

    /** Device EQ capabilities, probed once independent of any live player/service. */
    private data class DeviceBandInfo(
        val numberOfBands: Short,
        val centerFrequenciesHz: IntArray,
        val bandLevelRangeMillibel: IntArray
    )
    private var deviceBandInfo: DeviceBandInfo? = null

    private val globalMixConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            globalMixService = (binder as GlobalMixService.LocalBinder).getService()
            globalMixBound = true
        }
        override fun onServiceDisconnected(name: ComponentName) {
            globalMixBound = false
            globalMixService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildPresetChips()
        setupRootControls()
        setupBackgroundReliabilityControls()
        refreshRootStatus()
        probeDeviceBandInfo()
    }

    /**
     * Manual EQ sliders previously only appeared once the built-in test player had a
     * live Equalizer (i.e. after loading a file) — so if you went straight to
     * Global Mix without ever loading a test file, the Manual EQ section stayed
     * empty and did nothing. Band count/frequencies/range are a fixed device
     * property, not something tied to a specific playback session, so we can
     * query them once via a throwaway Equalizer on a freshly generated session id and
     * build the sliders immediately — independent of which mode is actually running.
     */
    private fun probeDeviceBandInfo() {
        Thread {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val probeSessionId = audioManager.generateAudioSessionId()
            val probe = EqEngine(probeSessionId)
            val info = if (probe.numberOfBands > 0) {
                DeviceBandInfo(
                    probe.numberOfBands,
                    probe.centerFrequenciesHz,
                    probe.bandLevelRangeMillibel
                )
            } else null
            probe.release()
            runOnUiThread {
                deviceBandInfo = info
                rebuildBandSlidersFromDevice()
            }
        }.start()
    }

    private fun buildPresetChips() {
        val saved = PrefsStore.loadSignature(this)
        SoundSignature.values().forEach { sig ->
            val chip = Chip(this).apply {
                text = sig.displayName
                isCheckable = true
                isChecked = sig == saved
                setOnClickListener {
                    applySignature(sig)
                }
            }
            binding.chipGroupPresets.addView(chip)
        }
        binding.textPresetDescription.text = saved.description
    }

    private fun applySignature(sig: SoundSignature) {
        PrefsStore.saveSignature(this, sig)
        binding.textPresetDescription.text = sig.description
        globalMixService?.applySignature(sig)
        rebuildBandSlidersFromDevice() // reflect the preset's actual per-band values
    }

    // ---- Background & Reliability ----

    @Suppress("BatteryLife")
    private fun setupBackgroundReliabilityControls() {
        updateBatteryButtonLabel()
        binding.buttonBatteryExemption.setOnClickListener {
            val powerManager = getSystemService(PowerManager::class.java)
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } else {
                try {
                    startActivity(Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    ))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }
    }

    private fun updateBatteryButtonLabel() {
        val powerManager = getSystemService(PowerManager::class.java)
        binding.buttonBatteryExemption.text =
            if (powerManager.isIgnoringBatteryOptimizations(packageName))
                "Battery Optimization Already Disabled ✓"
            else
                "Disable Battery Optimization for This App"
    }

    override fun onResume() {
        super.onResume()
        updateBatteryButtonLabel()
    }

    // ---- Root access (simpler permission grant + optional Global Mix EQ mode) ----

    private fun setupRootControls() {
        binding.buttonRootGrant.setOnClickListener {
            binding.textRootStatus.text = "Granting…"
            Thread {
                val granted = RootManager.grantCaptureAudioOutputPermission(this)
                if (granted) RootManager.restartAudioServer()
                runOnUiThread {
                    refreshRootStatus()
                }
            }.start()
        }

        binding.switchGlobalMix.setOnCheckedChangeListener { _, isChecked ->
            PrefsStore.setGlobalMixEnabled(this, isChecked)
            if (isChecked) {
                startGlobalMix()
            } else {
                globalMixService?.stop()
            }
        }
    }

    private fun hasCaptureAudioOutputPermission(): Boolean {
        return packageManager.checkPermission(
            "android.permission.CAPTURE_AUDIO_OUTPUT", packageName
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshRootStatus() {
        binding.textRootStatus.text = "Checking for root…"
        Thread {
            val hasRoot = RootManager.hasRoot()
            runOnUiThread {
                if (hasRoot) {
                    val hasPermission = hasCaptureAudioOutputPermission()
                    binding.textRootStatus.text = if (hasPermission)
                        "Root detected — capture permission already granted."
                    else
                        "Root detected. Tap below to unlock system-wide capture instantly."
                    binding.buttonRootGrant.isEnabled = !hasPermission
                    binding.switchGlobalMix.isEnabled = true
                    binding.dotRootStatus.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(getColor(com.example.earbudseq.R.color.accent))
                } else {
                    binding.textRootStatus.text = "No root detected on this device."
                    binding.buttonRootGrant.isEnabled = false
                    binding.switchGlobalMix.isEnabled = false
                    binding.dotRootStatus.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(getColor(com.example.earbudseq.R.color.text_faint))
                }
            }
        }.start()
    }

    private fun startGlobalMix() {
        val intent = Intent(this, GlobalMixService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, globalMixConnection, Context.BIND_AUTO_CREATE)
        binding.root.postDelayed({
            val savedSignature = PrefsStore.loadSignature(this)
            val ok = globalMixService?.start(savedSignature) ?: false
            if (!ok) {
                binding.textRootStatus.text = "Global Mix EQ isn't supported on this device's audio HAL."
                binding.switchGlobalMix.isChecked = false
            }
        }, 300)
    }

    /** Builds slider rows matching whatever bands the real device Equalizer exposes. */
    private fun rebuildBandSlidersFromDevice() {
        val info = deviceBandInfo ?: return
        binding.bandSliderContainer.removeAllViews()
        if (info.numberOfBands <= 0) return

        val signature = PrefsStore.loadSignature(this)
        val deviceGains = BandMapper.mapToDeviceBands(
            signature, info.centerFrequenciesHz, info.bandLevelRangeMillibel
        )
        val rangeDb = info.bandLevelRangeMillibel.map { it / 100f }
        val currentGainsDb = FloatArray(info.numberOfBands.toInt())

        for (band in 0 until info.numberOfBands) {
            val row = LayoutInflater.from(this)
                .inflate(com.example.earbudseq.R.layout.item_band_slider, binding.bandSliderContainer, false)
            val freqText = row.findViewById<TextView>(com.example.earbudseq.R.id.textBandFreq)
            val gainText = row.findViewById<TextView>(com.example.earbudseq.R.id.textBandGain)
            val seekBar = row.findViewById<SeekBar>(com.example.earbudseq.R.id.seekBarBand)

            val freqHz = info.centerFrequenciesHz[band]
            freqText.text = if (freqHz >= 1000) "${freqHz / 1000}kHz" else "${freqHz}Hz"

            val gainDb = deviceGains[band] / 100f
            currentGainsDb[band] = gainDb
            val span = rangeDb[1] - rangeDb[0]
            seekBar.max = span.toInt()
            seekBar.progress = (gainDb - rangeDb[0]).toInt()
            gainText.text = "${"%.0f".format(gainDb)}dB"

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val newDb = rangeDb[0] + progress
                    gainText.text = "${"%.0f".format(newDb)}dB"
                    currentGainsDb[band] = newDb
                    applyCustomGainsToAllActiveEngines(currentGainsDb)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            binding.bandSliderContainer.addView(row)
        }
    }

    /** Pushes manual slider changes to whichever EQ mode(s) are actually running right now. */
    private fun applyCustomGainsToAllActiveEngines(gainsDb: FloatArray) {
        globalMixService?.applyCustomBandGainsDb(gainsDb)
    }

    override fun onDestroy() {
        if (globalMixBound) unbindService(globalMixConnection)
        super.onDestroy()
    }
}
