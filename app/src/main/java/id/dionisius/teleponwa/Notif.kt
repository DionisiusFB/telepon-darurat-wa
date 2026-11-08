package id.dionisius.teleponwa

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build

/** Satu notifikasi permanen per kontak, tampil di layar kunci. */
object Notif {
    private const val CHANNEL_ID = "tombol_telepon"
    private const val NOTIF_BASE = 1

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        return nm.areNotificationsEnabled()
    }

    /** Intent untuk menelepon kontak tertentu. Dipakai notifikasi, tombol layar utama, dan panel cepat. */
    fun callIntent(ctx: Context, contact: WaContact): Intent =
        Intent(ctx, CallActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(CallActivity.EXTRA_ID, contact.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun showAll(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val contacts = Prefs.loadAll(ctx)

        // Hapus notifikasi kontak yang sudah tidak ada.
        for (i in contacts.size until Prefs.MAX) nm.cancel(NOTIF_BASE + i)
        if (contacts.isEmpty() || !canPost(ctx)) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tombol telepon",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Tombol telepon WhatsApp di layar kunci"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)

        // Kontak pertama diberi waktu paling baru supaya tampil paling atas.
        val now = System.currentTimeMillis()
        for (i in contacts.indices.reversed()) {
            post(ctx, nm, contacts[i], i, now - i * 60_000L)
        }
    }

    private fun post(ctx: Context, nm: NotificationManager, contact: WaContact, index: Int, time: Long) {
        val callPi = PendingIntent.getActivity(
            ctx, 100 + index, callIntent(ctx, contact),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Kalau notifikasi diusap atau "Hapus semua", munculkan lagi.
        val repostPi = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, RepostReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val action = Notification.Action.Builder(
            Icon.createWithResource(ctx, R.drawable.ic_call),
            "TELEPON ${contact.title.uppercase()}",
            callPi,
        ).build()

        val builder = Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setColor(ctx.getColor(R.color.wa_green))
            .setContentTitle(contact.title)
            .setContentText("Ketuk di sini untuk menelepon lewat WhatsApp")
            .setContentIntent(callPi)
            .addAction(action)
            .setDeleteIntent(repostPi)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(time)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        // Foto kontak tampil di samping tulisan notifikasi
        Photos.load(ctx, contact)?.let { builder.setLargeIcon(Icon.createWithBitmap(it)) }

        val notification = builder.build()
        nm.notify(NOTIF_BASE + index, notification)
    }
}
