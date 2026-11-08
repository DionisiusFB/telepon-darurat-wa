package id.dionisius.teleponwa

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast

/**
 * Layar tak terlihat. Dibuka dari notifikasi, panel cepat, atau ikon di layar utama.
 * Tugasnya: tampil di atas layar kunci, membuka kunci (kalau kuncinya hanya geser),
 * lalu menyuruh WhatsApp menelepon kontak yang dipilih.
 */
class CallActivity : Activity() {

    companion object {
        const val EXTRA_ID = "contact_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        val contact = Prefs.find(this, intent?.getStringExtra(EXTRA_ID))
        if (contact == null) {
            Toast.makeText(this, "Kontak ini sudah dihapus atau belum diatur. Buka app Telepon WA.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }

        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked) {
            // Kunci geser: langsung terbuka. Kunci PIN/pola: HP akan minta PIN dulu.
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    call(contact)
                }

                override fun onDismissCancelled() {
                    finish()
                }

                override fun onDismissError() {
                    call(contact)
                }
            })
        } else {
            call(contact)
        }
    }

    private fun call(contact: WaContact) {
        val result = WhatsApp.startCall(this, contact)
        val message = when (result.status) {
            WhatsApp.Status.CALL -> "Menelepon ${contact.title} lewat WhatsApp"
            WhatsApp.Status.CHAT -> "Panggilan langsung gagal, chat dibuka. Tekan ikon telepon.\n(${result.error})"
            WhatsApp.Status.FAILED -> "WhatsApp tidak bisa dibuka.\n(${result.error})"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
