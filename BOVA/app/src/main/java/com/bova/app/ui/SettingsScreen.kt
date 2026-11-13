package com.bova.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var notifGranted by remember { mutableStateOf(Notifier.canNotify(ctx)) }
    var focusText by remember { mutableStateOf(s.focusMin.toString()) }
    var breakText by remember { mutableStateOf(s.breakMin.toString()) }
    var photoBytes by remember { mutableStateOf(PhotoStore.totalBytes(ctx)) }
    var confirmWipe by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = Backup.export(env.db)
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("Tidak bisa menulis berkas")
                }.isSuccess
            }
            Toast.makeText(ctx, if (ok) "Backup tersimpan." else "Gagal menyimpan backup.", Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) pendingImport = uri
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") }
            Text("Pengaturan", style = MaterialTheme.typography.headlineMedium)
        }

        SectionLabel("Profil")
        OutlinedTextField(name, { name = it; s.updateName(it) }, label = { Text("Nama panggilan") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SectionLabel("Tampilan")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip("Ikuti sistem", s.themeMode == 0) { s.updateTheme(0) }
            PillChip("Terang", s.themeMode == 1) { s.updateTheme(1) }
            PillChip("Gelap", s.themeMode == 2) { s.updateTheme(2) }
        }
        Spacer(Modifier.height(10.dp))
        Text("Halaman awal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Beranda", "Catatan", "Jadwal", "Uang", "Belajar").forEachIndexed { i, l -> PillChip(l, s.startTab == i) { s.updateStartTab(i) } }
        }

        SectionLabel("Jam")
        Card2 {
            SettingSwitch("Format 24 jam", "Matikan untuk 12 jam (AM/PM)", s.use24h) { s.updateUse24h(it) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingSwitch("Tampilkan detik", "Jam bergerak tiap detik di semua halaman", s.showSeconds) { s.updateShowSeconds(it) }
        }

        SectionLabel("Timer belajar")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(focusText, { focusText = it; it.toIntOrNull()?.let { v -> s.updateFocusMin(v) } }, "Fokus (menit)", Modifier.weight(1f))
            NumberField(breakText, { breakText = it; it.toIntOrNull()?.let { v -> s.updateBreakMin(v) } }, "Istirahat (menit)", Modifier.weight(1f))
        }

        SectionLabel("Pengingat")
        Card2 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pengingat kelas", style = MaterialTheme.typography.titleSmall)
                    Text("Notifikasi sebelum kelas dimulai", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = s.classReminders, onCheckedChange = { v ->
                    s.updateClassReminders(v)
                    scope.launch { ClassReminderScheduler.rescheduleAll(ctx) }
                })
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
                        Text(if (exact) "Pengingat catatan & timer muncul tepat waktu" else "Tanpa izin ini, pengingat bisa terlambat beberapa menit",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!exact) {
                        OutlinedButton(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                        }) { Text("Atur") }
                    }
                }
            }
        }

        SectionLabel("Keuangan")
        NumberField(budget, { budget = it; s.updateBudget(it.toLongOrNull() ?: 0L) }, "Anggaran bulanan (Rp), kosongkan untuk mematikan", Modifier.fillMaxWidth())

        SectionLabel("Catatan pintar")
        Card2 {
            Text("Analisis offline (judul, tag, tanggal, nominal, ringkasan) selalu aktif tanpa internet.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                key, { key = it; s.updateAiKey(it) }, label = { Text("Claude API key (opsional, untuk ringkasan online)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation()
            )
            Text("Key disimpan hanya di perangkat ini dan dikirim langsung ke api.anthropic.com saat kamu menekan \"Ringkas dengan AI\".",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }

        SectionLabel("Database offline")
        Card2 {
            Text("Semua data tersimpan di perangkat (Room/SQLite). Buat backup agar aman saat ganti HP atau reinstall.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { exportLauncher.launch("bova-backup.json") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ekspor")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Impor")
                }
            }
            Text("Foto catatan hanya tersimpan sebagai tautan; di perangkat lain foto tidak ikut terpindah.", style = MaterialTheme.typography.bodySmall,
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
                    Toast.makeText(ctx, "Dibersihkan ${formatBytes(freed)}.", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Bersihkan file tak terpakai") }
        }

        SectionLabel("Zona hati-hati")
        OutlinedButton(onClick = { confirmWipe = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Hapus semua data", color = MaterialTheme.colorScheme.error)
        }

        SectionLabel("Tentang")
        Text("BOVA 3.0 • All-in-one untuk mahasiswa", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Hapus semua data?") },
            text = { Text("Catatan, transaksi, jadwal, dan riwayat belajar akan dihapus permanen. Buat backup dulu jika perlu.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            env.dao.allNotes().forEach { Alarms.cancel(ctx, it.id.toInt()) }
                            env.dao.clearNotes(); env.dao.clearTransactions(); env.dao.clearClasses(); env.dao.clearSessions()
                            PhotoStore.cleanup(ctx, emptySet())
                            ClassReminderScheduler.rescheduleAll(ctx)
                        }
                        photoBytes = 0L
                        Toast.makeText(ctx, "Semua data dihapus.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Batal") } }
        )
    }
    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Impor backup?") },
            text = { Text("Semua data saat ini (catatan, transaksi, jadwal, riwayat belajar) akan diganti dengan isi backup.") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImport!!
                    pendingImport = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val text = ctx.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: error("Berkas tidak bisa dibaca")
                                Backup.restore(env.db, text)
                            }
                        }
                        result.onSuccess { n ->
                            withContext(Dispatchers.IO) { Reschedule.all(ctx) }
                            Toast.makeText(ctx, "$n data dipulihkan.", Toast.LENGTH_LONG).show()
                        }.onFailure { Toast.makeText(ctx, it.message ?: "Gagal mengimpor.", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Ganti data") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Batal") } }
        )
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
