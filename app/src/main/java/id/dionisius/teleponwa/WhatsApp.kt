package id.dionisius.teleponwa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

/**
 * Satu kontak WhatsApp yang bisa ditelepon.
 *
 * @param dataId ID baris di tabel kontak Android (bisa berubah kalau WhatsApp sinkron ulang)
 * @param key    isi kolom DATA1, biasanya "628xxx@s.whatsapp.net"; dipakai untuk mencari ulang dataId
 * @param pkg    paket WhatsApp: com.whatsapp atau com.whatsapp.w4b (WA Business)
 */
data class WaContact(
    val dataId: Long,
    val name: String,
    val key: String,
    val pkg: String,
) {
    val digits: String get() = key.substringBefore('@').filter { it.isDigit() }

    val label: String
        get() {
            val bisnis = if (pkg == WhatsApp.PKG_W4B) " [WA Business]" else ""
            return if (digits.isNotEmpty()) "$name (+$digits)$bisnis" else "$name$bisnis"
        }
}

/**
 * WhatsApp tidak punya API resmi untuk memulai panggilan.
 * Cara yang dipakai di sini: WhatsApp menambahkan baris "panggilan suara" ke buku kontak Android
 * dengan mimetype khusus. Kalau baris itu dibuka dengan ACTION_VIEW, WhatsApp langsung menelepon.
 * Ini cara yang sama yang dipakai app Kontak bawaan saat kita mengetuk "Panggilan suara WhatsApp".
 */
object WhatsApp {
    const val PKG_WA = "com.whatsapp"
    const val PKG_W4B = "com.whatsapp.w4b"

    private fun voipMime(pkg: String): String =
        if (pkg == PKG_W4B) "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call"
        else "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"

    enum class Result { CALL, CHAT, FAILED }

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Semua kontak yang punya entri "panggilan suara WhatsApp". Butuh izin READ_CONTACTS. */
    fun listContacts(ctx: Context): List<WaContact> {
        val result = mutableListOf<WaContact>()
        for (pkg in listOf(PKG_WA, PKG_W4B)) {
            try {
                ctx.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Data._ID,
                        ContactsContract.Data.DISPLAY_NAME,
                        ContactsContract.Data.DATA1,
                    ),
                    "${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(voipMime(pkg)),
                    "${ContactsContract.Data.DISPLAY_NAME} COLLATE NOCASE ASC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val key = c.getString(2) ?: continue
                        val name = c.getString(1) ?: key.substringBefore('@')
                        result += WaContact(c.getLong(0), name, key, pkg)
                    }
                }
            } catch (e: Exception) {
                // Izin belum ada atau penyedia kontak bermasalah: lewati saja
            }
        }
        return result.distinctBy { it.pkg + "|" + it.key }
    }

    /** Cari ID baris terbaru, karena ID bisa berubah setelah WhatsApp sinkron ulang kontak. */
    private fun currentDataId(ctx: Context, saved: WaContact): Long? = try {
        ctx.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.Data.DATA1} = ?",
            arrayOf(voipMime(saved.pkg), saved.key),
            null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    } catch (e: Exception) {
        null
    }

    /**
     * Mulai panggilan suara WhatsApp.
     * Kalau gagal, buka chat dengan kontak itu sebagai cadangan (tinggal tekan ikon telepon).
     */
    fun startCall(activity: Activity, saved: WaContact): Result {
        val id = currentDataId(activity, saved) ?: saved.dataId.takeIf { it > 0 }
        if (id != null) {
            val call = Intent(Intent.ACTION_VIEW)
                .setDataAndType(
                    Uri.parse("content://com.android.contacts/data/$id"),
                    voipMime(saved.pkg),
                )
                .setPackage(saved.pkg)
            try {
                activity.startActivity(call)
                return Result.CALL
            } catch (e: Exception) {
                // Lanjut ke cadangan
            }
        }

        if (saved.digits.isNotEmpty()) {
            val chat = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${saved.digits}"))
                .setPackage(saved.pkg)
            try {
                activity.startActivity(chat)
                return Result.CHAT
            } catch (e: Exception) {
                // Tidak ada cadangan lagi
            }
        }
        return Result.FAILED
    }
}
