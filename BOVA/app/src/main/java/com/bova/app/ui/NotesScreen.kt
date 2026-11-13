package com.bova.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bova.app.*
import com.bova.app.data.Note
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NotesScreen(
    env: AppEnv,
    notes: List<Note>,
    dark: Boolean,
    pendingNew: Boolean,
    onConsumed: () -> Unit,
    onLogExpense: (String, Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    fun create() {
        scope.launch {
            query = ""; filter = 0
            val id = env.dao.upsertNote(Note(title = "", body = ""))
            expandedId = id
        }
    }
    LaunchedEffect(pendingNew) { if (pendingNew) { create(); onConsumed() } }

    fun collapse(n: Note?) {
        expandedId = null
        if (n != null && n.title.isBlank() && n.body.isBlank() && n.imageUri == null) {
            scope.launch { env.dao.deleteNote(n.id); Alarms.cancel(env.ctx, n.id.toInt()) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle("Catatan", "${notes.size} catatan tersimpan di perangkat")
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari judul, isi, atau tag") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Hapus") } },
                singleLine = true, shape = MaterialTheme.shapes.large
            )
            Row(Modifier.padding(vertical = 10.dp).horizontalScroll(rememberScrollState())) {
                listOf("Semua", "Disematkan", "Checklist", "Pengingat").forEachIndexed { i, label ->
                    PillChip(label, filter == i) { filter = i }
                    Spacer(Modifier.width(8.dp))
                }
            }
            val shown = notes.filter { n ->
                (query.isBlank() || (n.title + " " + n.body + " " + n.tags).contains(query, true)) &&
                    when (filter) { 1 -> n.isPinned; 2 -> n.isChecklist; 3 -> n.reminderAt != null; else -> true }
            }
            if (shown.isEmpty()) {
                EmptyState(Icons.Default.StickyNote2, if (notes.isEmpty()) "Belum ada catatan" else "Tidak ada hasil",
                    if (notes.isEmpty()) "Ketuk tombol + untuk menulis catatan pertama." else "Coba kata kunci atau filter lain.")
            }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                verticalItemSpacing = 10.dp,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    shown, key = { it.id },
                    span = { n -> if (n.id == expandedId) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane }
                ) { n ->
                    if (n.id == expandedId) {
                        NoteEditorCard(env, n, dark, onCollapse = { collapse(n) }, onLogExpense = onLogExpense,
                            modifier = Modifier.animateItem())
                    } else {
                        NoteCard(n, dark, onClick = { expandedId = n.id }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { create() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
        ) { Icon(Icons.Default.Add, "Catatan baru") }
    }
}

@Composable
private fun NoteCard(n: Note, dark: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = noteColor(n.color, dark), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            if (n.imageUri != null) {
                NotePhoto(n.imageUri, Modifier.fillMaxWidth().height(88.dp).clip(MaterialTheme.shapes.small))
                Spacer(Modifier.height(8.dp))
            }
            if (n.title.isNotBlank()) {
                Text(n.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (n.body.isNotBlank()) {
                if (n.title.isNotBlank()) Spacer(Modifier.height(4.dp))
                if (n.isChecklist) {
                    n.body.lines().filter { it.isNotBlank() }.take(4).forEach { line ->
                        val done = line.startsWith("[x] ")
                        Text(
                            (if (done) "☑ " else "☐ ") + line.removePrefix("[x] ").removePrefix("[ ] "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(n.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            val hasFooter = n.isPinned || n.reminderAt != null || n.tags.isNotBlank()
            if (hasFooter) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (n.isPinned) Icon(Icons.Default.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (n.reminderAt != null) {
                        Icon(Icons.Default.Notifications, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmtDateShort(n.reminderAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    n.tags.split(",").filter { it.isNotBlank() }.take(1).forEach {
                        Text("#$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (n.title.isBlank() && n.body.isBlank() && n.imageUri == null) {
                Text("Catatan kosong", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NotePhoto(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { s ->
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeStream(s, null, opts)
            }
        } catch (_: Exception) { null }
    }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NoteEditorCard(
    env: AppEnv, note: Note, dark: Boolean,
    onCollapse: () -> Unit, onLogExpense: (String, Long) -> Unit, modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(note)
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(note.body) }
    var showColors by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var showSmart by remember { mutableStateOf(false) }
    var deleted by remember(note.id) { mutableStateOf(false) }

    fun persist(transform: (Note) -> Note) {
        scope.launch {
            env.dao.upsertNote(transform(latest.copy(title = title, body = body, updatedAt = System.currentTimeMillis())))
        }
    }
    fun alarmLabel(): String = title.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }?.removePrefix("[ ] ")?.removePrefix("[x] ") ?: "Catatan" }
    fun setReminder(at: Long?) {
        persist { it.copy(reminderAt = at) }
        if (at == null) Alarms.cancel(ctx, note.id.toInt())
        else {
            Alarms.schedule(ctx, note.id.toInt(), at, "note", "Pengingat: ${alarmLabel()}", body.take(120))
            if (!Notifier.canNotify(ctx)) Toast.makeText(ctx, "Izinkan notifikasi di Pengaturan agar pengingat muncul.", Toast.LENGTH_LONG).show()
        }
    }

    // simpan otomatis 0,5 detik setelah berhenti mengetik
    LaunchedEffect(title, body) {
        if (title == latest.title && body == latest.body) return@LaunchedEffect
        delay(500)
        persist { it }
    }
    // pastikan teks terakhir tersimpan saat editor ditutup
    DisposableEffect(note.id) {
        onDispose {
            if (!deleted && (title != latest.title || body != latest.body)) {
                val snapshot = latest.copy(title = title, body = body, updatedAt = System.currentTimeMillis())
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { env.dao.upsertNote(snapshot) }
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    PhotoStore.save(ctx, uri, env.settings.photoQuality)
                }
                if (saved == null) Toast.makeText(ctx, "Foto tidak bisa dibaca.", Toast.LENGTH_SHORT).show()
                else {
                    PhotoStore.delete(latest.imageUri)
                    persist { it.copy(imageUri = saved) }
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(), shape = MaterialTheme.shapes.large,
        color = noteColor(note.color, dark), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostField(title, { title = it }, "Judul", MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    Modifier.weight(1f), singleLine = true)
                IconButton(onClick = { persist { it.copy(isPinned = !latest.isPinned) } }) {
                    Icon(Icons.Default.PushPin, "Sematkan",
                        tint = if (note.isPinned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                IconButton(onClick = onCollapse) { Icon(Icons.Default.KeyboardArrowUp, "Tutup") }
            }
            if (note.imageUri != null) {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    NotePhoto(note.imageUri, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp).clip(MaterialTheme.shapes.medium))
                    IconButton(
                        onClick = { PhotoStore.delete(note.imageUri); persist { it.copy(imageUri = null) } },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                    ) { Icon(Icons.Default.Close, "Hapus foto", Modifier.size(18.dp)) }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (note.isChecklist) {
                val lines = body.split("\n")
                Column(Modifier.padding(horizontal = 8.dp)) {
                    lines.forEachIndexed { i, line ->
                        val checked = line.startsWith("[x] ")
                        val text = line.removePrefix("[x] ").removePrefix("[ ] ")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = { v ->
                                body = lines.toMutableList().also { it[i] = (if (v) "[x] " else "[ ] ") + text }.joinToString("\n")
                            })
                            BasicTextField(
                                value = text,
                                onValueChange = { t ->
                                    body = lines.toMutableList().also { it[i] = (if (checked) "[x] " else "[ ] ") + t.replace("\n", " ") }.joinToString("\n")
                                },
                                singleLine = true, modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface)
                            )
                            IconButton(onClick = {
                                val m = lines.toMutableList(); m.removeAt(i)
                                body = m.joinToString("\n")
                            }) { Icon(Icons.Default.Close, "Hapus item", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    TextButton(onClick = { body = (if (body.isBlank()) "" else body + "\n") + "[ ] " }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Tambah item")
                    }
                }
            } else {
                GhostField(body, { body = it }, "Tulis catatan…", MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    Modifier.fillMaxWidth().heightIn(min = 120.dp))
            }

            if (note.reminderAt != null) {
                AssistChip(
                    onClick = { showReminder = true },
                    label = { Text("Ingatkan ${fmtDateTime(note.reminderAt)}") },
                    leadingIcon = { Icon(Icons.Default.Notifications, null, Modifier.size(18.dp)) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            if (note.tags.isNotBlank()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    note.tags.split(",").filter { it.isNotBlank() }.forEach {
                        Text("#$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (showColors) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 0 until noteColorCount) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(noteColor(i, dark))
                                .border(if (i == note.color) 2.dp else 1.dp,
                                    if (i == note.color) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { persist { it.copy(color = i) } }
                        )
                    }
                }
            }

            // toolbar
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showReminder = true }) { Icon(Icons.Default.Notifications, "Pengingat") }
                IconButton(onClick = {
                    val toChecklist = !note.isChecklist
                    val newBody = if (toChecklist) body.lines().joinToString("\n") { l ->
                        if (l.startsWith("[ ] ") || l.startsWith("[x] ")) l else "[ ] " + l.trimStart('-', '*', '•', ' ')
                    } else body.lines().joinToString("\n") { it.removePrefix("[ ] ").removePrefix("[x] ") }
                    body = newBody
                    persist { it.copy(isChecklist = toChecklist, body = newBody) }
                }) { Icon(Icons.Default.Checklist, "Checklist", tint = if (note.isChecklist) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { photoPicker.launch("image/*") }) { Icon(Icons.Default.AddPhotoAlternate, "Foto") }
                IconButton(onClick = { showColors = !showColors }) { Icon(Icons.Default.Palette, "Warna") }
                IconButton(onClick = { showSmart = true }) { Icon(Icons.Default.AutoAwesome, "Catatan pintar") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    deleted = true
                    Alarms.cancel(ctx, note.id.toInt())
                    PhotoStore.delete(note.imageUri)
                    scope.launch { env.dao.deleteNote(note.id) }
                    onCollapse()
                }) { Icon(Icons.Default.DeleteOutline, "Hapus") }
            }
            Text(
                "Diubah ${fmtDateTime(note.updatedAt)} • ${body.split(Regex("\\s+")).count { it.isNotBlank() }} kata",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }

    if (showReminder) {
        DateTimeDialog(
            initial = note.reminderAt,
            onDismiss = { showReminder = false },
            onConfirm = { at ->
                showReminder = false
                if (at <= System.currentTimeMillis()) Toast.makeText(ctx, "Pilih waktu di masa depan.", Toast.LENGTH_SHORT).show()
                else setReminder(at)
            },
            onClear = if (note.reminderAt != null) ({ showReminder = false; setReminder(null) }) else null
        )
    }
    if (showSmart) {
        SmartSheet(
            env = env, title = title, body = body, isChecklist = note.isChecklist,
            onDismiss = { showSmart = false },
            onApplyTitle = { title = it },
            onApplyTags = { tags -> persist { it.copy(tags = tags.joinToString(",")) } },
            onMakeChecklist = {
                val nb = body.lines().filter { it.isNotBlank() }.joinToString("\n") { l ->
                    if (l.startsWith("[ ] ") || l.startsWith("[x] ")) l else "[ ] " + l.trimStart('-', '*', '•', ' ')
                }
                body = nb
                persist { it.copy(isChecklist = true, body = nb) }
            },
            onSetReminder = { setReminder(it) },
            onLogExpense = { amt -> onLogExpense(alarmLabel(), amt) },
            onInsertSummary = { s -> body = if (body.isBlank()) s else body.trimEnd() + "\n\nRingkasan:\n" + s }
        )
    }
}

@Composable
private fun SmartSheet(
    env: AppEnv, title: String, body: String, isChecklist: Boolean,
    onDismiss: () -> Unit,
    onApplyTitle: (String) -> Unit,
    onApplyTags: (List<String>) -> Unit,
    onMakeChecklist: () -> Unit,
    onSetReminder: (Long) -> Unit,
    onLogExpense: (Long) -> Unit,
    onInsertSummary: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val result = remember { Smart.analyze(title, body) }
    val online = remember { AiClient.isOnline(ctx) }
    val hasKey = env.settings.aiKey.isNotBlank()
    var aiText by remember { mutableStateOf<String?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Catatan pintar", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (online) "Offline selalu aktif. Online tersedia untuk ringkasan AI." else "Mode offline — semua analisis berjalan di perangkat.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text("${result.wordCount} kata", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (result.summary.isNotBlank() && result.wordCount > 12) {
                SectionLabel("Ringkasan singkat")
                Card2(color = MaterialTheme.colorScheme.surfaceVariant) { Text(result.summary, style = MaterialTheme.typography.bodyMedium) }
                TextButton(onClick = { onInsertSummary(result.summary); onDismiss() }) { Text("Sisipkan ke catatan") }
            }
            if (title.isBlank() && result.title.isNotBlank()) {
                SmartAction("Judul: \"${result.title}\"", "Pakai judul ini") { onApplyTitle(result.title); onDismiss() }
            }
            if (result.tags.isNotEmpty()) {
                SmartAction("Tag: " + result.tags.joinToString(", ") { "#$it" }, "Terapkan tag") { onApplyTags(result.tags); onDismiss() }
            }
            if (result.reminderAt != null) {
                SmartAction("Terdeteksi waktu: ${fmtDateTime(result.reminderAt)}", "Pasang pengingat") { onSetReminder(result.reminderAt); onDismiss() }
            }
            if (result.amount != null) {
                SmartAction("Terdeteksi nominal: ${rupiah(result.amount)}", "Catat pengeluaran") { onLogExpense(result.amount); onDismiss() }
            }
            if (!isChecklist && result.canChecklist) {
                SmartAction("Isi catatan terlihat seperti daftar", "Ubah jadi checklist") { onMakeChecklist(); onDismiss() }
            }

            SectionLabel("Ringkasan AI (online)")
            when {
                !hasKey -> Text("Tambahkan API key di Pengaturan > Catatan pintar untuk memakai fitur ini.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                !online -> Text("Tidak ada koneksi internet. Ringkasan offline di atas tetap bisa dipakai.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Button(
                        onClick = {
                            aiLoading = true; aiError = null
                            scope.launch {
                                val r = AiClient.summarize(env.settings.aiKey, title, Smart.cleanLines(body).joinToString("\n"))
                                aiLoading = false
                                r.onSuccess { aiText = it }.onFailure { aiError = it.message ?: "Gagal meringkas." }
                            }
                        },
                        enabled = !aiLoading && body.isNotBlank()
                    ) { Text(if (aiLoading) "Meringkas…" else "Ringkas dengan AI") }
                    if (aiError != null) Text(aiError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    if (aiText != null) {
                        Spacer(Modifier.height(8.dp))
                        Card2(color = MaterialTheme.colorScheme.surfaceVariant) { Text(aiText!!, style = MaterialTheme.typography.bodyMedium) }
                        TextButton(onClick = { onInsertSummary(aiText!!); onDismiss() }) { Text("Sisipkan ke catatan") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartAction(text: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onClick) { Text(action) }
    }
}
