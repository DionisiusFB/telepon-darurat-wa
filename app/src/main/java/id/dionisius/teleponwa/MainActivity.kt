package id.dionisius.teleponwa

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/** Layar pengaturan: izin, pilih kontak, tes, dan tombol tambahan. */
class MainActivity : Activity() {

    private lateinit var tvContact: TextView
    private lateinit var tvWarning: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvContact = findViewById(R.id.tvContact)
        tvWarning = findViewById(R.id.tvWarning)

        findViewById<Button>(R.id.btnPermission).setOnClickListener { askPermissions() }
        findViewById<Button>(R.id.btnPick).setOnClickListener { pickContact() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { testCall() }
        findViewById<Button>(R.id.btnShortcut).setOnClickListener { pinShortcut() }
        findViewById<Button>(R.id.btnTile).setOnClickListener { addTile() }
        findViewById<Button>(R.id.btnNotifSettings).setOnClickListener { openNotifSettings() }
        findViewById<Button>(R.id.btnLockSettings).setOnClickListener { openLockSettings() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        Notif.show(this)
    }

    // ---------- Izin ----------

    private fun missingPermissions(): List<String> {
        val needed = mutableListOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        return needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    private fun askPermissions() {
        val missing = missingPermissions()
        if (missing.isEmpty()) {
            toast("Semua izin sudah diberikan")
        } else {
            requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMISSIONS) return
        refresh()
        Notif.show(this)
        if (missingPermissions().isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Izin belum lengkap")
                .setMessage("Kalau jendela izin tidak muncul lagi, buka pengaturan app ini lalu aktifkan izin Kontak, Telepon, dan Notifikasi.")
                .setPositiveButton("Buka pengaturan") { _, _ -> openAppSettings() }
                .setNegativeButton("Nanti", null)
                .show()
        }
    }

    // ---------- Kontak ----------

    private fun pickContact() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            toast("Beri izin dulu (langkah 1)")
            return
        }
        val contacts = WhatsApp.listContacts(this)
        if (contacts.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Kontak WhatsApp tidak ditemukan")
                .setMessage(
                    "Pastikan:\n" +
                        "1. Nomor yang mau ditelepon sudah disimpan di Kontak HP ini.\n" +
                        "2. WhatsApp sudah dibuka minimal sekali setelah nomor disimpan.\n" +
                        "3. Di pengaturan Akun HP, sinkronisasi akun WhatsApp aktif.\n\n" +
                        "Lalu coba lagi.",
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Pilih kontak yang ditelepon")
            .setItems(contacts.map { it.label }.toTypedArray()) { _, which ->
                val chosen = contacts[which]
                Prefs.save(this, chosen)
                updateShortcutIfPinned(chosen)
                TileService.requestListeningState(this, ComponentName(this, CallTileService::class.java))
                Notif.show(this)
                refresh()
                toast("Tersimpan: ${chosen.name}")
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun testCall() {
        if (Prefs.load(this) == null) {
            toast("Pilih kontak dulu (langkah 2)")
            return
        }
        startActivity(Intent(this, CallActivity::class.java))
    }

    // ---------- Tombol tambahan ----------

    private fun buildShortcut(contact: WaContact): ShortcutInfo {
        val intent = Intent(this, CallActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return ShortcutInfo.Builder(this, SHORTCUT_ID)
            .setShortLabel("Telepon ${contact.name}".take(25))
            .setLongLabel("Telepon ${contact.name} lewat WA".take(50))
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
    }

    private fun pinShortcut() {
        val contact = Prefs.load(this) ?: run {
            toast("Pilih kontak dulu (langkah 2)")
            return
        }
        val sm = getSystemService(ShortcutManager::class.java)
        if (sm == null || !sm.isRequestPinShortcutSupported) {
            toast("Layar utama HP ini tidak mendukung tombol pintasan")
            return
        }
        sm.requestPinShortcut(buildShortcut(contact), null)
    }

    private fun updateShortcutIfPinned(contact: WaContact) {
        val sm = getSystemService(ShortcutManager::class.java) ?: return
        if (sm.pinnedShortcuts.any { it.id == SHORTCUT_ID }) {
            sm.updateShortcuts(listOf(buildShortcut(contact)))
        }
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            val sbm = getSystemService(StatusBarManager::class.java)
            if (sbm != null) {
                sbm.requestAddTileService(
                    ComponentName(this, CallTileService::class.java),
                    getString(R.string.tile_label),
                    Icon.createWithResource(this, R.drawable.ic_call),
                    mainExecutor,
                ) { result ->
                    when (result) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED,
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED,
                        -> toast("Tombol sudah ada di panel cepat")
                        else -> showTileManualSteps()
                    }
                }
                return
            }
        }
        showTileManualSteps()
    }

    private fun showTileManualSteps() {
        AlertDialog.Builder(this)
            .setTitle("Tambah tombol di panel cepat")
            .setMessage(
                "1. Tarik layar dari atas sampai panel cepat terbuka penuh.\n" +
                    "2. Ketuk ikon pensil, Edit, atau titik tiga lalu Edit tombol.\n" +
                    "3. Cari tombol \"Telepon WA\" lalu seret ke deretan atas.",
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------- Pengaturan HP ----------

    private fun openNotifSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        safeStart(intent)
    }

    private fun openLockSettings() {
        safeStart(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }

    private fun openAppSettings() {
        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            safeFallbackSettings()
        }
    }

    private fun safeFallbackSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e: Exception) {
            toast("Tidak bisa membuka pengaturan")
        }
    }

    // ---------- Tampilan ----------

    private fun refresh() {
        val contact = Prefs.load(this)
        tvContact.text = if (contact != null) "Kontak tujuan: ${contact.label}" else "Kontak tujuan: belum dipilih"

        val warnings = mutableListOf<String>()
        if (missingPermissions().isNotEmpty()) {
            warnings += "Izin kontak, telepon, atau notifikasi belum lengkap (langkah 1)."
        } else if (!Notif.canPost(this)) {
            warnings += "Notifikasi app ini dimatikan. Aktifkan lewat tombol pengaturan notifikasi."
        }
        if (!WhatsApp.isInstalled(this, WhatsApp.PKG_WA) && !WhatsApp.isInstalled(this, WhatsApp.PKG_W4B)) {
            warnings += "WhatsApp belum terpasang di HP ini."
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && km.isDeviceSecure) {
            warnings += "HP ini masih memakai PIN, pola, atau sandi. Tombol di layar kunci akan minta PIN dulu. " +
                "Ubah kunci layar ke Geser atau Tidak ada."
        }

        if (warnings.isEmpty()) {
            tvWarning.visibility = View.GONE
        } else {
            tvWarning.text = warnings.joinToString("\n\n") { "Perhatian: $it" }
            tvWarning.visibility = View.VISIBLE
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val REQ_PERMISSIONS = 10
        private const val SHORTCUT_ID = "telepon_wa"
    }
}
