package com.example.earbudseq

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.widget.PopupMenu
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.example.earbudseq.databinding.ActivityMainBinding
import com.example.earbudseq.system.GlobalMixService

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

        setupCurveView()
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

    private fun setupCurveView() {
        binding.eqCurveView.onBandChanged = fun(index: Int, newGainDb: Float) {
            val info = deviceBandInfo ?: return
            globalMixService?.applySingleBandGainDb(index, newGainDb)
        }

        binding.switchMasterEq.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startGlobalMix()
            } else {
                globalMixService?.stop()
            }
        }
    }

    private fun startGlobalMix() {
        val intent = Intent(this, GlobalMixService::class.java)
        startService(intent)
    }

    private fun pushAllStateToService() {
        // Placeholder — safe empty implementation
    }
}
