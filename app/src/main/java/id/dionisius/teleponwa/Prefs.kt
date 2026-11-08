package id.dionisius.teleponwa

import android.content.Context

/** Menyimpan kontak yang dipilih di memori HP. */
object Prefs {
    private const val FILE = "telepon_wa"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(ctx: Context, contact: WaContact) {
        sp(ctx).edit()
            .putLong("data_id", contact.dataId)
            .putString("name", contact.name)
            .putString("key", contact.key)
            .putString("pkg", contact.pkg)
            .apply()
    }

    fun load(ctx: Context): WaContact? {
        val p = sp(ctx)
        val key = p.getString("key", null) ?: return null
        return WaContact(
            dataId = p.getLong("data_id", -1L),
            name = p.getString("name", null) ?: key.substringBefore('@'),
            key = key,
            pkg = p.getString("pkg", null) ?: WhatsApp.PKG_WA,
        )
    }
}
