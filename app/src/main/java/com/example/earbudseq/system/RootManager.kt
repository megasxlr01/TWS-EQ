package com.example.earbudseq.system

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Thin wrapper around `su` for devices with root (Magisk or otherwise). This is the
 * simpler alternative to the Shizuku path: root can grant CAPTURE_AUDIO_OUTPUT directly,
 * no wireless-debugging pairing or companion app needed.
 */
object RootManager {

    private const val TAG = "RootManager"
    private var cachedHasRoot: Boolean? = null

    /** Runs a quick `su -c id` check. Caches the result for the process lifetime. */
    fun hasRoot(): Boolean {
        cachedHasRoot?.let { return it }
        val result = runAsRoot("id")
        val granted = result?.output?.contains("uid=0") == true
        cachedHasRoot = granted
        return granted
    }

    data class RootResult(val exitCode: Int, val output: String, val error: String)

    /**
     * Runs a single shell command as root via `su -c`. Returns null if `su` itself
     * couldn't be invoked (no root binary / request denied outright).
     */
    fun runAsRoot(command: String): RootResult? {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exit = process.waitFor()
            RootResult(exit, output, error)
        } catch (e: Exception) {
            Log.w(TAG, "su invocation failed: ${e.message}")
            null
        }
    }

    /** Grants CAPTURE_AUDIO_OUTPUT directly — the root equivalent of the Shizuku flow. */
    fun grantCaptureAudioOutputPermission(context: Context): Boolean {
        val pkg = context.packageName
        val result = runAsRoot("pm grant $pkg android.permission.CAPTURE_AUDIO_OUTPUT")
        Log.i(TAG, "pm grant exit=${result?.exitCode} out=${result?.output} err=${result?.error}")
        return context.packageManager.checkPermission(
            "android.permission.CAPTURE_AUDIO_OUTPUT", pkg
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Some devices need audioserver restarted for a fresh permission grant to take
     * effect immediately, rather than waiting for next reboot. Safe no-op if it fails —
     * audioserver auto-restarts and any active playback just briefly blips.
     */
    fun restartAudioServer() {
        runAsRoot("killall audioserver")
    }
}
