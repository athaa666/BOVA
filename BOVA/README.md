# BOVA — Catatan & Keuangan Offline

BOVA adalah project Android native (Kotlin + Jetpack Compose) untuk:
- Catatan pribadi: tambah, lihat, dan hapus.
- Pencatatan uang masuk/keluar.
- Saldo dan total pemasukan/pengeluaran.
- Penyimpanan lokal memakai Room (tanpa akun dan tanpa internet).
- Pengingat harian lokal melalui WorkManager.

## Cara membuka
1. Ekstrak `BOVA-Android-Project.zip`.
2. Buka Android Studio, pilih **Open**, lalu pilih folder `BOVA`.
3. Tunggu Gradle Sync selesai. Gunakan JDK 17.
4. Hubungkan HP Android dengan USB debugging atau jalankan emulator.
5. Tekan **Run**.

## Notifikasi
Aplikasi meminta izin notifikasi saat pertama dibuka (Android 13+). Pengingat harian dijadwalkan sekitar pukul 20.00. WorkManager dapat menjalankannya dalam jendela waktu yang fleksibel, jadi bukan alarm tepat-menit.

## Offline & data
Semua catatan dan transaksi disimpan di database Room lokal pada perangkat. Aplikasi tidak membutuhkan koneksi internet dan tidak mengirim data ke server. Menghapus aplikasi dapat menghapus data lokal; buat cadangan sendiri jika data penting.

## Kebutuhan
- Android Studio versi baru
- Android SDK 35
- JDK 17

Project ini menyediakan source code yang bisa dibuka dan dikembangkan di Android Studio. APK belum ditandatangani/dibangun dari sini; buat APK melalui **Build > Build APK(s)** di Android Studio.
