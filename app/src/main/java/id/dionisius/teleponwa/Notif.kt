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

/** Notifikasi permanen berisi tombol telepon, tampil di layar kunci. */
object Notif {
    private const val CHANNEL_ID = "tombol_telepon"
    private const val NOTIF_ID = 1

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        return nm.areNotificationsEnabled()
    }

    fun show(ctx: Context) {
        val contact = Prefs.load(ctx) ?: return
        if (!canPost(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return

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

        val callIntent = Intent(ctx, CallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val callPi = PendingIntent.getActivity(
            ctx, 0, callIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Kalau notifikasi diusap atau "Hapus semua", munculkan lagi.
        val repostPi = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, RepostReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val action = Notification.Action.Builder(
            Icon.createWithResource(ctx, R.drawable.ic_call),
            "TELEPON ${contact.name.uppercase()}",
            callPi,
        ).build()

        val notification = Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setColor(ctx.getColor(R.color.wa_green))
            .setContentTitle("Telepon ${contact.name}")
            .setContentText("Ketuk di sini untuk menelepon lewat WhatsApp")
            .setContentIntent(callPi)
            .addAction(action)
            .setDeleteIntent(repostPi)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        nm.notify(NOTIF_ID, notification)
    }
}
