# BOVA 4.0 — All-in-one untuk mahasiswa
Dibuat oleh **Atha Bima**.

Android native (Kotlin + Jetpack Compose), tema putih/abu-abu (terang & gelap), data 100% offline.

## Fitur
- **Beranda**: sapaan, jam live, kelas berikutnya (countdown), tugas terdekat, ringkasan uang & fokus.
- **Catatan**: grid ala Keep, ketuk untuk **layar penuh** (edit langsung, simpan otomatis), foto layar penuh dengan zoom, checklist, warna, tag, pengingat, catatan pintar offline + AI online opsional.
- **Tugas**: deadline, prioritas, mata kuliah, pengingat (1 jam / 3 jam / H-1 / H-2), bagian Terlambat / Hari ini / Mendatang.
- **Jadwal**: tambah/ubah/hapus kelas (hari, jam, ruangan, kode, SKS) dengan deteksi bentrok + **kalender bulanan** (kelas, deadline, pengingat).
- **Uang**: pemasukan/pengeluaran, anggaran total dan **anggaran per kategori** dengan peringatan 80% / terlampaui.
- **Mode Fokus**: timer fokus/istirahat, opsi Jangan Ganggu, tautan ke tugas, statistik mingguan.
- **Keamanan**: kunci aplikasi (sidik jari/PIN HP atau PIN BOVA dengan batas percobaan), kunci otomatis, blokir screenshot/preview, notifikasi privat, API key terenkripsi Keystore, backup terenkripsi kata sandi, tanpa backup cloud.
- **Handler**: pasang ulang alarm setelah reboot/ganti waktu/zona, undo hapus, laporan crash lokal, izin notifikasi/alarm/baterai.

## Build APK
Push ke `main`; GitHub Actions menjalankan `gradle -p BOVA :app:assembleRelease`. APK ada di Artifacts `BOVA-4.0-APK`.
