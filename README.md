# Orchestrator – Aplikasi Android sederhana untuk UiPath Orchestrator

Aplikasi WebView ringan (±1–2 MB) yang membuka UiPath Orchestrator dalam layar penuh,
dengan ikon sendiri di layar utama HP.

## Fitur
- Membuka `https://cloud.uipath.com/` secara default (URL bisa diganti dari menu)
- Login tersimpan (cookie disimpan, tidak perlu login ulang setiap buka)
- Tarik ke bawah untuk refresh, tombol Back untuk kembali ke halaman sebelumnya
- Mendukung unggah file (mis. paket .nupkg) dan unduh file (log, laporan) ke folder Download
- Menu: Beranda · Muat ulang · Ubah URL Orchestrator · Buka di browser · Keluar & hapus sesi
- Mendukung Orchestrator on-premise (harus HTTPS)

## Cara membuat APK

### Opsi A – GitHub Actions (tanpa install apa pun)
1. Buat repository baru di GitHub, unggah seluruh isi folder ini (termasuk folder `.github`).
2. Buka tab **Actions** → workflow **Build APK** akan berjalan otomatis (±3–5 menit).
3. Setelah selesai (centang hijau), buka run tersebut → bagian **Artifacts** → unduh
   **Orchestrator-apk** (file zip berisi `app-debug.apk`).

### Opsi B – Android Studio
1. Buka Android Studio → **Open** → pilih folder ini.
2. Jika diminta Gradle wrapper, setujui (Android Studio akan membuatnya).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. APK ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Cara install di HP
1. Salin `app-debug.apk` ke HP (atau unduh langsung dari GitHub di HP).
2. Buka file tersebut → izinkan **Install dari sumber tidak dikenal** bila diminta.
3. Buka aplikasi **Orchestrator** → menu ⋮ → **Ubah URL Orchestrator** dan isi URL
   organisasi/tenant Anda, contoh:
   `https://cloud.uipath.com/namaorg/DefaultTenant/orchestrator_/`

## Catatan penting
- **Login dengan Google** biasanya diblokir Google di dalam WebView. Gunakan login
  email/kata sandi UiPath, Microsoft/Azure AD, atau SSO perusahaan. Jika tetap gagal,
  pakai menu **Buka di browser**.
- Butuh Android 8.0 (Oreo) ke atas.
- APK ini adalah build *debug* (ditandatangani kunci debug) – cukup untuk penggunaan
  pribadi. Untuk distribusi ke banyak pengguna/Play Store, buat build *release* yang
  ditandatangani.
- Aplikasi ini tidak resmi dari UiPath; ia hanya menampilkan situs Orchestrator.
