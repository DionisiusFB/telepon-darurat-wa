# Telepon WA

App Android sederhana untuk menelepon satu kontak lewat **WhatsApp** dengan satu ketukan dari **layar kunci**. Dibuat untuk orang yang tidak terbiasa memakai HP, supaya tetap bisa menghubungi keluarga kalau ada apa-apa.

## Unduh APK

Unduh versi terbaru di sini:
**https://github.com/DionisiusFB/telepon-darurat-wa/releases/latest/download/TeleponWA.apk**

APK dibuat otomatis oleh GitHub Actions setiap ada perubahan di branch `main`.

## Cara kerja singkat

1. App menampilkan **notifikasi permanen** "Telepon (nama)" yang muncul di layar kunci.
2. Saat notifikasi diketuk, app membuka layar tak terlihat di atas layar kunci, membuka kunci (kalau kuncinya hanya geser), lalu menyuruh WhatsApp menelepon.
3. WhatsApp tidak punya API resmi untuk memulai panggilan. App memakai entri "panggilan suara WhatsApp" yang WhatsApp tambahkan ke buku kontak Android (mimetype `vnd.com.whatsapp.voip.call`). Ini cara tidak resmi, jadi bisa berhenti berfungsi kalau WhatsApp mengubah sistemnya.
4. Kalau panggilan langsung gagal, app membuka chat WhatsApp dengan kontak itu sebagai cadangan.

Tombol tambahan: ikon di layar utama dan tombol di panel cepat (Quick Settings).

## Syarat di HP pengguna

- Android 8.0 atau lebih baru, WhatsApp terpasang dan ada internet.
- Nomor tujuan tersimpan di Kontak HP dan terbaca oleh WhatsApp.
- Kunci layar diatur ke **Geser** atau **Tidak ada**. Dengan PIN atau pola, HP akan minta PIN dulu.
- Izin Kontak dan Notifikasi diberikan.

## Struktur kode

| File | Isi |
|---|---|
| `MainActivity.kt` | Layar pengaturan: izin, pilih kontak, tes, tombol tambahan |
| `CallActivity.kt` | Tampil di atas layar kunci, buka kunci, mulai panggilan |
| `WhatsApp.kt` | Cari kontak WA dan mulai panggilan suara |
| `Notif.kt` | Notifikasi permanen di layar kunci |
| `RepostReceiver.kt` | Munculkan lagi notifikasi setelah restart atau diusap |
| `CallTileService.kt` | Tombol di panel cepat |

## Catatan update

APK ditandatangani dengan kunci debug yang dibuat baru di setiap build. Akibatnya, untuk memasang versi baru, **hapus dulu versi lama** lalu pasang ulang dan atur lagi kontaknya. Kalau nanti sering update, kunci tetap bisa disimpan sebagai GitHub Secret (bukan di dalam repo).
