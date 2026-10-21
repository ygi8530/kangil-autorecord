package com.yoonki.watchrecorder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Ids already saved on the phone (so a resend is acked without saving twice) and a short history for the UI. */
object ReceivedStore {

    data class Entry(val id: String, val name: String, val size: Long, val time: Long)

    private const val PREFS = "received"
    private const val KEY_HISTORY = "history"
    private const val KEY_DELETE_AFTER_SYNC = "deleteAfterSync"
    private const val MAX_HISTORY = 200

    @Synchronized
    fun history(context: Context): List<Entry> {
        val array = JSONArray(prefs(context).getString(KEY_HISTORY, "[]"))
        return (0 until array.length()).map {
            val o = array.getJSONObject(it)
            Entry(o.getString("id"), o.getString("name"), o.getLong("size"), o.getLong("time"))
        }
    }

    fun contains(context: Context, id: String) = history(context).any { it.id == id }

    @Synchronized
    fun add(context: Context, entry: Entry) {
        val entries = (listOf(entry) + history(context).filter { it.id != entry.id }).take(MAX_HISTORY)
        val array = JSONArray()
        entries.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("size", it.size).put("time", it.time))
        }
        prefs(context).edit().putString(KEY_HISTORY, array.toString()).commit()
    }

    var Context.deleteAfterSync: Boolean
        get() = prefs(this).getBoolean(KEY_DELETE_AFTER_SYNC, false)
        set(value) { prefs(this).edit().putBoolean(KEY_DELETE_AFTER_SYNC, value).apply() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
