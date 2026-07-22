package com.example.earbudseq.system

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Thin wrapper around `su` for devices with root (Magisk or otherwise). Used to check
 * root availability so the app can gate Global Mix EQ behind it.
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
}
