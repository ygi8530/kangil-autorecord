package com.yoonki.watchrecorder

import android.net.Uri

/**
 * Wearable Data Layer contract shared with the phone app (mobile/.../SyncProtocol.kt keeps the same values).
 *
 * watch → phone : channel "/watchrecorder/file/<id>/<size>/<urlencoded name>" carrying the m4a bytes
 * phone → watch : message "/watchrecorder/ack" with payload = id, sent after the phone saved and size-checked the file
 * phone → watch : data item "/watchrecorder/settings" { deleteAfterSync: Boolean }
 */
object SyncProtocol {
    const val PHONE_CAPABILITY = "watchrecorder_phone"
    const val FILE_PATH_PREFIX = "/watchrecorder/file/"
    const val ACK_PATH = "/watchrecorder/ack"
    const val SETTINGS_PATH = "/watchrecorder/settings"
    const val KEY_DELETE_AFTER_SYNC = "deleteAfterSync"

    fun filePath(id: String, size: Long, name: String) = "$FILE_PATH_PREFIX$id/$size/${Uri.encode(name)}"
}
