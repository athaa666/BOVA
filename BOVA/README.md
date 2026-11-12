# BOVA 2.0 — Personal Life Manager

Aplikasi Android native Kotlin + Jetpack Compose untuk:
- Catatan offline dengan judul, isi, foto dari galeri, dan pin.
- Jadwal kuliah TPB 61 (Senin–Jumat).
- Countdown kelas berikutnya.
- Notifikasi lokal 30 dan 15 menit sebelum kelas.
- Pencatatan pemasukan/pengeluaran dan ringkasan saldo.
- Database lokal Room; tidak membutuhkan akun atau koneksi internet untuk fitur inti.

## Build APK lewat GitHub Actions
Repository memiliki workflow `.github/workflows/android.yml` (jika belum ada, salin workflow dari project versi sebelumnya). Build menggunakan JDK 17 dan Gradle 8.9:
`gradle -p BOVA :app:assembleDebug`

Output:
`BOVA/app/build/outputs/apk/debug/app-debug.apk`

## Catatan penting
- Notifikasi kelas dijadwalkan lokal memakai WorkManager. Android dapat menunda pekerjaan karena Doze/optimasi baterai; notifikasi bukan alarm presisi-menit.
- Izinkan notifikasi saat diminta Android 13+.
- Untuk jadwal minggu berikutnya, worker menjadwalkan pengingat berulang setiap minggu.
- Foto catatan menggunakan URI galeri yang disimpan secara persisten.
- Data disimpan di perangkat. Menghapus aplikasi dapat menghapus data lokal.
- Versi 2.0 menaikkan skema Room dengan migrasi agar data versi 1 tidak sengaja dihapus.
