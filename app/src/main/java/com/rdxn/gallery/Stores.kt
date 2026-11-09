package com.rdxn.gallery

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Album buatan sendiri (virtual): hanya daftar id foto, file aslinya tidak dipindah. */
data class UserAlbum(val id: Long, val name: String, val mediaIds: Set<Long>)

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("rdxn", Context.MODE_PRIVATE)

    var autoScan: Boolean
        get() = sp.getBoolean("autoScan", true)
        set(v) { sp.edit().putBoolean("autoScan", v).apply() }

    var lockHidden: Boolean
        get() = sp.getBoolean("lockHidden", true)
        set(v) { sp.edit().putBoolean("lockHidden", v).apply() }

    var startCols: Int
        get() = sp.getInt("startCols", 3)
        set(v) { sp.edit().putInt("startCols", v).apply() }

    fun loadHidden(): Set<Long> =
        (sp.getStringSet("hidden", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()

    fun saveHidden(s: Set<Long>) {
        sp.edit().putStringSet("hidden", s.map { it.toString() }.toSet()).apply()
    }

    fun loadAlbums(): List<UserAlbum> = try {
        val arr = JSONArray(sp.getString("albums", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ids = o.getJSONArray("ids")
            UserAlbum(o.getLong("id"), o.getString("name"), (0 until ids.length()).map { ids.getLong(it) }.toSet())
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveAlbums(list: List<UserAlbum>) {
        val arr = JSONArray()
        for (a in list) {
            arr.put(JSONObject().put("id", a.id).put("name", a.name).put("ids", JSONArray(a.mediaIds.toList())))
        }
        sp.edit().putString("albums", arr.toString()).apply()
    }
}
