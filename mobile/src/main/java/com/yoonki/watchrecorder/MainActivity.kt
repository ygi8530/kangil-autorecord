package com.yoonki.watchrecorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.yoonki.watchrecorder.ui.AppTheme
import com.yoonki.watchrecorder.ui.LibraryScreen
import com.yoonki.watchrecorder.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so the list reloads when the user comes back to the app. */
    private var resumeCount by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeCount++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Received files are owned by this app and need no permission. Read access only matters for
        // recordings saved by a previous install (after reinstalling, the app no longer owns them).
        val read = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(read) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(read), 1)

        setContent {
            AppTheme {
                var settings by rememberSaveable { mutableStateOf(false) }
                AnimatedContent(settings, label = "screen") { showSettings ->
                    if (showSettings) SettingsScreen(onBack = { settings = false })
                    else LibraryScreen(resumeCount = resumeCount, onOpenSettings = { settings = true })
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) recreate() // reload with older recordings visible
    }
}
