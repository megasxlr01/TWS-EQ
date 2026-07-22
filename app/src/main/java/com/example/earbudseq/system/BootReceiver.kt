package com.example.earbudseq.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.earbudseq.PrefsStore

class BootReceiver : BroadcastReceiver() {

    private val tag = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        if (!PrefsStore.isGlobalMixEnabled(context)) return
        if (!RootManager.hasRoot()) {
            Log.w(tag, "Global Mix was enabled but root is unavailable after reboot.")
            return
        }

        Log.i(tag, "Restarting Global Mix EQ after boot.")
        GlobalMixService.pendingBootSignature = PrefsStore.loadSignature(context)
        val serviceIntent = Intent(context, GlobalMixService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
