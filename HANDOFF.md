# Recygo — handoff untuk pengembang berikutnya

**Implementasi dijeda atas permintaan pengguna, 13 November 2026 (WIB).** Pengguna kemudian mengizinkan commit dan push GitHub per fitur; izin itu tidak melanjutkan instalasi/pengujian HP atau pekerjaan produk. Temannya akan melanjutkan implementasi.

## Kondisi terakhir yang penting

- Source Android dan backend memakai Kotlin. Backend SQLite lokal, fokus penjemputan.
- **APK paling baru sudah berhasil dibuild dan lint lulus, tetapi BELUM dipasang ke HP.** Lokasi: `app/build/outputs/apk/debug/app-debug.apk`.
- **Versi yang masih terpasang di HP mendahului perbaikan masking password terakhir.** Kolom password yang terisi dapat tampil terbuka pada versi itu. Source/APK terbaru mengembalikan `PasswordTransformationMethod` setelah `setSingleLine()`, tetapi pemeriksaan populated-field pada HP masih harus dilakukan. Gunakan data sintetis saja. Password akun uji yang sempat terlihat sudah diganti melalui reset OTP; jangan memulihkan password lama.
- APK terbaru juga memuat penyesuaian posisi panah CTA agar berdampingan dengan label, dan pemindahan divider/statistik profil berdasarkan tinggi teks. Dua perbaikan ini belum direcapture/diverifikasi di perangkat.
- Kode lokal dipisahkan menjadi commit per fitur untuk publikasi ke `sagara-ds/Recygo`. Commit publikasi memakai tanggal sebenarnya; tidak merekonstruksi tanggal pengerjaan sebelumnya. Lihat `git log` untuk riwayat dan hash terbaru. Belum ada hash submission yang ditetapkan pengguna.

## Keputusan pengguna

- Ikuti seluruh alur Figma asli bertema terang, termasuk onboarding dan akun warga/pengepul. Jangan mengaktifkan desain gelap otomatis atau mengganti komposisi tanpa arahan.
- Login **nomor/email + password**. OTP hanya untuk daftar dan lupa password; jalur OTP LOGIN lama sudah dihapus/ditolak, termasuk challenge lama.
- OTP memakai kotak masuk demo lokal. SMTP sungguhan belum diimplementasikan.
- Prioritaskan penjemputan dengan backend. Tidak ada dispatch/mitra nyata, verifikasi identitas resmi, rute/ETA langsung, pembayaran, pencairan poin, chat atau telepon sungguhan.
- Nama tim pada README tetap `Masukan text...`; empat anggota sudah dimasukkan.
- Minimalkan MCP. Kuota Figma MCP habis; jangan mengulang panggilan yang sudah gagal.

## Yang sudah tersedia

Splash/welcome, penjelasan izin opsional, pilihan peran, pendaftaran warga dua langkah/pengepul tiga langkah, foto demo/pilihan kamera/galeri, persetujuan data demo, OTP enam digit, reset password, sesi Android Keystore, profil terenkripsi dan pengeditannya.

Beranda kedua peran, pilih sampah, formulir alamat/jadwal/berat, antrean pengepul, terima/tolak/batal dengan aturan peran, perjalanan, tiba, berat aktual, ringkasan transaksi, konfirmasi warga dan riwayat/filter. Backend mengatur status, otorisasi, transaksi atomik serta idempotensi. Poin simulasi dihitung dari pesanan selesai.

## Bukti pengujian

- **19 pengujian backend lulus**: 10 akun + 9 pickup, termasuk penolakan OTP login/challenge lama.
- HTTP smoke lulus setelah backend terbaru dibangun dan dijalankan ulang.
- Android debug/lint terakhir lulus: **0 error, 10 warning**. APK sekitar 4,4 MB; ini APK debug untuk demo USB.
- Samsung SM-M325FV, Android13, serial `SERIAL_PERANGKAT`, 1080×2400; density override338 bawaan dipertahankan.
- UI fisik lulus: onboarding dengan skip izin, pendaftaran dua peran + OTP lokal, reset/login nomor, OTP salah lalu retry benar, edit profil, foto demo dan checked-state aksesibilitas pilihan.
- Dua skenario pickup fisik selesai sampai riwayat dan relaunch. Masing-masing 2,5kg plastik + 1kg kertas =1550 poin simulasi. Akun UI warga kini memiliki total3100 poin/7kg dari dua skenario tersebut.
- Font1.3/night mode diuji: tata letak akun mengalir memperbaiki overlap; pengaturan asli HP dikembalikan ke font1.0/nightyes. Profil besar memerlukan recapture perbaikan divider terakhir.
- Pemeriksaan password tersamarkan pada field terisi belum dilakukan terhadap APK terakhir. Jangan menyebutnya lulus.

## Pekerjaan berikutnya saat pengguna melanjutkan

1. Jalankan backend lokal, pasang APK terbaru tanpa menghapus data app, pastikan USB reverse dan Recygo di depan. Jangan menangkap layar pribadi/OS lain.
2. Jalankan stage `verdict` pada `scripts/device-account-smoke.py` untuk cek password terisi tersamarkan, ganti capture kontak pengepul yang sudah dihapus, dan recapture panah/profil. Stage ini memulihkan font setelah pengujian.
3. Minta verdict pada reviewer independen atas perbaikan terakhir. Banyak temuan sebelumnya sudah resolved; status keseluruhan terakhir masih **fix**, bukan persetujuan seluruh Figma.
4. Lengkapi kecocokan aset/visual yang belum tersedia: OTP/reset sukses, detail registrasi/profil pengepul, arrival, summary/history. Metadata dan adaptasi fungsional tersedia, tetapi ekspor frame PNG/PDF + SVG/PNG asli atau kuota pulih dibutuhkan untuk klaim persis.
5. Perbarui `docs/IMPLEMENTATION_STATUS.md`, `docs/DEVICE_TESTS.md` dan README sesuai bukti terakhir. Pertahankan `DESIGN.md`/sidecar sebagai sistem terang yang sudah ada.

Contoh, dari root proyek (aturan lokal meminta prefix `rtk`):

```powershell
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-backend.ps1
rtk proxy C:\Users\septi\AppData\Local\Android\Sdk\platform-tools\adb.exe -s SERIAL_PERANGKAT reverse tcp:8080 tcp:8080
rtk proxy C:\Users\septi\AppData\Local\Android\Sdk\platform-tools\adb.exe -s SERIAL_PERANGKAT install -r app/build/outputs/apk/debug/app-debug.apk
rtk proxy python -u scripts/device-account-smoke.py --serial SERIAL_PERANGKAT verdict
```

Backend perlu terminal terpisah. Jangan menjalankan dua skrip `uiautomator dump` bersamaan. Harness menolak dump lama dan screenshot OTP terisi/password terbuka; jangan menghapus guard untuk memaksa capture.

## Data dan referensi lokal

- `.env`, `backend/data/demo-accounts.json`, `device-accounts.json`, `smoke-account.json`, database dan `outbox/` diabaikan Git. Jangan mencetak/mengunggah password, OTP, token atau foto pribadi.
- **Jangan mengganti `OTP_SIGNING_KEY` tanpa migrasi**: profil tersimpan bergantung padanya.
- Referensi Figma ada di `.figma-cache/`, capture di `.impeccable/review/` (keduanya ignored). Manifest asal aset: `docs/figma-assets.json`. `scripts/check-assets.py` lulus.
- File `device-collector-registration-2.png` yang tidak aman sudah dihapus; pengganti harus dibuat dengan build yang masking-nya sudah diperbaiki.
- Detail aturan lomba dan riset: `docs/RULES_ANALYSIS.md`, `docs/RESEARCH.md`. Freeze14 November2026 23.59WIB berasal dari lampiran dan belum dikonfirmasi lewat panduan resmi.
