package com.yoonki.watchrecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast

/**
 * Double-press target. Has no UI: it toggles recording and finishes immediately.
 * A microphone FGS may only be started while the app is in the foreground, so this
 * activity is the minimum screen transition the OS allows.
 */
class ToggleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLaunch("onCreate", intent)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        logLaunch("onNewIntent", intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val missing = REQUIRED_PERMISSIONS.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
            return
        }
        val action = if (RecorderService.isRunning) RecorderService.ACTION_STOP else RecorderService.ACTION_START
        val serviceIntent = Intent(this, RecorderService::class.java)
            .setAction(action)
            .putExtra(RecorderService.EXTRA_RELATIVE_PATH, intent.getStringExtra(RecorderService.EXTRA_RELATIVE_PATH))
        try {
            if (action == RecorderService.ACTION_START) startForegroundService(serviceIntent) else startService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "service start failed", e)
            Toast.makeText(this, "녹음 실행 실패: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
        close()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "마이크 권한이 필요합니다", Toast.LENGTH_LONG).show()
        }
        close()
    }

    private fun close() {
        Log.i(TAG, "finish t=${SystemClock.elapsedRealtime()}")
        finish()
        overridePendingTransition(0, 0)
    }

    private fun logLaunch(where: String, intent: Intent) {
        val screenOn = getSystemService(PowerManager::class.java).isInteractive
        Log.i(TAG, "$where t=${SystemClock.elapsedRealtime()} screenOn=$screenOn flags=0x${Integer.toHexString(intent.flags)} running=${RecorderService.isRunning}")
    }

    companion object {
        private const val TAG = "WatchRecorder"
        private const val REQUEST_PERMISSIONS = 1
        private val REQUIRED_PERMISSIONS = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    }
}
