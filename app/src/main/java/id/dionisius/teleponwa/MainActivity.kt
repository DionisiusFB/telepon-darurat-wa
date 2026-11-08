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
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Layar pengaturan: izin, daftar kontak (maksimal 3), dan tombol tambahan. */
class MainActivity : Activity() {

    private lateinit var tvWarning: TextView
    private lateinit var btnAdd: Button
    private lateinit var contactList: LinearLayout

    /** Kontak yang sedang dipilihkan fotonya dari galeri. */
    private var pendingPhotoId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pendingPhotoId = savedInstanceState?.getString(STATE_PHOTO_ID)

        tvWarning = findViewById(R.id.tvWarning)
        btnAdd = findViewById(R.id.btnAdd)
        contactList = findViewById(R.id.contactList)

        findViewById<Button>(R.id.btnPermission).setOnClickListener { askPermissions() }
        btnAdd.setOnClickListener { addContact() }
        findViewById<Button>(R.id.btnTile).setOnClickListener { addTile() }
        findViewById<Button>(R.id.btnNotifSettings).setOnClickListener { openNotifSettings() }
        findViewById<Button>(R.id.btnLockSettings).setOnClickListener { openLockSettings() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHOTO_ID, pendingPhotoId)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        Notif.showAll(this)
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
        Notif.showAll(this)
        if (missingPermissions().isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Izin belum lengkap")
                .setMessage("Kalau jendela izin tidak muncul lagi, buka pengaturan app ini lalu aktifkan izin Kontak, Telepon, dan Notifikasi.")
                .setPositiveButton("Buka pengaturan") { _, _ -> openAppSettings() }
                .setNegativeButton("Nanti", null)
                .show()
        }
    }

    // ---------- Daftar kontak ----------

    private fun addContact() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            toast("Beri izin dulu (langkah 1)")
            return
        }
        val saved = Prefs.loadAll(this)
        if (saved.size >= Prefs.MAX) {
            toast("Maksimal ${Prefs.MAX} kontak. Hapus salah satu dulu.")
            return
        }
        val savedIds = saved.map { it.id }.toSet()
        val choices = WhatsApp.listContacts(this).filter { it.id !in savedIds }
        if (choices.isEmpty()) {
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
            .setTitle("Pilih kontak")
            .setItems(choices.map { it.label }.toTypedArray()) { _, which ->
                val chosen = choices[which]
                askTitle("Nama di notifikasi", chosen.name) { title ->
                    val contact = chosen.copy(title = title)
                    Photos.saveFromContact(this, contact)
                    Prefs.saveAll(this, Prefs.loadAll(this) + contact)
                    afterChange()
                    toast("Tersimpan: $title")
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun askTitle(dialogTitle: String, initial: String, onSave: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial)
            setSelection(text.length)
            hint = "Contoh: Pak Kukuh"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val wrap = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setMessage("Nama ini yang tampil di layar kunci.")
            .setView(wrap)
            .setPositiveButton("Simpan") { _, _ ->
                val typed = input.text.toString().trim().take(30)
                onSave(typed.ifEmpty { initial })
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun rename(index: Int) {
        val list = Prefs.loadAll(this).toMutableList()
        val c = list.getOrNull(index) ?: return
        askTitle("Ubah nama", c.title) { title ->
            list[index] = c.copy(title = title)
            Prefs.saveAll(this, list)
            afterChange()
        }
    }

    private fun moveUp(index: Int) {
        val list = Prefs.loadAll(this).toMutableList()
        if (index <= 0 || index >= list.size) return
        val tmp = list[index - 1]
        list[index - 1] = list[index]
        list[index] = tmp
        Prefs.saveAll(this, list)
        afterChange()
    }

    private fun remove(index: Int) {
        val list = Prefs.loadAll(this).toMutableList()
        val c = list.getOrNull(index) ?: return
        AlertDialog.Builder(this)
            .setTitle("Hapus ${c.title}?")
            .setMessage("Notifikasi dan tombol untuk kontak ini akan hilang.")
            .setPositiveButton("Hapus") { _, _ ->
                list.removeAt(index)
                Prefs.saveAll(this, list)
                Photos.delete(this, c)
                try {
                    getSystemService(ShortcutManager::class.java)
                        ?.disableShortcuts(listOf(shortcutId(c)), "Kontak ini sudah dihapus dari Telepon WA")
                } catch (e: Exception) {
                    // Tidak ada tombol layar utama untuk kontak ini
                }
                afterChange()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun testCall(c: WaContact) {
        startActivity(Notif.callIntent(this, c))
    }

    // ---------- Foto ----------

    private fun photoMenu(c: WaContact) {
        val options = mutableListOf("Pilih dari galeri", "Pakai foto dari Kontak HP")
        if (Photos.has(this, c)) options += "Hapus foto"
        AlertDialog.Builder(this)
            .setTitle("Foto untuk ${c.title}")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Pilih dari galeri" -> pickPhoto(c)
                    "Pakai foto dari Kontak HP" -> useContactPhoto(c)
                    "Hapus foto" -> {
                        Photos.delete(this, c)
                        afterChange()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun pickPhoto(c: WaContact) {
        pendingPhotoId = c.id
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(Intent.createChooser(intent, "Pilih foto"), REQ_PHOTO)
        } catch (e: Exception) {
            toast("Tidak ada app galeri")
        }
    }

    private fun useContactPhoto(c: WaContact) {
        // contactId tidak disimpan, jadi cari lagi dari daftar kontak WhatsApp
        val fresh = WhatsApp.listContacts(this).firstOrNull { it.id == c.id }
        if (fresh != null && Photos.saveFromContact(this, fresh)) {
            afterChange()
            toast("Foto kontak dipakai")
        } else {
            toast("Kontak ini tidak punya foto di Kontak HP")
        }
    }

    @Deprecated("Activity tanpa AndroidX masih memakai API ini")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PHOTO) return
        val id = pendingPhotoId
        pendingPhotoId = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || id == null) return
        val c = Prefs.find(this, id) ?: return
        if (Photos.saveFromUri(this, c, uri)) {
            afterChange()
            toast("Foto tersimpan")
        } else {
            toast("Foto tidak bisa dibuka, coba foto lain")
        }
    }

    // ---------- Tombol layar utama dan panel cepat ----------

    private fun shortcutId(c: WaContact) = "wa:" + c.id

    private fun buildShortcut(c: WaContact): ShortcutInfo {
        val photo = Photos.load(this, c)
        val icon = if (photo != null) Icon.createWithAdaptiveBitmap(photo)
        else Icon.createWithResource(this, R.mipmap.ic_launcher)
        return ShortcutInfo.Builder(this, shortcutId(c))
            .setShortLabel(c.title.take(25))
            .setLongLabel("Telepon ${c.title}".take(50))
            .setIcon(icon)
            .setIntent(Notif.callIntent(this, c))
            .build()
    }

    private fun pinShortcut(c: WaContact) {
        val sm = getSystemService(ShortcutManager::class.java)
        if (sm == null || !sm.isRequestPinShortcutSupported) {
            toast("Layar utama HP ini tidak mendukung tombol pintasan")
            return
        }
        sm.requestPinShortcut(buildShortcut(c), null)
    }

    private fun updatePinnedShortcuts() {
        val sm = getSystemService(ShortcutManager::class.java) ?: return
        val pinned = sm.pinnedShortcuts.map { it.id }.toSet()
        val updates = Prefs.loadAll(this).filter { shortcutId(it) in pinned }.map { buildShortcut(it) }
        if (updates.isNotEmpty()) sm.updateShortcuts(updates)
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
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                toast("Tidak bisa membuka pengaturan")
            }
        }
    }

    // ---------- Tampilan ----------

    /** Setelah daftar kontak berubah: perbarui notifikasi, tombol, dan tampilan. */
    private fun afterChange() {
        Notif.showAll(this)
        updatePinnedShortcuts()
        TileService.requestListeningState(this, ComponentName(this, CallTileService::class.java))
        refresh()
    }

    private fun refresh() {
        renderContacts()

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

    private fun renderContacts() {
        contactList.removeAllViews()
        val list = Prefs.loadAll(this)

        btnAdd.text = if (list.size >= Prefs.MAX) "Sudah ${Prefs.MAX} kontak (maksimal)"
        else "2. Tambah kontak (${list.size}/${Prefs.MAX})"
        btnAdd.isEnabled = list.size < Prefs.MAX

        if (list.isEmpty()) {
            contactList.addView(TextView(this).apply {
                text = "Belum ada kontak. Ketuk Tambah kontak."
                textSize = 15f
                setPadding(0, dp(8), 0, 0)
            })
            return
        }

        list.forEachIndexed { i, c ->
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(16), 0, dp(4))
            }
            val photo = Photos.load(this, c)
            header.addView(ImageView(this).apply {
                if (photo != null) setImageBitmap(photo) else setImageResource(R.mipmap.ic_launcher)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(12) }
            })
            header.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = "${i + 1}. ${c.title}"
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = c.label
                    textSize = 14f
                })
            })
            contactList.addView(header)

            contactList.addView(buttonRow(
                "Tes" to { testCall(c) },
                "Ubah nama" to { rename(i) },
                "Foto" to { photoMenu(c) },
            ))
            contactList.addView(buttonRow(
                "Layar utama" to { pinShortcut(c) },
                "Naikkan" to { if (i > 0) moveUp(i) else toast("Sudah paling atas") },
                "Hapus" to { remove(i) },
            ))
        }
    }

    private fun buttonRow(vararg buttons: Pair<String, () -> Unit>): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            buttons.forEach { (label, action) ->
                addView(Button(this@MainActivity).apply {
                    text = label
                    isAllCaps = false
                    setOnClickListener { action() }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val REQ_PERMISSIONS = 10
        private const val REQ_PHOTO = 20
        private const val STATE_PHOTO_ID = "pending_photo_id"
    }
}
