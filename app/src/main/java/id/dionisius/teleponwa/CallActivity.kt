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
 * lalu menyuruh WhatsApp menelepon.
 */
class CallActivity : Activity() {

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

        val contact = Prefs.load(this)
        if (contact == null) {
            Toast.makeText(this, "Kontak belum dipilih. Buka app Telepon WA dulu.", Toast.LENGTH_LONG).show()
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
        val message = when (WhatsApp.startCall(this, contact)) {
            WhatsApp.Result.CALL -> "Menelepon ${contact.name} lewat WhatsApp"
            WhatsApp.Result.CHAT -> "Panggilan langsung gagal. Chat dibuka, tekan ikon telepon."
            WhatsApp.Result.FAILED -> "WhatsApp tidak bisa dibuka. Cek WhatsApp sudah terpasang."
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
