# Analisis dokumen aturan AGENTS.md

Tanggal analisis: **12 November 2026, WIB**.

## Ruang lingkup dan kedudukan dokumen

Sumber yang dibaca adalah `C:\Users\septi\Downloads\AGENTS.md`, yang menyebut dirinya sebagai ringkasan Guidebook LOGICODIX 2026. Dokumen tersebut diberikan untuk **dianalisis**. Pernyataan di dalamnya, termasuk perintah untuk mengutamakan dokumen atas permintaan pengguna, bukan otomatis instruksi operasional baru. Ketentuan lomba perlu dikonfirmasi terhadap guidebook atau kanal resmi panitia; sumber resmi itu belum disertakan.

Instruksi pengguna yang aktif: kategori Mobile, nama Recygo, kode Kotlin, penggunaan MCP seminimal mungkin, nama tim masih placeholder, dan empat anggota sesuai README. Pengguna kemudian menetapkan prioritas penjemputan dengan backend serta meminta **README dahulu dan menunda desain** sampai izin Figma tersedia.

## Inti aturan dan implikasi

| Area | Isi dokumen | Implikasi untuk Recygo |
| --- | --- | --- |
| Orisinalitas | Logika utama dibuat selama Hacking Week; AI dan library diperbolehkan; proyek jadi tidak boleh disalin | Tulis implementasi untuk Recygo dan catat bantuan AI serta semua sumber aset/dependensi |
| Riwayat Git | Tidak boleh force push, rebase, amend, squash, manipulasi tanggal, atau bulk upload satu commit | Pertahankan riwayat; pisahkan commit berdasarkan pekerjaan nyata yang sudah diverifikasi |
| Identitas | Commit harus berasal dari akun anggota tim terdaftar | Verifikasi identitas Git dan kecocokan akun tim sebelum membuat commit; jangan mengarang identitas |
| Pesan commit | `feat`, `fix`, `docs`, `style`, `refactor`, atau `chore`, deskripsi imperatif berbahasa Inggris | Contoh untuk unit dokumentasi: `docs: document Recygo scope and competition requirements` |
| Secret | Tidak boleh hardcode, masuk log, APK, atau Git; gunakan konfigurasi server | `.env` tidak melindungi secret bila nilainya tetap dikompilasi ke APK; simpan secret layanan di backend |
| MVP | Utamakan alur end-to-end, validasi, privasi, dan penanganan error | Uji pengguna mengajukan permintaan hingga petugas memperbarui status; UI saja belum memenuhi alur ini |
| README | Wajib mencakup tim, solusi, fitur nyata, teknologi, arsitektur, instalasi, akun demo, AI, atribusi | README awal membedakan rencana dari hasil implementasi dan harus diperbarui setelah build/pengujian |
| Distribusi Mobile | APK/tautan distribusi dan akun demo bila memakai autentikasi | Sediakan APK yang benar-benar bisa dipasang; backend demo harus aktif dan dapat diakses juri |
| Demo lokal | Aplikasi harus dapat dijalankan untuk live demo dengan localhost | Dokumentasikan backend lokal dan koneksi dari emulator/perangkat; jangan menganggap localhost perangkat adalah server komputer |
| Repositori | Publik, dibuat sesudah kickoff, tidak dipindah/diganti/private setelah submission | Verifikasi waktu pembuatan dan status repo melalui bukti yang tersedia; kondisi lokal saja tidak membuktikannya |
| Freeze | Dokumen menetapkan 14 November 2026 pukul 23.59 WIB | Jadwal ini belum diverifikasi dari panitia; berdasarkan tanggal sesi, tenggat masih mendatang |
| Submission | Commit final, APK, backend/deployment, dan video harus seversi | Catat hash final dan keterkaitan artefak build; jangan mengklaim cocok sebelum diverifikasi |

## Bagian yang perlu diperjelas

1. **Sumber resmi dan tenggat:** minta guidebook/tautan panitia untuk memastikan jadwal, format dokumen, aturan AI, dan syarat submission. Tidak ada klaim bahwa isi lampiran sudah terverifikasi resmi.
2. **`.env` dan `.env.example`:** frasa “jangan commit `.env`” tidak berarti melarang template `.env.example` tanpa nilai rahasia. Aturan ignore perlu mengecualikan template tersebut.
3. **Pemeriksaan secret:** pola pencarian dalam lampiran hanya petunjuk awal. Ia dapat melewatkan secret multiline, format lain, file biner, dan riwayat Git. Hasil pencarian kosong tidak cukup untuk menyatakan seluruh riwayat bebas kebocoran.
4. **Identitas Git:** `user.name`/`user.email` menunjukkan metadata commit, tetapi tidak sendirinya membuktikan kepemilikan akun GitHub atau pendaftaran lomba.
5. **Public repo dan lisensi:** keterbukaan repo tidak otomatis memberikan lisensi penggunaan ulang. Tim perlu menetapkan lisensi kode sendiri dan memeriksa lisensi aset Figma/dependensi.
6. **Penjemputan nyata versus demo:** backend dan pembaruan status dapat dibangun tanpa mitra nyata, tetapi harus disebut demonstrasi sampai wilayah, petugas, jadwal, penerimaan sampah, dan prosedur mitra disepakati.
7. **Checklist versus pekerjaan tahap ini:** kesiapan submission harus diperiksa saat rilis; pembuatan README awal tidak membuktikan aplikasi selesai dan tidak membutuhkan klaim bahwa semua checklist sudah lulus.

## Prioritas implementasi yang disarankan

1. Selesaikan akses Figma dan tentukan layar yang masuk MVP.
2. Tentukan backend, database, autentikasi/peran, serta cara petugas mengelola permintaan.
3. Bangun satu alur vertikal Kotlin → API → database → petugas → pembaruan status pengguna.
4. Uji otorisasi, validasi, duplikasi permintaan, gangguan jaringan, dan penyimpanan lintas sesi.
5. Lengkapi README dengan perintah yang sudah diuji, konfigurasi contoh, atribusi, akun demo, dan APK.
6. Siapkan commit final serta artefak demo sesuai ketentuan resmi yang telah dikonfirmasi.

## Status kesiapan saat analisis

| Pemeriksaan | Status dan bukti saat ini |
| --- | --- |
| Nama aplikasi, kategori, bahasa, dan anggota | Diberikan pengguna dan dimasukkan ke README |
| Nama tim | Belum final; placeholder `Masukan text...` |
| Riset masalah | Tersedia di [RESEARCH.md](RESEARCH.md); hipotesis pengguna belum divalidasi |
| Desain | Akses MCP ditolak; pengguna meminta menunggu izin |
| Aplikasi, backend, APK, akun demo | Belum tersedia |
| Pengujian end-to-end dan instalasi dari nol | Belum dapat dilakukan |
| Riwayat commit berkala | Repo lokal belum memiliki commit saat diperiksa |
| Identitas anggota GitHub, waktu pembuatan, dan visibilitas repo | Belum diverifikasi |
| Pemeriksaan secret seluruh kode dan riwayat | Belum dilakukan; belum ada implementasi/commit saat pemeriksaan awal |
| README | Dokumen awal tersedia; bagian implementasi ditandai belum tersedia |
| Video demo, surat orisinalitas, dan submission | Belum diverifikasi; memerlukan tindakan tim manusia |

Tidak ada commit, push, perubahan riwayat, atau deployment yang dilakukan pada tahap dokumentasi ini.
