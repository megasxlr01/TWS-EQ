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

```
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

private fun setupCurveView() {
binding.eqCurveView.onBandChanged = fun(index: Int, newGainDb: Float) {
val info = deviceBandInfo ?: return
globalMixService?.applySingleBandGainDb(index, newGainDb)
val current = binding.eqCurveView.bands.map { it.gainDb }.toFloatArray()
PrefsStore.saveCustomCurve(this, current)
binding.textSelectedPreset.text = "Custom"
}

```
binding.switchMasterEq.setOnCheckedChangeListener { _, isChecked ->
    PrefsStore.setGlobalMixEnabled(this, isChecked)
    if (isChecked) startGlobalMix() else globalMixService?.stop()
}
```

}
