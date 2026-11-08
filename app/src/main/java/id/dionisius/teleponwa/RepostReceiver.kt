package id.dionisius.teleponwa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Memunculkan lagi notifikasi setelah HP restart, app di-update, atau notifikasi diusap. */
class RepostReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Notif.showAll(context)
    }
}
