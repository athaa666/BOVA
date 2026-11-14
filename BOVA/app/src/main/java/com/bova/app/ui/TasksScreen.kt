package com.bova.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.bova.app.*
import com.bova.app.data.ClassEntity
import com.bova.app.data.TaskItem
import kotlinx.coroutines.launch
import java.util.Calendar

val priorityLabels = listOf("Rendah", "Sedang", "Tinggi")
val remindOptions = listOf(0 to "Tanpa", 60 to "1 jam", 180 to "3 jam", 1440 to "H-1", 2880 to "H-2")

fun deadlineLabel(t: TaskItem, now: Long): String {
    val d = t.deadline ?: return "Tanpa tenggat"
    val diff = d - now
    val day = 86_400_000L
    return when {
        t.done -> "Selesai"
        diff < 0 -> "Terlambat • ${fmtDateShort(d)}"
        d < startOfDay(now) + day -> "Hari ini ${fmtClock(d)}"
        d < startOfDay(now) + 2 * day -> "Besok ${fmtClock(d)}"
        else -> fmtDateShort(d)
    }
}

@Composable
fun TasksScreen(
    env: AppEnv,
    tasks: List<TaskItem>,
    classes: List<ClassEntity>,
    pendingNew: Boolean,
    onConsumed: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000) } }
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }

    LaunchedEffect(pendingNew) { if (pendingNew) { editing = null; showSheet = true; onConsumed() } }

    val pending = remember(tasks) { tasks.filter { !it.done } }
    val done = remember(tasks) { tasks.filter { it.done }.sortedByDescending { it.doneAt ?: 0L } }
    val today = startOfDay(now)
    val overdue = pending.filter { (it.deadline ?: Long.MAX_VALUE) < now }
    val todayList = pending.filter { val d = it.deadline; d != null && d >= now && d < today + 86_400_000L }
    val upcoming = pending.filter { val d = it.deadline; d != null && d >= today + 86_400_000L }
    val noDate = pending.filter { it.deadline == null }

    fun toggle(t: TaskItem) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val updated = t.copy(done = !t.done, doneAt = if (!t.done) System.currentTimeMillis() else null)
        env.appScope.launch { env.dao.upsertTask(updated); scheduleTask(ctx, updated) }
    }

    @Composable
    fun TaskRow(t: TaskItem, modifier: Modifier = Modifier) {
        val late = !t.done && (t.deadline ?: Long.MAX_VALUE) < now
        Row(
            modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { editing = t; showSheet = true }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = t.done, onCheckedChange = { toggle(t) })
            Column(Modifier.weight(1f)) {
                Text(
                    t.title, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(deadlineLabel(t, now), style = MaterialTheme.typography.bodySmall,
                        color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (t.subject.isNotBlank()) Text("• ${t.subject}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (t.remindMin > 0 && !t.done) Icon(Icons.Default.Notifications, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!t.done && t.priority == 2) {
                Box(Modifier.padding(end = 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("Tinggi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                ScreenTitle("Tugas", if (tasks.isEmpty()) "Catat tugas dan deadline-mu" else "${done.size} dari ${tasks.size} selesai")
                if (tasks.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { done.size.toFloat() / tasks.size.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (pending.isEmpty()) {
                item { EmptyState(Icons.Default.CheckCircle, if (tasks.isEmpty()) "Belum ada tugas" else "Semua tugas selesai", "Ketuk + untuk menambah tugas dengan tenggat dan pengingat.") }
            }
            fun section(title: String, list: List<TaskItem>, danger: Boolean = false) {
                if (list.isEmpty()) return
                item(key = "s_$title") {
                    Text("$title • ${list.size}", style = MaterialTheme.typography.titleSmall,
                        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
                }
                items(list, key = { it.id }) { t -> TaskRow(t, Modifier.animateItem()) }
            }
            section("Terlambat", overdue, true)
            section("Hari ini", todayList)
            section("Mendatang", upcoming)
            section("Tanpa tenggat", noDate)
            if (done.isNotEmpty()) {
                item(key = "doneHeader") {
                    TextButton(onClick = { showDone = !showDone }, modifier = Modifier.padding(top = 12.dp)) {
                        Text(if (showDone) "Sembunyikan selesai (${done.size})" else "Tampilkan selesai (${done.size})")
                    }
                }
                if (showDone) items(done, key = { "d${it.id}" }) { t -> TaskRow(t, Modifier.animateItem()) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = null; showSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Tugas") }
        )
    }

    if (showSheet) {
        TaskSheet(
            initial = editing, subjects = classes.map { it.title }.distinct(),
            onDismiss = { showSheet = false },
            onSave = { t ->
                showSheet = false
                env.appScope.launch {
                    val id = env.dao.upsertTask(t)
                    scheduleTask(ctx, t.copy(id = if (t.id == 0L) id else t.id))
                    if (!Notifier.canNotify(ctx) && t.remindMin > 0) env.toast("Izinkan notifikasi di Pengaturan agar pengingat muncul.")
                }
            },
            onDelete = editing?.takeIf { it.id != 0L }?.let { e ->
                {
                    showSheet = false
                    val old = e
                    env.appScope.launch { env.dao.deleteTask(old.id); Alarms.cancel(ctx, Alarms.taskCode(old.id)) }
                    env.undoable("Tugas dihapus") { val id = env.dao.upsertTask(old); scheduleTask(ctx, old.copy(id = id)) }
                }
            }
        )
    }
}

@Composable
private fun TaskSheet(
    initial: TaskItem?, subjects: List<String>,
    onDismiss: () -> Unit, onSave: (TaskItem) -> Unit, onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var detail by remember { mutableStateOf(initial?.detail ?: "") }
    var subject by remember { mutableStateOf(initial?.subject ?: "") }
    var deadline by remember { mutableStateOf(initial?.deadline) }
    var priority by remember { mutableIntStateOf(initial?.priority ?: 1) }
    var remind by remember { mutableIntStateOf(initial?.remindMin ?: 0) }
    var pickDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun quick(daysAhead: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, daysAhead); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (initial == null) "Tugas baru" else "Ubah tugas", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(title, { title = it.take(120); error = null }, label = { Text("Judul tugas") }, singleLine = true, isError = error != null,
                supportingText = { if (error != null) Text(error!!) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(detail, { detail = it.take(1000) }, label = { Text("Detail (opsional)") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(subject, { subject = it.take(60) }, label = { Text("Mata kuliah (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (subjects.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { subjects.take(9).forEach { s -> PillChip(s.take(22), subject == s) { subject = s } } }
            }
            SectionLabel("Tenggat", Modifier.padding(top = 4.dp))
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(deadline?.let { fmtDateTime(it) } ?: "Pilih tanggal & jam")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip("Hari ini", false) { deadline = quick(0) }
                PillChip("Besok", false) { deadline = quick(1) }
                PillChip("3 hari", false) { deadline = quick(3) }
                PillChip("1 pekan", false) { deadline = quick(7) }
                if (deadline != null) PillChip("Hapus tenggat", false) { deadline = null; remind = 0 }
            }
            SectionLabel("Prioritas", Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { priorityLabels.forEachIndexed { i, l -> PillChip(l, priority == i) { priority = i } } }
            if (deadline != null) {
                SectionLabel("Ingatkan sebelum tenggat", Modifier.padding(top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { remindOptions.forEach { (m, l) -> PillChip(l, remind == m) { remind = m } } }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onDelete != null) OutlinedButton(onClick = onDelete) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        if (title.isBlank()) { error = "Judul tidak boleh kosong"; return@Button }
                        val dl = deadline
                        if (dl != null && remind > 0 && dl - remind * 60_000L <= System.currentTimeMillis() && initial?.done != true) {
                            // pengingat sudah lewat; tetap disimpan tanpa alarm
                        }
                        onSave(TaskItem(
                            id = initial?.id ?: 0L, title = title.trim(), detail = detail.trim(), subject = subject.trim(),
                            deadline = dl, priority = priority, done = initial?.done ?: false, doneAt = initial?.doneAt,
                            remindMin = if (dl == null) 0 else remind, createdAt = initial?.createdAt ?: System.currentTimeMillis()
                        ))
                    },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Text("Simpan") }
            }
        }
    }
    if (pickDate) DateTimeDialog(initial = deadline, onDismiss = { pickDate = false }, onConfirm = { deadline = it; pickDate = false })
}
