package id.dionisius.teleponwa

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Menyimpan daftar kontak (maksimal [MAX]) di memori HP, berurutan dari yang paling penting. */
object Prefs {
    const val MAX = 3

    private const val FILE = "telepon_wa"
    private const val KEY_LIST = "contacts"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun loadAll(ctx: Context): List<WaContact> {
        val p = sp(ctx)
        val json = p.getString(KEY_LIST, null) ?: return migrateSingle(ctx)
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val name = o.optString("name")
                WaContact(
                    dataId = o.optLong("dataId", -1L),
                    name = name,
                    key = o.getString("key"),
                    pkg = o.optString("pkg", WhatsApp.PKG_WA),
                    title = o.optString("title").ifBlank { name },
                )
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun saveAll(ctx: Context, contacts: List<WaContact>) {
        val arr = JSONArray()
        contacts.take(MAX).forEach { c ->
            arr.put(
                JSONObject()
                    .put("dataId", c.dataId)
                    .put("name", c.name)
                    .put("key", c.key)
                    .put("pkg", c.pkg)
                    .put("title", c.title),
            )
        }
        sp(ctx).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    /**
     * Cari kontak berdasarkan ID. Tanpa ID: kontak pertama.
     * ID yang sudah dihapus menghasilkan null, supaya tombol lama tidak menelepon orang yang salah.
     */
    fun find(ctx: Context, id: String?): WaContact? {
        val all = loadAll(ctx)
        return if (id == null) all.firstOrNull() else all.firstOrNull { it.id == id }
    }

    /** Versi pertama app hanya menyimpan satu kontak. Pindahkan ke format daftar. */
    private fun migrateSingle(ctx: Context): List<WaContact> {
        val p = sp(ctx)
        val key = p.getString("key", null) ?: return emptyList()
        val single = WaContact(
            dataId = p.getLong("data_id", -1L),
            name = p.getString("name", null) ?: key.substringBefore('@'),
            key = key,
            pkg = p.getString("pkg", null) ?: WhatsApp.PKG_WA,
        )
        saveAll(ctx, listOf(single))
        p.edit().remove("data_id").remove("name").remove("key").remove("pkg").apply()
        return listOf(single)
    }
}
