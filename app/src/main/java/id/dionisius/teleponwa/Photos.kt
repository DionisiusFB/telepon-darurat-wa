package id.dionisius.teleponwa

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Foto per kontak, disimpan sebagai salinan kecil (256x256) di memori app.
 * Disalin supaya tetap ada walaupun foto aslinya dihapus dari galeri atau kontak.
 */
object Photos {
    private const val SIZE = 256

    private fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }

    private fun file(ctx: Context, c: WaContact) =
        File(dir(ctx), "p_" + Integer.toHexString(c.id.hashCode()) + ".png")

    fun has(ctx: Context, c: WaContact): Boolean = file(ctx, c).exists()

    fun load(ctx: Context, c: WaContact): Bitmap? {
        val f = file(ctx, c)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    fun delete(ctx: Context, c: WaContact) {
        file(ctx, c).delete()
    }

    /** Salin foto dari buku kontak HP. Mengembalikan false kalau kontak tidak punya foto. */
    fun saveFromContact(ctx: Context, c: WaContact): Boolean {
        if (c.contactId <= 0) return false
        return try {
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.contactId)
            val bmp = ContactsContract.Contacts.openContactPhotoInputStream(ctx.contentResolver, uri, true)
                ?.use { BitmapFactory.decodeStream(it) }
                ?: return false
            save(ctx, c, bmp)
        } catch (e: Exception) {
            false
        }
    }

    /** Salin foto dari galeri (hasil pilihan pengguna). */
    fun saveFromUri(ctx: Context, c: WaContact, uri: Uri): Boolean = try {
        val bmp = if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder otomatis memutar foto sesuai orientasi kamera
            val source = ImageDecoder.createSource(ctx.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val shortSide = min(info.size.width, info.size.height)
                if (shortSide > SIZE * 2) {
                    val scale = (SIZE * 2).toFloat() / shortSide
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            decodeSampled(ctx, uri)
        }
        bmp != null && save(ctx, c, bmp)
    } catch (e: Exception) {
        false
    }

    private fun decodeSampled(ctx: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun save(ctx: Context, c: WaContact, src: Bitmap): Boolean {
        val side = min(src.width, src.height)
        if (side <= 0) return false
        val cropped = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true)
        FileOutputStream(file(ctx, c)).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return true
    }
}
