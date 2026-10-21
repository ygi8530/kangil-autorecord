package com.yoonki.watchrecorder

import android.net.Uri

/** Same contract as wear/.../SyncProtocol.kt — keep the two in sync. */
object SyncProtocol {
    const val PHONE_CAPABILITY = "watchrecorder_phone"
    const val FILE_PATH_PREFIX = "/watchrecorder/file/"
    const val ACK_PATH = "/watchrecorder/ack"
    const val SETTINGS_PATH = "/watchrecorder/settings"
    const val KEY_DELETE_AFTER_SYNC = "deleteAfterSync"

    data class FileInfo(val id: String, val size: Long, val name: String)

    /** "/watchrecorder/file/<id>/<size>/<urlencoded name>" */
    fun parseFilePath(path: String): FileInfo? {
        val parts = path.removePrefix(FILE_PATH_PREFIX).split('/')
        if (!path.startsWith(FILE_PATH_PREFIX) || parts.size != 3) return null
        val size = parts[1].toLongOrNull() ?: return null
        return FileInfo(parts[0], size, Uri.decode(parts[2]))
    }
}
