package com.yoonki.watchrecorder

import android.content.ContentValues
import android.provider.MediaStore
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.TimeUnit

/**
 * Receives recordings from the watch. Each file arrives on its own channel; it is written to
 * MediaStore (Recordings/WatchRecorder/), size-checked, and only then acked so the watch can drop it.
 */
class PhoneListenerService : WearableListenerService() {

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val info = SyncProtocol.parseFilePath(channel.path) ?: return
        val channels = Wearable.getChannelClient(this)
        try {
            if (ReceivedStore.contains(this, info.id)) {
                Log.i(TAG, "${info.name} already saved, re-acking")
            } else {
                receive(channels, channel, info)
            }
            Tasks.await(
                Wearable.getMessageClient(this).sendMessage(channel.nodeId, SyncProtocol.ACK_PATH, info.id.toByteArray()),
                TIMEOUT_S, TimeUnit.SECONDS
            )
            Log.i(TAG, "acked ${info.name}")
        } catch (e: Exception) {
            // No ack → the watch keeps the file queued and resends later.
            Log.w(TAG, "receive ${info.name} failed: $e")
        } finally {
            channels.close(channel)
        }
    }

    private fun receive(channels: ChannelClient, channel: ChannelClient.Channel, info: SyncProtocol.FileInfo) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, info.name)
            put(MediaStore.Audio.Media.MIME_TYPE, if (info.name.endsWith(".m4a")) "audio/mp4" else "audio/aac")
            put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")
        try {
            val copied = Tasks.await(channels.getInputStream(channel), TIMEOUT_S, TimeUnit.SECONDS).use { input ->
                contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) }
            }
            check(copied == info.size) { "size mismatch: got $copied, expected ${info.size}" }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
        ReceivedStore.add(this, ReceivedStore.Entry(info.id, info.name, info.size, System.currentTimeMillis()))
        Log.i(TAG, "saved ${info.name} (${info.size}B) → $RELATIVE_PATH")
    }

    companion object {
        private const val TAG = "WatchRecorder"
        private const val TIMEOUT_S = 30L
        const val RELATIVE_PATH = "Recordings/WatchRecorder/"
    }
}
