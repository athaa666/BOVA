# BOVA 3.0 — All-in-one untuk mahasiswa

Android native (Kotlin + Jetpack Compose), tema putih/abu-abu (terang & gelap), data 100% offline.

## Fitur
- **Beranda**: sapaan + jam live, kelas berikutnya (countdown), kelas hari ini, ringkasan uang & waktu belajar, pengingat mendatang.
- **Catatan** ala Keep: kartu kecil saat tertutup, ketuk untuk membesar dan langsung edit (simpan otomatis), sematkan, checklist, foto, 6 warna, tag, pencarian.
  - **Pengingat**: pilih tanggal & jam, notifikasi muncul tepat waktu (alarm), tetap aktif setelah restart HP.
  - **Catatan pintar**: offline (judul, tag, deteksi tanggal/jam, nominal Rp, ringkasan, jadikan checklist) + online opsional (ringkasan AI dengan API key sendiri).
- **Jadwal kuliah**: bisa tambah/ubah/hapus, pilih hari & jam, pengingat 60/30/15/5 menit sebelum kelas.
- **Keuangan**: masuk/keluar, kategori, ringkasan per bulan, grafik kategori, anggaran bulanan, ubah/hapus transaksi.
- **Belajar**: timer fokus/istirahat (preset atau durasi sendiri), tetap jalan saat aplikasi ditutup, notifikasi saat selesai, statistik harian/mingguan.
- **Pengaturan**: nama, tema, pengingat, anggaran, API key (opsional), **ekspor/impor backup JSON**.
- **Database offline**: Room (SQLite) dengan migrasi dari versi 1 & 2 — data lama aman.

## Build APK lewat GitHub Actions
Push ke branch `main` (atau jalankan manual di tab Actions). Workflow `.github/workflows/android.yml` memakai JDK 17 + Gradle 8.9:
`gradle -p BOVA :app:assembleDebug`
APK ada di Artifacts: `BOVA-3.0-debug-APK`.

## Catatan
- Pengingat catatan memakai AlarmManager. Di Android 12+ aktifkan "Alarm tepat waktu" di Pengaturan agar tidak terlambat.
- Matikan optimasi baterai untuk BOVA jika notifikasi sering tertunda di HP tertentu.
- Menghapus aplikasi menghapus data lokal — buat backup dulu.
