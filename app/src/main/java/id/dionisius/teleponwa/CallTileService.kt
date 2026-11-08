package id.dionisius.teleponwa

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Tombol di panel cepat (tarik layar dari atas). Menelepon kontak pertama. */
class CallTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val contact = Prefs.loadAll(this).firstOrNull()
        tile.label = contact?.title ?: getString(R.string.tile_label)
        tile.state = if (contact != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val contact = Prefs.loadAll(this).firstOrNull() ?: return
        val intent = Notif.callIntent(this, contact)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 2, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
