package com.example.earbudseq

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.PopupMenu
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.earbudseq.audio.BandMapper
import com.example.earbudseq.audio.SoundSignature
import com.example.earbudseq.databinding.ActivityMainBinding
import com.example.earbudseq.system.GlobalMixService
import com.example.earbudseq.system.RootManager
import com.example.earbudseq.ui.EqualizerCurveView
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var globalMixService: GlobalMixService? = null
    private var globalMixBound = false
    private var hasRoot = false

    private data class DeviceBandInfo(
        val numberOfBands: Short,
        val centerFrequenciesHz: IntArray,
        val bandLevelRangeMillibel: IntArray
    )

    private var deviceBandInfo: DeviceBandInfo? = null
    private var currentSignature: SoundSignature = SoundSignature.FLAT

    private val globalMixConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            globalMixService = (binder as GlobalMixService.LocalBinder).getService()
            globalMixBound = true
            syncUiToServiceState()
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

        currentSignature = PrefsStore.loadSignature(this)

        setupCurveView()
        setupMasterSwitch()
        setupPresetDropdown()
        setupDials()
        setupVolume()
        setupBatteryExemption()
        setupEqMenu()

        checkRoot()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, GlobalMixService::class.java)
        bindService(intent, globalMixConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (globalMixBound) {
            unbindService(globalMixConnection)
            globalMixBound = false
        }
    }

    // ── Root detection ───────────────────────────────────────────────

    private fun checkRoot() {
        binding.textRootStatus.text = "Checking for root…"
        binding.switchMasterEq.isEnabled = false
        thread {
            val rooted = RootManager.hasRoot()
            runOnUiThread { onRootResult(rooted) }
        }
    }

    private fun onRootResult(rooted: Boolean) {
        hasRoot = rooted
        if (rooted) {
            binding.textRootStatus.text = "Root available — system-wide EQ ready."
            binding.dotRootStatus.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.accent)
            binding.switchMasterEq.isEnabled = true

            if (PrefsStore.isGlobalMixEnabled(this)) {
                binding.switchMasterEq.isChecked = true
                startGlobalMix()
            }
        } else {
            binding.textRootStatus.text = "Root not found. This app requires root (Magisk or otherwise) to attach EQ to the system output mix."
            binding.dotRootStatus.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.danger)
            binding.switchMasterEq.isEnabled = false
        }
    }

    // ── Service state sync ───────────────────────────────────────────

    private fun syncUiToServiceState() {
        val service = globalMixService ?: return
        val engine = service.getEqEngine()
        if (engine != null) {
            populateBandsFromEngine(engine.numberOfBands, engine.centerFrequenciesHz, engine.bandLevelRangeMillibel)
            binding.switchMasterEq.isChecked = true
            pushAllStateToService()
        }
    }

    private fun populateBandsFromEngine(
        numBands: Short,
        centerFreqsHz: IntArray,
        rangeMillibel: IntArray
    ) {
        if (numBands <= 0) return
        deviceBandInfo = DeviceBandInfo(numBands, centerFreqsHz, rangeMillibel)

        val minDb = rangeMillibel[0] / 100f
        val maxDb = rangeMillibel[1] / 100f

        val gainsDb = if (PrefsStore.isCustomCurve(this)) {
            PrefsStore.loadCustomCurve(this) ?: signatureToGainsDb(currentSignature, centerFreqsHz, rangeMillibel)
        } else {
            signatureToGainsDb(currentSignature, centerFreqsHz, rangeMillibel)
        }

        val bands = List(numBands.toInt()) { i ->
            EqualizerCurveView.Band(
                freqLabel = formatFreq(centerFreqsHz[i]),
                gainDb = gainsDb.getOrElse(i) { 0f },
                minDb = minDb,
                maxDb = maxDb
            )
        }
        binding.eqCurveView.bands = bands
    }

    private fun signatureToGainsDb(
        signature: SoundSignature,
        centerFreqsHz: IntArray,
        rangeMillibel: IntArray
    ): FloatArray {
        val millibel = BandMapper.mapToDeviceBands(signature, centerFreqsHz, rangeMillibel)
        return FloatArray(millibel.size) { millibel[it] / 100f }
    }

    private fun formatFreq(hz: Int): String =
        if (hz >= 1000) {
            val whole = hz / 1000
            if (hz % 1000 == 0) "${whole}kHz" else String.format("%.1fkHz", hz / 1000.0)
        } else {
            "${hz}Hz"
        }

    // ── Curve view ────────────────────────────────────────────────────

    private fun setupCurveView() {
        binding.eqCurveView.onBandChanged = fun(index: Int, newGainDb: Float) {
            if (deviceBandInfo == null) return
            globalMixService?.applySingleBandGainDb(index, newGainDb)
            markCustomCurve()
        }
    }

    private fun markCustomCurve() {
        PrefsStore.saveCustomCurve(
            this,
            binding.eqCurveView.bands.map { it.gainDb }.toFloatArray()
        )
        binding.textSelectedPreset.text = "Custom"
    }

    // ── Master switch ─────────────────────────────────────────────────

    private fun setupMasterSwitch() {
        binding.switchMasterEq.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startGlobalMix()
            } else {
                globalMixService?.stop()
                PrefsStore.setGlobalMixEnabled(this, false)
            }
        }
    }

    private fun startGlobalMix() {
        val service = globalMixService
        if (service != null) {
            val ok = service.start(currentSignature)
            if (ok) {
                PrefsStore.setGlobalMixEnabled(this, true)
                val engine = service.getEqEngine()
                if (engine != null) {
                    populateBandsFromEngine(
                        engine.numberOfBands,
                        engine.centerFrequenciesHz,
                        engine.bandLevelRangeMillibel
                    )
                }
                pushAllStateToService()
            } else {
                binding.textRootStatus.text = "Failed to attach EQ to session 0. This device's audio HAL may not support global mix effects."
                binding.switchMasterEq.isChecked = false
            }
        } else {
            val intent = Intent(this, GlobalMixService::class.java)
            startService(intent)
        }
    }

    // ── Preset dropdown ───────────────────────────────────────────────

    private fun setupPresetDropdown() {
        binding.textSelectedPreset.text = currentSignature.displayName
        binding.buttonPresetDropdown.setOnClickListener { showPresetMenu() }
    }

    private fun showPresetMenu() {
        val popup = PopupMenu(this, binding.buttonPresetDropdown)
        for (sig in SoundSignature.entries) {
            popup.menu.add(0, sig.ordinal, sig.ordinal, sig.displayName)
        }
        popup.setOnMenuItemClickListener { item ->
            val sig = SoundSignature.entries[item.itemId]
            applyPreset(sig)
            true
        }
        popup.show()
    }

    private fun applyPreset(signature: SoundSignature) {
        currentSignature = signature
        PrefsStore.saveSignature(this, signature)
        binding.textSelectedPreset.text = signature.displayName

        val info = deviceBandInfo
        if (info != null) {
            val gainsDb = signatureToGainsDb(signature, info.centerFrequenciesHz, info.bandLevelRangeMillibel)
            updateCurveBands(gainsDb)
        }
        globalMixService?.applySignature(signature)
        pushAllStateToService()
    }

    private fun updateCurveBands(gainsDb: FloatArray) {
        val bands = binding.eqCurveView.bands
        if (bands.size == gainsDb.size) {
            bands.forEachIndexed { i, band -> band.gainDb = gainsDb[i] }
            binding.eqCurveView.bands = bands
        }
    }

    // ── Dials & effect switches ───────────────────────────────────────

    private fun setupDials() {
        val bassDefault = (currentSignature.bassBoost / 10).coerceIn(0, 100)
        val virtDefault = (currentSignature.virtualizer / 10).coerceIn(0, 100)

        binding.dialBassBoost.value = PrefsStore.getBassPercent(this, bassDefault)
        binding.dialLoudness.value = PrefsStore.getLoudnessPercent(this, 0)
        binding.dialVirtualizer.value = PrefsStore.getVirtualizerPercent(this, virtDefault)

        binding.switchBassBoost.isChecked = PrefsStore.getBassEnabled(this)
        binding.switchLoudness.isChecked = PrefsStore.getLoudnessEnabled(this)
        binding.switchVirtualizer.isChecked = PrefsStore.getVirtualizerEnabled(this)

        binding.dialBassBoost.onValueChanged = { percent ->
            PrefsStore.setBassPercent(this, percent)
            globalMixService?.setBassBoostPercent(percent)
        }
        binding.dialLoudness.onValueChanged = { percent ->
            PrefsStore.setLoudnessPercent(this, percent)
            globalMixService?.setLoudnessPercent(percent)
        }
        binding.dialVirtualizer.onValueChanged = { percent ->
            PrefsStore.setVirtualizerPercent(this, percent)
            globalMixService?.setVirtualizerPercent(percent)
        }

        binding.switchBassBoost.setOnCheckedChangeListener { _, checked ->
            PrefsStore.setBassEnabled(this, checked)
            globalMixService?.setBassBoostEnabled(checked)
        }
        binding.switchLoudness.setOnCheckedChangeListener { _, checked ->
            PrefsStore.setLoudnessEnabled(this, checked)
            globalMixService?.setLoudnessEnabled(checked)
        }
        binding.switchVirtualizer.setOnCheckedChangeListener { _, checked ->
            PrefsStore.setVirtualizerEnabled(this, checked)
            globalMixService?.setVirtualizerEnabled(checked)
        }
    }

    // ── Volume ────────────────────────────────────────────────────────

    private fun setupVolume() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        binding.seekBarVolume.max = maxVol

        val savedVol = PrefsStore.getVolume(this, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        binding.seekBarVolume.progress = savedVol
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVol, 0)

        binding.seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    PrefsStore.setVolume(this@MainActivity, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // ── Battery optimization exemption ────────────────────────────────

    private fun setupBatteryExemption() {
        binding.buttonBatteryExemption.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                binding.textRootStatus.text = "Could not open battery optimization settings."
            }
        }
    }

    // ── EQ overflow menu ──────────────────────────────────────────────

    private fun setupEqMenu() {
        binding.buttonEqMenu.setOnClickListener {
            val popup = PopupMenu(this, binding.buttonEqMenu)
            popup.menu.add(0, 0, 0, "Reset to preset")
            popup.setOnMenuItemClickListener {
                resetToPreset()
                true
            }
            popup.show()
        }
    }

    private fun resetToPreset() {
        val info = deviceBandInfo ?: return
        val gainsDb = signatureToGainsDb(currentSignature, info.centerFrequenciesHz, info.bandLevelRangeMillibel)
        updateCurveBands(gainsDb)
        globalMixService?.applySignature(currentSignature)
        binding.textSelectedPreset.text = currentSignature.displayName
    }

    // ── Push all state to service ─────────────────────────────────────

    private fun pushAllStateToService() {
        val service = globalMixService ?: return

        service.setBassBoostPercent(binding.dialBassBoost.value)
        service.setLoudnessPercent(binding.dialLoudness.value)
        service.setVirtualizerPercent(binding.dialVirtualizer.value)

        service.setBassBoostEnabled(binding.switchBassBoost.isChecked)
        service.setLoudnessEnabled(binding.switchLoudness.isChecked)
        service.setVirtualizerEnabled(binding.switchVirtualizer.isChecked)
    }
}
