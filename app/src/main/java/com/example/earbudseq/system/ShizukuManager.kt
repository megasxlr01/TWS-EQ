package com.example.earbudseq.system

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku gives this app shell-level (adb) privilege without needing full root,
 * set up on-device via Developer Options > Wireless debugging > pair, then the
 * Shizuku app's "start via wireless debugging" action.
 *
 * We use that shell privilege for exactly one thing: running
 *   pm grant <package> android.permission.CAPTURE_AUDIO_OUTPUT
 * which shell is specially allowed to do even though it's a signature-level
 * permission. Once granted, system-wide audio capture properly replaces
 * (rather than duplicates) the original output — no echo.
 */
object ShizukuManager {

    private const val TAG = "ShizukuManager"
    const val REQUEST_CODE = 9001

    sealed class Status {
        object NotInstalled : Status()
        object NotRunning : Status()
        object PermissionNeeded : Status()
        object Ready : Status()
    }

    fun currentStatus(): Status {
        return try {
            if (!Shizuku.pingBinder()) {
                Status.NotRunning
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Status.PermissionNeeded
            } else {
                Status.Ready
            }
        } catch (e: Throwable) {
            // Shizuku binder not present at all (service never started on this device)
            Status.NotInstalled
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.isPreV11()) {
                // Legacy versions use a different permission call; skipped here for brevity.
                Log.w(TAG, "Shizuku pre-v11 detected; please update the Shizuku app.")
                return
            }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Throwable) {
            Log.e(TAG, "requestPermission failed: ${e.message}")
        }
    }

    /**
     * Runs `pm grant <package> android.permission.CAPTURE_AUDIO_OUTPUT` through Shizuku's
     * shell-privileged process runner. Returns true if the command reported success.
     *
     * NOTE: Shizuku's process-spawning API has shifted across library versions (older
     * versions expose Shizuku.newProcess directly; newer ones route through a bound
     * UserService AIDL interface). This uses the newProcess path present in the
     * dev.rikka.shizuku:api 13.x line — if Gradle resolves a version where that method
     * isn't available, swap this for a UserService implementation per Shizuku's docs:
     * https://github.com/RikkaApps/Shizuku-API
     */
    fun grantCaptureAudioOutputPermission(context: Context): Boolean {
        if (currentStatus() != Status.Ready) {
            Log.w(TAG, "Shizuku not ready; cannot grant permission.")
            return false
        }
        val pkg = context.packageName
        return try {
            val method = Shizuku::class.java.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            val cmd = arrayOf("sh", "-c", "pm grant $pkg android.permission.CAPTURE_AUDIO_OUTPUT")
            val process = method.invoke(null, cmd, null, null)

            val getInputStream = process.javaClass.getMethod("getInputStream")
            val getErrorStream = process.javaClass.getMethod("getErrorStream")
            val waitFor = process.javaClass.getMethod("waitFor")

            val exitCode = waitFor.invoke(process) as Int
            val output = BufferedReader(InputStreamReader(getInputStream.invoke(process) as java.io.InputStream))
                .readText()
            val errorOutput = BufferedReader(InputStreamReader(getErrorStream.invoke(process) as java.io.InputStream))
                .readText()

            Log.i(TAG, "pm grant exit=$exitCode out=$output err=$errorOutput")

            // Re-check via PackageManager afterward — this is the real source of truth.
            context.packageManager.checkPermission(
                "android.permission.CAPTURE_AUDIO_OUTPUT", pkg
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            Log.e(TAG, "grantCaptureAudioOutputPermission failed: ${e.message}", e)
            false
        }
    }

    fun hasCaptureAudioOutputPermission(context: Context): Boolean {
        return context.packageManager.checkPermission(
            "android.permission.CAPTURE_AUDIO_OUTPUT", context.packageName
        ) == PackageManager.PERMISSION_GRANTED
    }
}
