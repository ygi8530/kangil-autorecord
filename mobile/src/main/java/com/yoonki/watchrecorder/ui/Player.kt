package com.yoonki.watchrecorder.ui

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Single shared MediaPlayer for the list; only one recording plays at a time. */
class Player(private val context: Context) {

    var uri by mutableStateOf<Uri?>(null); private set
    var playing by mutableStateOf(false); private set
    var position by mutableIntStateOf(0); private set
    var duration by mutableIntStateOf(0); private set
    var speed by mutableFloatStateOf(1f); private set

    private var mp: MediaPlayer? = null

    fun toggle(target: Uri) {
        if (uri == target && mp != null) {
            if (playing) pause() else resume()
            return
        }
        release()
        val player = MediaPlayer().apply {
            setDataSource(context, target)
            prepare()
            setOnCompletionListener {
                this@Player.playing = false
                this@Player.position = 0
                it.seekTo(0)
            }
        }
        mp = player
        uri = target
        duration = player.duration
        position = 0
        player.start()
        playing = true
        applySpeed()
    }

    fun seekTo(ms: Int) {
        val p = mp ?: return
        val clamped = ms.coerceIn(0, duration)
        p.seekTo(clamped)
        position = clamped
    }

    fun skip(deltaMs: Int) = seekTo(position + deltaMs)

    fun cycleSpeed() {
        speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        applySpeed()
    }

    /** Called from a ticker while playing. */
    fun tick() {
        mp?.let { if (playing) position = it.currentPosition }
    }

    fun stopIf(target: Uri) {
        if (uri == target) release()
    }

    fun release() {
        mp?.release()
        mp = null
        uri = null
        playing = false
        position = 0
        duration = 0
    }

    private fun pause() {
        mp?.pause(); playing = false
    }

    private fun resume() {
        mp?.start(); playing = true
        applySpeed()
    }

    /** Only while playing: setting a speed on a paused MediaPlayer would start it. Paused → applied on resume. */
    private fun applySpeed() {
        val p = mp ?: return
        if (playing) p.playbackParams = p.playbackParams.setSpeed(speed)
    }
}
