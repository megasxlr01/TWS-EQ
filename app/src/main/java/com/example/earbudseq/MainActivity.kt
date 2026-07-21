package com.example.earbudseq

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.earbudseq.audio.BandMapper
import com.example.earbudseq.audio.EqService
import com.example.earbudseq.audio.SoundSignature
import com.example.earbudseq.databinding.ActivityMainBinding
import com.example.earbudseq.system.GlobalMixService
import com.example.earbudseq.system.RootManager
import com.example.earbudseq.system.ShizukuManager
import com.example.earbudseq.system.SystemCaptureService
import com.google.android.material.chip.Chip
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var service: EqService? = null
    private var bound = false

    private var systemCaptureService: SystemCaptureService? = null
    private var systemCaptureBound = false

    private var globalMixService: GlobalMixService? = null
    private var globalMixBound = false

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadFile(it) }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as EqService.LocalBinder).getService()
            bound = true
            rebuildBandSlidersFromDevice()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bound = false
            service = null
        }
    }

    private val systemCaptureConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            systemCaptureService = (binder as SystemCaptureService.LocalBinder).getService()
            systemCaptureBound = true
        }
        override fun onServiceDisconnected(name: ComponentName) {
            systemCaptureBound = false
            systemCaptureService = null
        }
    }

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

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        runOnUiThread { refreshShizukuStatus() }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            bindAndStartCapture(result.resultCode, data)
        } else {
            binding.textShizukuStatus.text = "System-wide capture was declined."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = Intent(this, EqService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        buildPresetChips()
        setupPlayerControls()
        setupSystemWideEqControls()
        setupRootControls()
        setupBackgroundReliabilityControls()
        refreshShizukuStatus()
        refreshRootStatus()
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
        service?.applySignature(sig)
        systemCaptureService?.applySignature(sig)
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

    private fun setupSystemWideEqControls() {
        binding.buttonShizukuAction.setOnClickListener {
            when (ShizukuManager.currentStatus()) {
                ShizukuManager.Status.NotInstalled, ShizukuManager.Status.NotRunning -> {
                    binding.textShizukuStatus.text =
                        "Open the Shizuku app and start it via Wireless debugging, then come back here."
                }
                ShizukuManager.Status.PermissionNeeded -> {
                    ShizukuManager.requestPermission()
                }
                ShizukuManager.Status.Ready -> {
                    val granted = ShizukuManager.grantCaptureAudioOutputPermission(this)
                    refreshShizukuStatus()
                    if (granted) {
                        binding.textShizukuStatus.text = "Ready — system-wide EQ can be enabled."
                        binding.buttonToggleSystemEq.isEnabled = true
                    } else {
                        binding.textShizukuStatus.text =
                            "Permission grant failed. See README's Shizuku troubleshooting section."
                    }
                }
            }
        }

        binding.buttonToggleSystemEq.setOnClickListener {
            if (systemCaptureService?.isCapturing() == true) {
                systemCaptureService?.stopCapture()
                binding.buttonToggleSystemEq.text = "Enable"
            } else {
                val projectionManager = getSystemService(MediaProjectionManager::class.java)
                mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        }
    }

    private fun refreshShizukuStatus() {
        val status = ShizukuManager.currentStatus()
        binding.textShizukuStatus.text = when (status) {
            ShizukuManager.Status.NotInstalled -> "Shizuku not detected. Install it from the Play Store or GitHub."
            ShizukuManager.Status.NotRunning -> "Shizuku installed but not running. Start it via Wireless debugging."
            ShizukuManager.Status.PermissionNeeded -> "Shizuku running — tap Set Up to grant this app access."
            ShizukuManager.Status.Ready ->
                if (ShizukuManager.hasCaptureAudioOutputPermission(this))
                    "Ready — system-wide EQ can be enabled."
                else
                    "Shizuku connected — tap Set Up to unlock system audio capture."
        }
        binding.buttonShizukuAction.text = when (status) {
            ShizukuManager.Status.Ready ->
                if (ShizukuManager.hasCaptureAudioOutputPermission(this)) "Re-check" else "Set up"
            else -> "Set up"
        }
        val ready = status == ShizukuManager.Status.Ready && ShizukuManager.hasCaptureAudioOutputPermission(this)
        binding.buttonToggleSystemEq.isEnabled = ready
        val dotColor = when {
            ready -> com.example.earbudseq.R.color.accent
            status == ShizukuManager.Status.PermissionNeeded -> com.example.earbudseq.R.color.accent_warm
            else -> com.example.earbudseq.R.color.text_faint
        }
        binding.dotSystemEqStatus.backgroundTintList =
            android.content.res.ColorStateList.valueOf(getColor(dotColor))
    }

    private fun bindAndStartCapture(resultCode: Int, data: Intent) {
        val intent = Intent(this, SystemCaptureService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, systemCaptureConnection, Context.BIND_AUTO_CREATE)
        // Small delay isn't ideal, but bind + start need the service connected first.
        binding.root.postDelayed({
            val savedSignature = PrefsStore.loadSignature(this)
            systemCaptureService?.startCapture(resultCode, data, savedSignature)
            binding.buttonToggleSystemEq.text = "Disable"
        }, 300)
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
                    refreshShizukuStatus() // same permission unlocks the capture-based mode too
                }
            }.start()
        }

        binding.switchGlobalMix.setOnCheckedChangeListener { _, isChecked ->
            PrefsStore.setGlobalMixEnabled(this, isChecked)
            if (isChecked) {
                // Mutually exclusive with the capture-based mode to avoid double-processing.
                if (systemCaptureService?.isCapturing() == true) {
                    systemCaptureService?.stopCapture()
                    binding.buttonToggleSystemEq.text = "Enable"
                }
                startGlobalMix()
            } else {
                globalMixService?.stop()
            }
        }
    }

    private fun refreshRootStatus() {
        binding.textRootStatus.text = "Checking for root…"
        Thread {
            val hasRoot = RootManager.hasRoot()
            runOnUiThread {
                if (hasRoot) {
                    val hasPermission = ShizukuManager.hasCaptureAudioOutputPermission(this)
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
        val eq = service?.eqEngine ?: return
        binding.bandSliderContainer.removeAllViews()
        if (eq.numberOfBands <= 0) return

        val signature = service?.currentSignature ?: SoundSignature.FLAT
        val deviceGains = BandMapper.mapToDeviceBands(
            signature, eq.centerFrequenciesHz, eq.bandLevelRangeMillibel
        )
        val rangeDb = eq.bandLevelRangeMillibel.map { it / 100f }
        val currentGainsDb = FloatArray(eq.numberOfBands.toInt())

        for (band in 0 until eq.numberOfBands) {
            val row = LayoutInflater.from(this)
                .inflate(com.example.earbudseq.R.layout.item_band_slider, binding.bandSliderContainer, false)
            val freqText = row.findViewById<TextView>(com.example.earbudseq.R.id.textBandFreq)
            val gainText = row.findViewById<TextView>(com.example.earbudseq.R.id.textBandGain)
            val seekBar = row.findViewById<SeekBar>(com.example.earbudseq.R.id.seekBarBand)

            val freqHz = eq.centerFrequenciesHz[band]
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
                    service?.applyCustomBandGainsDb(currentGainsDb)
                    systemCaptureService?.applyCustomBandGainsDb(currentGainsDb)
                    globalMixService?.applyCustomBandGainsDb(currentGainsDb)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            binding.bandSliderContainer.addView(row)
        }
    }

    private fun setupPlayerControls() {
        binding.buttonPickFile.setOnClickListener {
            filePicker.launch("audio/*")
        }
        binding.buttonPlayPause.setOnClickListener {
            val playing = service?.togglePlayPause() ?: false
            binding.buttonPlayPause.text = if (playing) "Pause" else "Play"
        }
    }

    private fun loadFile(uri: Uri) {
        binding.textNowPlaying.text = "Loading…"
        service?.loadAndPlay(uri) {
            runOnUiThread {
                binding.textNowPlaying.text = uri.lastPathSegment ?: "Playing"
                binding.buttonPlayPause.isEnabled = true
                binding.buttonPlayPause.text = "Pause"
                rebuildBandSlidersFromDevice()
            }
        }
    }

    override fun onDestroy() {
        if (bound) unbindService(connection)
        if (systemCaptureBound) unbindService(systemCaptureConnection)
        if (globalMixBound) unbindService(globalMixConnection)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }
}
