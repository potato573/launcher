# Pocket Launcher

Launcher Android (aplikasi home screen) yang ringan. Kotlin, minSdk 24 (Android 7.0), targetSdk 35.

## Fitur v0.1
- **Layar home**: jam + tanggal, grid ikon pilihan Anda di atas wallpaper.
- **Dock**: maks 5 aplikasi; awalnya diisi otomatis (telepon, pesan, browser, kamera).
- **Pindah ikon**: tekan lama lalu geser untuk mengatur urutan di home atau dock.
  Tekan lama tanpa menggeser = menu (pindah ke dock/home, hapus, info, copot).
- **Laci aplikasi**: geser ke atas (atau ketuk panah). Pencarian tanpa peduli huruf besar/aksen,
  bisa pakai huruf awal (mis. "gm" = Google Maps). Tekan Enter = buka hasil pertama.
  Tekan lama aplikasi = tambah ke home/dock, info, copot.
- **Pengaturan** (tekan lama area kosong di home): jumlah kolom, nama aplikasi, dock, jam,
  keyboard otomatis di laci, jadikan launcher utama, atur ulang tata letak.
- Bahasa Inggris (default) dan Indonesia.

## Build
- Lokal: `./gradlew assembleDebug` (butuh Android SDK). APK ada di `app/build/outputs/apk/debug/`.
- GitHub Actions: setiap push ke `main` atau jalankan manual (workflow **Build APK**),
  lalu unduh artifact **PocketLauncher-debug-apk** dari halaman run.

## Pakai sebagai launcher utama
Pasang APK, tekan tombol Home, pilih **Pocket Launcher** → **Selalu**.
Atau: Pengaturan launcher → Jadikan launcher utama.
