package com.yoonki.watchrecorder

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.yoonki.watchrecorder.ReceivedStore.deleteAfterSync

/** Phone → watch: connection status and the "delete from watch after sync" setting. */
object WatchLink {

    /** Name of the connected watch, or null when none is connected. */
    fun connectedWatch(context: Context, onResult: (String?) -> Unit) {
        Wearable.getNodeClient(context).connectedNodes.addOnCompleteListener { task ->
            onResult(task.result.orEmpty().firstOrNull()?.displayName)
        }
    }

    /**
     * Stored as a Data Layer data item: Play Services delivers it to the watch now, or as soon as the
     * watch reconnects, so the watch always acts on the latest choice.
     */
    fun setDeleteAfterSync(context: Context, delete: Boolean) {
        context.deleteAfterSync = delete
        val request = PutDataMapRequest.create(SyncProtocol.SETTINGS_PATH).apply {
            dataMap.putBoolean(SyncProtocol.KEY_DELETE_AFTER_SYNC, delete)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }
}
