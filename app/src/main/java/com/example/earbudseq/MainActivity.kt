package com.example.earbudseq

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.PopupMenu
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.earbudseq.audio.BandMapper
import com.example.earbudseq.audio.EqEngine
import com.example.earbudseq.audio.SoundSignature
import com.example.earbudseq.databinding.ActivityMainBinding
import com.example.earbudseq.system.GlobalMixService
import com.example.earbudseq.system.RootManager
import com.example.earbudseq.ui.EqualizerCurveView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var globalMixService: GlobalMixService? = null
    private var globalMixBound = false

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
            pushAllStateToService()
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

        setupEqMenu()
        setupCurveView()
        setupPresetDropdown()
        setupDials()
        setupVolumeSlider()
        setupBackgroundReliabilityControls()

        probeDeviceBandInfo()
        refreshRootStatus()
    }

    override fun onResume() {
        super.onResume()
        updateBatteryButtonLabel()
    }

    // ---- Device band info probe (independent of any live session) ----

    private fun probeDeviceBandInfo() {
        Thread {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val probeSessionId = audioManager.generateAudioSessionId()
            val probe = EqEngine(probeSessionId)
            val info = if (probe.numberOfBands > 0) {
                DeviceBandInfo(probe.numberOfBands, probe.centerFrequenciesHz, probe.bandLevelRangeMillibel)
            } else null
            probe.release()
            runOnUiThread {
                deviceBandInfo = info
                rebuildCurveFromDevice()
            }
        }.start()
    }

    // ---- Equalizer card: menu, master toggle, curve, presets ----

    private fun setupEqMenu() {
        binding.buttonEqMenu.setOnClickListener { anchor ->
            val menu = PopupMenu(this, anchor)
            menu.menu.add("Reset to Flat")
            menu.setOnMenuItemClickListener {
                applySignature(SoundSignature.FLAT)
                true
            }
            menu.show()
        }
    }

    private fun setupCurveView() {
        binding.eqCurveView.onBandChanged = { index, newGainDb ->
            val info = deviceBandInfo ?: return@setOnBandChanged
            globalMixService?.applySingleBandGainDb(index, newGainDb)
            val current = binding.eqCurveView.bands.map { it.gainDb }.toFloatArray()
            PrefsStore.saveCustomCurve(this, current)
            binding.textSelectedPreset.text = "Custom"
        }

        binding.switchMasterEq.setOnCheckedChangeListener { _, isChecked ->
            PrefsStore.setGlobalMixEnabled(this, isChecked)
            if (isChecked) startGlobalMix() else globalMixService?.stop()
        }
    }

    private fun rebuildCurveFromDevice() {
        val info = deviceBandInfo ?: return
        val rangeDb = info.bandLevelRangeMillibel.map { it / 100f }

        val customGains = if (PrefsStore.isCustomCurve(this)) PrefsStore.loadCustomCurve(this) else null
        val bands = if (customGains != null && customGains.size == info.numberOfBands.toInt()) {
            info.centerFrequenciesHz.mapIndexed { i, freqHz ->
                EqualizerCurveView.Band(freqLabel(freqHz), customGains[i], rangeDb[0], rangeDb[1])
            }
        } else {
            val signature = PrefsStore.loadSignature(this)
            val deviceGains = BandMapper.mapToDeviceBands(signature, info.centerFrequenciesHz, info.bandLevelRangeMillibel)
            info.centerFrequenciesHz.mapIndexed { i, freqHz ->
                EqualizerCurveView.Band(freqLabel(freqHz), deviceGains[i] / 100f, rangeDb[0], rangeDb[1])
            }
        }
        binding.eqCurveView.bands = bands
        binding.textSelectedPreset.text =
            if (PrefsStore.isCustomCurve(this)) "Custom" else PrefsStore.loadSignature(this).displayName
    }

    private fun freqLabel(freqHz: Int): String =
        if (freqHz >= 1000) "${freqHz / 1000}k" else "$freqHz"

    private fun setupPresetDropdown() {
        binding.buttonPresetDropdown.setOnClickListener { anchor ->
            val menu = PopupMenu(this, anchor, Gravity.END)
            SoundSignature.values().forEach { sig ->
                menu.menu.add(sig.displayName)
            }
            menu.setOnMenuItemClickListener { item ->
                val sig = SoundSignature.values().firstOrNull { it.displayName == item.title }
                sig?.let { applySignature(it) }
                true
            }
            menu.show()
        }
    }

    private fun applySignature(sig: SoundSignature) {
        PrefsStore.saveSignature(this, sig)
        globalMixService?.applySignature(sig)
        rebuildCurveFromDevice()

        // Presets also set sensible defaults for the three dials, saved so they
        // persist independent of the curve itself.
        val bassPercent = (sig.bassBoost / 10)
        val virtPercent = (sig.virtualizer / 10)
        PrefsStore.setBassPercent(this, bassPercent)
        PrefsStore.setVirtualizerPercent(this, virtPercent)
        binding.dialBassBoost.value = bassPercent
        binding.dialVirtualizer.value = virtPercent
        globalMixService?.setBassBoostPercent(bassPercent)
        globalMixService?.setVirtualizerPercent(virtPercent)
    }

    // ---- Bass Boost / Loudness / Virtualizer dials ----

    private fun setupDials() {
        val bassDefault = PrefsStore.loadSignature(this).bassBoost / 10
        val virtDefault = PrefsStore.loadSignature(this).virtualizer / 10

        binding.dialBassBoost.value = PrefsStore.getBassPercent(this, bassDefault)
        binding.switchBassBoost.isChecked = PrefsStore.getBassEnabled(this)
        binding.dialBassBoost.onValueChanged = { percent ->
            PrefsStore.setBassPercent(this, percent)
            globalMixService?.setBassBoostPercent(percent)
        }
        binding.switchBassBoost.setOnCheckedChangeListener { _, enabled ->
            PrefsStore.setBassEnabled(this, enabled)
            globalMixService?.setBassBoostEnabled(enabled)
        }

        binding.dialLoudness.value = PrefsStore.getLoudnessPercent(this, 0)
        binding.switchLoudness.isChecked = PrefsStore.getLoudnessEnabled(this)
        binding.dialLoudness.onValueChanged = { percent ->
            PrefsStore.setLoudnessPercent(this, percent)
            globalMixService?.setLoudnessPercent(percent)
        }
        binding.switchLoudness.setOnCheckedChangeListener { _, enabled ->
            PrefsStore.setLoudnessEnabled(this, enabled)
            globalMixService?.setLoudnessEnabled(enabled)
        }

        binding.dialVirtualizer.value = PrefsStore.getVirtualizerPercent(this, virtDefault)
        binding.switchVirtualizer.isChecked = PrefsStore.getVirtualizerEnabled(this)
        binding.dialVirtualizer.onValueChanged = { percent ->
            PrefsStore.setVirtualizerPercent(this, percent)
            globalMixService?.setVirtualizerPercent(percent)
        }
        binding.switchVirtualizer.setOnCheckedChangeListener { _, enabled ->
            PrefsStore.setVirtualizerEnabled(this, enabled)
            globalMixService?.setVirtualizerEnabled(enabled)
        }
    }

    // ---- Volume ----

    private fun setupVolumeSlider() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        binding.seekBarVolume.max = max
        binding.seekBarVolume.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                PrefsStore.setVolume(this@MainActivity, progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    // ---- Root / Global Mix service lifecycle ----

    private fun refreshRootStatus() {
        binding.textRootStatus.text = "Checking for root…"
        Thread {
            val hasRoot = RootManager.hasRoot()
            runOnUiThread {
                binding.switchMasterEq.isEnabled = hasRoot
                if (hasRoot) {
                    binding.textRootStatus.text = "Root detected — ready to enable."
                    binding.dotRootStatus.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(getColor(R.color.accent))
                    binding.switchMasterEq.isChecked = PrefsStore.isGlobalMixEnabled(this)
                    if (PrefsStore.isGlobalMixEnabled(this)) startGlobalMix()
                } else {
                    binding.textRootStatus.text = "No root detected — this app requires root for system-wide EQ."
                    binding.dotRootStatus.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(getColor(R.color.text_faint))
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
                binding.switchMasterEq.isChecked = false
            } else {
                pushAllStateToService()
            }
        }, 300)
    }

    /** Re-applies every persisted control once the service is bound (fresh start or reconnect). */
    private fun pushAllStateToService() {
        val service = globalMixService ?: return
        val customGains = if (PrefsStore.isCustomCurve(this)) PrefsStore.loadCustomCurve(this) else null
        if (customGains != null) {
            service.applyCustomBandGainsDb(customGains)
        } else {
            service.applySignature(PrefsStore.loadSignature(this))
        }
        service.setBassBoostPercent(binding.dialBassBoost.value)
        service.setBassBoostEnabled(binding.switchBassBoost.isChecked)
        service.setLoudnessPercent(binding.dialLoudness.value)
        service.setLoudnessEnabled(binding.switchLoudness.isChecked)
        service.setVirtualizerPercent(binding.dialVirtualizer.value)
        service.setVirtualizerEnabled(binding.switchVirtualizer.isChecked)
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
                "Battery Optimization Already Disabled \u2713"
            else
                "Disable Battery Optimization for This App"
    }

    override fun onDestroy() {
        if (globalMixBound) unbindService(globalMixConnection)
        super.onDestroy()
    }
}
