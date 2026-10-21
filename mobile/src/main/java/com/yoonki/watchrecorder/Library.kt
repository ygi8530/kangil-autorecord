package com.yoonki.watchrecorder

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/** One recording received from the watch, as stored in the phone's MediaStore plus local metadata. */
data class Recording(
    val uri: Uri,
    val fileName: String,
    val size: Long,
    val durationMs: Long,
    /** When it was recorded on the watch (from the file name), falling back to when it arrived. */
    val recordedAt: Long,
    val title: String?,
    val note: String?,
    val favorite: Boolean,
) {
    val key get() = fileName
}

/**
 * Reads recordings from Recordings/WatchRecorder/ and keeps user metadata (custom title, note, favorite)
 * in SharedPreferences keyed by file name — the audio files themselves are never rewritten.
 */
object Library {

    private const val PREFS = "library"
    private val nameTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun load(context: Context): List<Recording> {
        val meta = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.VOLUME_NAME,
        )
        val result = mutableListOf<Recording>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("${PhoneListenerService.RELATIVE_PATH}%"),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val m = meta.getString(name, null)?.let { JSONObject(it) }
                // Volume-specific uri (content://media/external_primary/...): createDeleteRequest rejects the generic "external" one.
                val volume = c.getString(5) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                result += Recording(
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.getContentUri(volume), id),
                    fileName = name,
                    size = c.getLong(2),
                    durationMs = c.getLong(3),
                    recordedAt = parseNameTime(name) ?: c.getLong(4) * 1000,
                    title = m?.optString("title")?.takeIf { it.isNotBlank() },
                    note = m?.optString("note")?.takeIf { it.isNotBlank() },
                    favorite = m?.optBoolean("favorite") ?: false,
                )
            }
        }
        return result.sortedByDescending { it.recordedAt }
    }

    fun update(context: Context, rec: Recording, title: String? = rec.title, note: String? = rec.note, favorite: Boolean = rec.favorite) {
        val json = JSONObject().put("title", title.orEmpty()).put("note", note.orEmpty()).put("favorite", favorite)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(rec.key, json.toString()).apply()
    }

    /**
     * Deletes the app's own files directly (everything it received). Files it does not own (older installs,
     * files copied in by other apps) can only be removed with the user's consent → returns ONE system
     * confirmation for all of them, or null when nothing is left to confirm.
     */
    fun delete(context: Context, recs: Collection<Recording>): IntentSender? {
        val needConsent = mutableListOf<Recording>()
        var legacyRequest: IntentSender? = null
        for (rec in recs) {
            try {
                context.contentResolver.delete(rec.uri, null, null)
                forget(context, rec)
            } catch (e: SecurityException) {
                needConsent += rec
                if (legacyRequest == null) legacyRequest = (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
            }
        }
        if (needConsent.isEmpty()) return null
        needConsent.forEach { forget(context, it) }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(context.contentResolver, needConsent.map { it.uri }).intentSender
        } else {
            legacyRequest
        }
    }

    fun forget(context: Context, rec: Recording) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(rec.key).apply()
    }

    private fun parseNameTime(name: String): Long? {
        val match = Regex("""(\d{8}_\d{6})""").find(name) ?: return null
        return runCatching { nameTime.parse(match.value)?.time }.getOrNull()
    }
}
