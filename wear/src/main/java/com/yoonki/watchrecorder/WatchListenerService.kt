package com.yoonki.watchrecorder

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.yoonki.watchrecorder.SyncQueue.deleteAfterSync

/**
 * Data Layer events, delivered even when the app is not running:
 * - phone became reachable again → flush the queue
 * - phone acked a file → drop it from the queue (and delete the watch copy if the phone setting says so)
 * - phone changed settings → remember them locally
 */
class WatchListenerService : WearableListenerService() {

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name == SyncProtocol.PHONE_CAPABILITY && info.nodes.isNotEmpty()) {
            Log.i(TAG, "phone reachable again (${info.nodes.size} node), syncing")
            SyncJobService.scheduleNow(this)
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != SyncProtocol.ACK_PATH) return
        val id = String(event.data, Charsets.UTF_8)
        val item = SyncQueue.remove(this, id)
        if (item == null) {
            Log.i(TAG, "ack for unknown id=$id (already handled)")
            return
        }
        if (deleteAfterSync) {
            val rows = try {
                contentResolver.delete(item.uri, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "delete after sync failed for ${item.name}: $e")
                0
            }
            Log.i(TAG, "ack ${item.name}: saved on phone, deleted from watch (rows=$rows)")
        } else {
            Log.i(TAG, "ack ${item.name}: saved on phone, kept on watch")
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.dataItem.uri.path == SyncProtocol.SETTINGS_PATH }.forEach {
            applySettings(this, DataMapItem.fromDataItem(it.dataItem).dataMap)
        }
    }

    companion object {
        private const val TAG = "WatchRecorder"

        fun applySettings(context: Context, map: DataMap) {
            val delete = map.getBoolean(SyncProtocol.KEY_DELETE_AFTER_SYNC, false)
            if (context.deleteAfterSync != delete) {
                context.deleteAfterSync = delete
                Log.i(TAG, "settings: deleteAfterSync=$delete")
            }
        }
    }
}
