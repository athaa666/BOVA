package com.bova.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bova.app.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(env: AppEnv, onBack: () -> Unit) {
    val s = env.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    var name by remember { mutableStateOf(s.userName) }
    var budget by remember { mutableStateOf(if (s.budget > 0) s.budget.toString() else "") }
    var key by remember { mutableStateOf(s.aiKey) }
    var notifGranted by remember { mutableStateOf(Notifier.canNotify(ctx)) }
    var focusText by remember { mutableStateOf(s.focusMin.toString()) }
    var breakText by remember { mutableStateOf(s.breakMin.toString()) }
    var photoBytes by remember { mutableStateOf(PhotoStore.totalBytes(ctx)) }
    var confirmWipe by remember { mutableStateOf(false) }
    var crash by remember { mutableStateOf(CrashLogger.read(ctx)) }

    // keamanan
    var showPinSetup by remember { mutableStateOf(false) }
    var pinStep by remember { mutableIntStateOf(0) }
    var pinFirst by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    // backup
    var exportAskPassword by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf<String?>(null) }
    var importNeedPassword by remember { mutableStateOf(false) }
    var importPlain by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val plain = Backup.export(env.db)
                    val out = if (exportPassword.isNotBlank()) Crypto.encryptWithPassword(plain, exportPassword) else plain
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(out.toByteArray()) } ?: error("Tidak bisa menulis berkas")
                }.isSuccess
            }
            exportPassword = ""
            env.toast(if (ok) "Backup tersimpan." else "Gagal menyimpan backup.")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Berkas tidak bisa dibaca")
                    if (raw.size > 50_000_000) error("Berkas terlalu besar")
                    String(raw, Charsets.UTF_8)
                }
            }
            text.onSuccess {
                importText = it
                if (Crypto.isEncryptedBackup(it)) importNeedPassword = true else importPlain = it
            }.onFailure { env.toast(it.message ?: "Gagal membaca berkas.") }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") }
            Text("Pengaturan", style = MaterialTheme.typography.headlineMedium)
        }

        SectionLabel("Profil")
        OutlinedTextField(name, { name = it.take(40); s.updateName(name) }, label = { Text("Nama panggilan") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SectionLabel("Tampilan")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip("Ikuti sistem", s.themeMode == 0) { s.updateTheme(0) }
            PillChip("Terang", s.themeMode == 1) { s.updateTheme(1) }
            PillChip("Gelap", s.themeMode == 2) { s.updateTheme(2) }
        }
        Spacer(Modifier.height(10.dp))
        Text("Halaman awal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Beranda", "Catatan", "Tugas", "Jadwal", "Uang").forEachIndexed { i, l -> PillChip(l, s.startTab == i) { s.updateStartTab(i) } }
        }

        SectionLabel("Keamanan")
        Card2 {
            SettingSwitch("Kunci aplikasi", "Minta verifikasi setiap membuka BOVA", s.lockEnabled) { on ->
                if (!on) s.updateLockEnabled(false)
                else if (s.lockMethod == 1 && !s.hasPin) { pinStep = 0; pinInput = ""; pinError = null; showPinSetup = true }
                else if (s.lockMethod == 0 && !Biometrics.available(ctx)) env.toast("Atur kunci layar atau sidik jari di pengaturan HP dulu.")
                else s.updateLockEnabled(true)
            }
            if (s.lockEnabled) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("Metode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillChip("Sidik jari / PIN HP", s.lockMethod == 0) {
                        if (Biometrics.available(ctx)) s.updateLockMethod(0) else env.toast("Verifikasi perangkat belum diatur di HP ini.")
                    }
                    PillChip("PIN BOVA", s.lockMethod == 1) {
                        if (s.hasPin) s.updateLockMethod(1) else { pinStep = 0; pinInput = ""; pinError = null; showPinSetup = true }
                    }
                }
                if (s.lockMethod == 1) {
                    Spacer(Modifier.height(8.dp))
                    SettingSwitch("Izinkan biometrik", "Tombol sidik jari di layar PIN", s.biometricOnPin) { s.updateBiometricOnPin(it) }
                    TextButton(onClick = { pinStep = 0; pinInput = ""; pinError = null; showPinSetup = true }) { Text("Ganti PIN") }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("Kunci otomatis setelah ditinggalkan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Langsung", 30 to "30 detik", 60 to "1 menit", 300 to "5 menit").forEach { (v, l) -> PillChip(l, s.autoLockSec == v) { s.updateAutoLock(v) } }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingSwitch("Sembunyikan di aplikasi terbaru", "Blokir screenshot dan pratinjau layar saat multitasking", s.secureWindow) { s.updateSecureWindow(it) }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingSwitch("Sembunyikan isi notifikasi", "Di layar kunci hanya tampil \"Ada pengingat untukmu\"", s.hidePrivate) { s.updateHidePrivate(it) }
        }

        SectionLabel("Jam")
        Card2 {
            SettingSwitch("Format 24 jam", "Matikan untuk 12 jam (AM/PM)", s.use24h) { s.updateUse24h(it) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingSwitch("Tampilkan detik", "Jam bergerak tiap detik di semua halaman", s.showSeconds) { s.updateShowSeconds(it) }
        }

        SectionLabel("Pengingat")
        Card2 {
            SettingSwitch("Pengingat kelas", "Notifikasi sebelum kelas dimulai", s.classReminders) { v ->
                s.updateClassReminders(v)
                scope.launch { ClassReminderScheduler.rescheduleAll(ctx) }
            }
            if (s.classReminders) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60, 30, 15, 5).forEach { m ->
                        PillChip("$m mnt", m in s.classLeads) {
                            val cur = s.classLeads.toMutableSet()
                            if (m in cur) cur.remove(m) else cur.add(m)
                            if (cur.isEmpty()) cur.add(m)
                            s.updateClassLeads(cur)
                            scope.launch { ClassReminderScheduler.rescheduleAll(ctx) }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Izin notifikasi", style = MaterialTheme.typography.titleSmall)
                    Text(if (notifGranted) "Aktif" else "Belum diizinkan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!notifGranted) {
                    OutlinedButton(onClick = {
                        LockState.external()
                        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    }) { Text("Izinkan") }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val exact = Alarms.canExact(ctx)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Alarm tepat waktu", style = MaterialTheme.typography.titleSmall)
                        Text(if (exact) "Pengingat & timer muncul tepat waktu" else "Tanpa izin ini, pengingat bisa terlambat beberapa menit",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!exact) OutlinedButton(onClick = {
                        LockState.external()
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                    }) { Text("Atur") }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Optimasi baterai", style = MaterialTheme.typography.titleSmall)
                    Text("Jika notifikasi sering terlambat, izinkan BOVA berjalan tanpa dibatasi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = {
                    LockState.external()
                    try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) { env.toast("Pengaturan tidak tersedia di HP ini.") }
                }) { Text("Buka") }
            }
        }

        SectionLabel("Mode fokus")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(focusText, { focusText = it.take(3); it.toIntOrNull()?.let { v -> s.updateFocusMin(v) } }, "Fokus (menit)", Modifier.weight(1f))
            NumberField(breakText, { breakText = it.take(3); it.toIntOrNull()?.let { v -> s.updateBreakMin(v) } }, "Istirahat (menit)", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Card2 {
            SettingSwitch("Jangan ganggu saat fokus", if (Dnd.hasAccess(ctx)) "Izin akses sudah diberikan" else "Perlu izin akses Jangan Ganggu saat diaktifkan", s.focusDnd) { v ->
                s.updateFocusDnd(v)
                if (v && !Dnd.hasAccess(ctx)) { LockState.external(); ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            }
        }

        SectionLabel("Keuangan")
        NumberField(budget, { budget = it.take(12); s.updateBudget(it.toLongOrNull() ?: 0L) }, "Anggaran bulanan total (Rp), kosongkan untuk mematikan", Modifier.fillMaxWidth())
        Text("Batas per kategori diatur dari tab Uang.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        SectionLabel("Catatan pintar")
        Card2 {
            Text("Analisis offline (judul, tag, tanggal, nominal, ringkasan) selalu aktif tanpa internet.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                key, { key = it.take(200); s.updateAiKey(key) }, label = { Text("Claude API key (opsional, untuk ringkasan online)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation()
            )
            Text("Key dienkripsi dengan Android Keystore, disimpan hanya di perangkat ini, dan dikirim langsung ke api.anthropic.com saat kamu menekan \"Ringkas dengan AI\".",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }

        SectionLabel("Database offline")
        Card2 {
            Text("Semua data tersimpan di perangkat (Room/SQLite) dan tidak ikut backup cloud. Buat backup agar aman saat ganti HP atau reinstall.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { exportAskPassword = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ekspor")
                }
                OutlinedButton(onClick = { LockState.external(); importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Impor")
                }
            }
            Text("Ekspor bisa dienkripsi dengan kata sandi. Foto catatan tidak ikut dalam berkas backup.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }

        SectionLabel("Penyimpanan")
        Card2 {
            Text("Foto catatan otomatis diperkecil (maks 1280 px) dan dikompres. Terpakai: ${formatBytes(photoBytes)}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("Kualitas foto", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip("Hemat (60)", s.photoQuality == 60) { s.updatePhotoQuality(60) }
                PillChip("Seimbang (80)", s.photoQuality == 80) { s.updatePhotoQuality(80) }
                PillChip("Tajam (90)", s.photoQuality == 90) { s.updatePhotoQuality(90) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    val freed = withContext(Dispatchers.IO) {
                        env.dao.deleteBlankNotes()
                        val used = env.dao.allNotes().mapNotNull { it.imageUri }.toSet()
                        PhotoStore.cleanup(ctx, used)
                    }
                    photoBytes = PhotoStore.totalBytes(ctx)
                    env.toast("Dibersihkan ${formatBytes(freed)}.")
                }
            }) { Text("Bersihkan file tak terpakai") }
        }

        SectionLabel("Diagnostik")
        Card2 {
            if (crash == null) Text("Tidak ada laporan error. Semua berjalan baik.", style = MaterialTheme.typography.bodyMedium)
            else {
                Text("Laporan error terakhir", style = MaterialTheme.typography.titleSmall)
                Text(crash!!.take(500), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 8, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("BOVA crash", crash))
                        env.toast("Laporan disalin.")
                    }) { Text("Salin") }
                    TextButton(onClick = { CrashLogger.clear(ctx); crash = null }) { Text("Hapus") }
                }
            }
        }

        SectionLabel("Zona hati-hati")
        OutlinedButton(onClick = { confirmWipe = true }, modifier = Modifier.fillMaxWidth()) { Text("Hapus semua data", color = MaterialTheme.colorScheme.error) }

        SectionLabel("Tentang")
        Card2 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BovaLogo(48.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("BOVA 4.0", style = MaterialTheme.typography.titleMedium)
                    Text("All-in-one untuk mahasiswa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Dibuat oleh Atha Bima", style = MaterialTheme.typography.titleSmall)
            Text("Catatan • Tugas • Jadwal • Keuangan • Mode Fokus. Data tersimpan offline di perangkatmu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showPinSetup) {
        AlertDialog(
            onDismissRequest = { showPinSetup = false },
            title = { Text(if (pinStep == 0) "Buat PIN (4–6 angka)" else "Ulangi PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        pinInput, { pinInput = it.filter(Char::isDigit).take(6); pinError = null }, singleLine = true, label = { Text("PIN") }, isError = pinError != null,
                        supportingText = { if (pinError != null) Text(pinError!!) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Jangan pakai PIN yang mudah ditebak. Lupa PIN berarti harus menghapus data aplikasi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput.length < 4) { pinError = "Minimal 4 angka"; return@TextButton }
                    if (pinStep == 0) {
                        if (pinInput.toSet().size == 1) { pinError = "PIN terlalu mudah ditebak"; return@TextButton }
                        pinFirst = pinInput; pinInput = ""; pinStep = 1
                    } else if (pinInput != pinFirst) { pinError = "PIN tidak sama"; pinInput = ""; pinStep = 0; pinFirst = "" }
                    else {
                        s.setPin(pinInput); s.updateLockMethod(1); s.updateLockEnabled(true)
                        showPinSetup = false; pinInput = ""; pinFirst = ""; env.toast("PIN BOVA aktif.")
                    }
                }) { Text(if (pinStep == 0) "Lanjut" else "Simpan") }
            },
            dismissButton = { TextButton(onClick = { showPinSetup = false }) { Text("Batal") } }
        )
    }
    if (exportAskPassword) {
        PasswordDialog("Ekspor backup", "Isi kata sandi untuk mengenkripsi backup. Kosongkan untuk tanpa enkripsi.", { exportAskPassword = false }) { pw ->
            exportAskPassword = false; exportPassword = pw
            LockState.external(); exportLauncher.launch("bova-backup.json")
        }
    }
    if (importNeedPassword && importText != null) {
        PasswordDialog("Backup terenkripsi", "Masukkan kata sandi yang dipakai saat ekspor.", { importNeedPassword = false; importText = null }) { pw ->
            val plain = Crypto.decryptWithPassword(importText!!, pw)
            if (plain == null) env.toast("Kata sandi salah atau berkas rusak.")
            else { importNeedPassword = false; importPlain = plain }
        }
    }
    if (importPlain != null) {
        ConfirmDialog("Impor backup?", "Semua data saat ini (catatan, tugas, transaksi, jadwal, riwayat) akan diganti dengan isi backup.", "Ganti data", true,
            onDismiss = { importPlain = null; importText = null },
            onConfirm = {
                val text = importPlain!!; importPlain = null; importText = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { Backup.restore(env.db, text) } }
                    result.onSuccess { n ->
                        withContext(Dispatchers.IO) { Reschedule.all(ctx) }
                        env.toast("$n data dipulihkan.")
                    }.onFailure { env.toast(it.message ?: "Gagal mengimpor.") }
                }
            })
    }
    if (confirmWipe) {
        ConfirmDialog("Hapus semua data?", "Catatan, tugas, transaksi, jadwal, dan riwayat belajar akan dihapus permanen. Buat backup dulu jika perlu.", "Hapus", true,
            onDismiss = { confirmWipe = false },
            onConfirm = {
                confirmWipe = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        env.dao.allNotes().forEach { Alarms.cancel(ctx, it.id.toInt()) }
                        env.dao.allTasks().forEach { Alarms.cancel(ctx, Alarms.taskCode(it.id)) }
                        env.dao.clearNotes(); env.dao.clearTransactions(); env.dao.clearClasses(); env.dao.clearSessions()
                        env.dao.clearTasks(); env.dao.clearBudgets()
                        PhotoStore.cleanup(ctx, emptySet())
                        ClassReminderScheduler.rescheduleAll(ctx)
                    }
                    photoBytes = 0L
                    env.toast("Semua data dihapus.")
                }
            })
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
