package com.yoonki.watchrecorder

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Recordings waiting to be confirmed by the phone. Persisted in SharedPreferences so the queue
 * survives process death and reboots. An item leaves the queue only when the phone acks it.
 */
object SyncQueue {

    data class Item(val id: String, val uri: Uri, val name: String, val size: Long, val sentAt: Long)

    private const val PREFS = "sync"
    private const val KEY_ITEMS = "items"
    private const val KEY_DELETE_AFTER_SYNC = "deleteAfterSync"

    @Synchronized
    fun all(context: Context): List<Item> {
        val json = prefs(context).getString(KEY_ITEMS, "[]")!!
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Item(o.getString("id"), Uri.parse(o.getString("uri")), o.getString("name"), o.getLong("size"), o.optLong("sentAt", 0))
        }
    }

    @Synchronized
    fun add(context: Context, item: Item) = save(context, all(context).filter { it.id != item.id } + item)

    @Synchronized
    fun markSent(context: Context, id: String, time: Long) =
        save(context, all(context).map { if (it.id == id) it.copy(sentAt = time) else it })

    @Synchronized
    fun remove(context: Context, id: String): Item? {
        val items = all(context)
        save(context, items.filter { it.id != id })
        return items.find { it.id == id }
    }

    var Context.deleteAfterSync: Boolean
        get() = prefs(this).getBoolean(KEY_DELETE_AFTER_SYNC, false)
        set(value) { prefs(this).edit().putBoolean(KEY_DELETE_AFTER_SYNC, value).commit() }

    private fun save(context: Context, items: List<Item>) {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().put("id", it.id).put("uri", it.uri.toString()).put("name", it.name)
                .put("size", it.size).put("sentAt", it.sentAt))
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).commit()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
