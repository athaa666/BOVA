package com.bova.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bova.app.*
import com.bova.app.data.ClassEntity
import com.bova.app.data.Note
import com.bova.app.data.TaskItem
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun ScheduleScreen(env: AppEnv, classes: List<ClassEntity>, tasks: List<TaskItem>, notes: List<Note>) {
    var mode by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Jadwal", if (mode == 0) "Kelas mingguan • ${classes.size} mata kuliah" else "Kalender bulanan: kelas, tugas, pengingat")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            PillChip("Kelas", mode == 0) { mode = 0 }
            PillChip("Kalender", mode == 1) { mode = 1 }
        }
        if (mode == 0) WeekView(env, classes) else MonthCalendar(classes, tasks, notes)
    }
}

@Composable
private fun WeekView(env: AppEnv, classes: List<ClassEntity>) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var selectedDay by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) }
    var editing by remember { mutableStateOf<ClassEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val minuteTick by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(20_000) } }

    val next = remember(classes, minuteTick) { classes.map { it to nextStart(it, minuteTick) }.minByOrNull { it.second } }
    val dayClasses = remember(classes, selectedDay) { classes.filter { it.day == selectedDay }.sortedBy { it.startMin } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (next != null) {
                item {
                    Card2(color = MaterialTheme.colorScheme.primary) {
                        Text("Kelas berikutnya", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        Text(next.first.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                        Text("${dayLong[next.first.day]} ${hm(next.first.startMin)}" + (if (next.first.room.isNotBlank()) " • ${next.first.room}" else ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        Spacer(Modifier.height(10.dp))
                        LiveCountdown(next.second, MaterialTheme.typography.headlineMedium, MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            item {
                Row(Modifier.padding(vertical = 14.dp).horizontalScroll(rememberScrollState())) {
                    dayOrder.forEach { d ->
                        val count = classes.count { it.day == d }
                        PillChip("${dayShort[d]}${if (count > 0) " · $count" else ""}", selectedDay == d) { selectedDay = d }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
            if (dayClasses.isEmpty()) {
                item { EmptyState(Icons.Default.CalendarMonth, "Tidak ada kelas hari ${dayLong[selectedDay]}", "Ketuk \"Tambah kelas\" untuk menambah mata kuliah.") }
            }
            items(dayClasses, key = { it.id }) { c ->
                Card2(modifier = Modifier.padding(bottom = 10.dp).animateItem(), onClick = { editing = c; showDialog = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(84.dp)) {
                            Text(hm(c.startMin), style = MaterialTheme.typography.titleMedium)
                            Text("– ${hm(c.endMin)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.titleSmall)
                            if (c.room.isNotBlank()) Text(c.room, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val meta = listOfNotNull(c.code.takeIf { it.isNotBlank() }, c.sks.takeIf { it > 0 }?.let { "$it SKS" }).joinToString(" • ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Edit, "Ubah", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = null; showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Tambah kelas") }
        )
    }

    if (showDialog) {
        ClassDialog(
            initial = editing, defaultDay = selectedDay, existing = classes,
            onDismiss = { showDialog = false },
            onSave = { c ->
                showDialog = false
                env.appScope.launch { env.dao.upsertClass(c); ClassReminderScheduler.rescheduleAll(ctx) }
            },
            onDelete = editing?.let { e ->
                {
                    showDialog = false
                    env.appScope.launch { env.dao.deleteClass(e.id); ClassReminderScheduler.rescheduleAll(ctx) }
                    env.undoable("Kelas dihapus") { env.dao.upsertClass(e); ClassReminderScheduler.rescheduleAll(ctx) }
                }
            }
        )
    }
}

@Composable
private fun ClassDialog(
    initial: ClassEntity?, defaultDay: Int, existing: List<ClassEntity>,
    onDismiss: () -> Unit, onSave: (ClassEntity) -> Unit, onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var room by remember { mutableStateOf(initial?.room ?: "") }
    var code by remember { mutableStateOf(initial?.code ?: "") }
    var sks by remember { mutableStateOf(initial?.sks?.takeIf { it > 0 }?.toString() ?: "") }
    var day by remember { mutableIntStateOf(initial?.day ?: defaultDay) }
    var startMin by remember { mutableIntStateOf(initial?.startMin ?: 8 * 60) }
    var endMin by remember { mutableIntStateOf(initial?.endMin ?: (9 * 60 + 40)) }
    var pick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Kelas baru" else "Ubah kelas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(80); error = null }, label = { Text("Mata kuliah") }, singleLine = true, isError = error != null,
                    supportingText = { if (error != null) Text(error!!) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(room, { room = it.take(80) }, label = { Text("Ruangan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(code, { code = it.take(20) }, label = { Text("Kode kelas") }, singleLine = true, modifier = Modifier.weight(1f))
                    NumberField(sks, { sks = it.take(1) }, "SKS", Modifier.weight(0.6f))
                }
                Text("Hari", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { dayOrder.forEach { d -> PillChip(dayShort[d] ?: "", day == d) { day = d } } }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { pick = 1 }, modifier = Modifier.weight(1f)) { Text("Mulai ${hm(startMin)}") }
                    OutlinedButton(onClick = { pick = 2 }, modifier = Modifier.weight(1f)) { Text("Selesai ${hm(endMin)}") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) { error = "Nama mata kuliah wajib diisi"; return@TextButton }
                if (endMin <= startMin) { error = "Jam selesai harus setelah jam mulai"; return@TextButton }
                val clash = existing.firstOrNull { it.id != (initial?.id ?: -1L) && it.day == day && startMin < it.endMin && endMin > it.startMin }
                if (clash != null && error == null) { error = "Bentrok dengan ${clash.title}. Tekan Simpan lagi untuk tetap menyimpan."; return@TextButton }
                onSave(ClassEntity(initial?.id ?: 0L, title.trim(), room.trim(), day, startMin, endMin, code.trim(), sks.toIntOrNull() ?: 0))
            }) { Text("Simpan") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        }
    )
    if (pick == 1) TimeDialog(startMin, "Jam mulai", { pick = 0 }) { startMin = it; if (endMin <= it) endMin = (it + 100).coerceAtMost(23 * 60 + 59); error = null; pick = 0 }
    if (pick == 2) TimeDialog(endMin, "Jam selesai", { pick = 0 }) { endMin = it; error = null; pick = 0 }
}

// ---------- Kalender bulanan ----------

private data class DayEvent(val kind: Int, val time: String, val title: String, val sub: String, val sortKey: Long)

@Composable
private fun MonthCalendar(classes: List<ClassEntity>, tasks: List<TaskItem>, notes: List<Note>) {
    var ym by rememberSaveable { mutableStateOf(currentYm()) }
    var selected by rememberSaveable { mutableStateOf(startOfDay(System.currentTimeMillis())) }
    val (start, end) = remember(ym) { monthRange(ym) }
    val cal = remember(ym) { Calendar.getInstance().apply { timeInMillis = start } }
    val offset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val rows = (offset + daysInMonth + 6) / 7
    val today = startOfDay(System.currentTimeMillis())

    fun dayMs(d: Int): Long = Calendar.getInstance().apply { timeInMillis = start; set(Calendar.DAY_OF_MONTH, d) }.timeInMillis
    val classDays = remember(classes) { classes.map { it.day }.toSet() }
    val taskDays = remember(tasks, ym) { tasks.filter { !it.done && it.deadline != null && it.deadline in start until end }.map { startOfDay(it.deadline!!) }.toSet() }
    val noteDays = remember(notes, ym) { notes.filter { it.reminderAt != null && it.reminderAt in start until end }.map { startOfDay(it.reminderAt!!) }.toSet() }

    val events = remember(selected, classes, tasks, notes) {
        val dow = Calendar.getInstance().apply { timeInMillis = selected }.get(Calendar.DAY_OF_WEEK)
        val list = mutableListOf<DayEvent>()
        classes.filter { it.day == dow }.forEach { list += DayEvent(0, "${hm(it.startMin)}–${hm(it.endMin)}", it.title, it.room, selected + it.startMin * 60_000L) }
        tasks.filter { it.deadline != null && startOfDay(it.deadline) == selected }.forEach {
            list += DayEvent(1, fmtClock(it.deadline!!), (if (it.done) "✓ " else "") + it.title, "Deadline" + (if (it.subject.isNotBlank()) " • ${it.subject}" else ""), it.deadline!!)
        }
        notes.filter { it.reminderAt != null && startOfDay(it.reminderAt) == selected }.forEach {
            list += DayEvent(2, fmtClock(it.reminderAt!!), it.title.ifBlank { it.body.lineSequence().firstOrNull { l -> l.isNotBlank() } ?: "Catatan" }, "Pengingat catatan", it.reminderAt!!)
        }
        list.sortedBy { it.sortKey }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ym -= 1 }) { Icon(Icons.Default.ChevronLeft, "Bulan sebelumnya") }
                Text(monthLabel(ym), Modifier.weight(1f).clickable { ym = currentYm(); selected = today }, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                IconButton(onClick = { ym += 1 }) { Icon(Icons.Default.ChevronRight, "Bulan berikutnya") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(Modifier.padding(top = 6.dp)) {
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val d = r * 7 + c - offset + 1
                            if (d < 1 || d > daysInMonth) { Spacer(Modifier.weight(1f).height(48.dp)); continue }
                            val ms = dayMs(d)
                            val dow = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_WEEK)
                            val isSel = ms == selected
                            val isToday = ms == today
                            Box(
                                Modifier.weight(1f).height(48.dp).padding(2.dp).clip(MaterialTheme.shapes.small)
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { selected = ms },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$d", style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isToday) androidx.compose.ui.text.font.FontWeight.Bold else null
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(8.dp).padding(top = 2.dp)) {
                                        val dotCol = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        if (dow in classDays) SmallDot(dotCol.copy(alpha = 0.55f), 5.dp)
                                        if (ms in taskDays) SmallDot(if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.error, 5.dp)
                                        if (ms in noteDays) SmallDot(if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary, 5.dp)
                                    }
                                }
                                if (isToday && !isSel) Box(Modifier.matchParentSize().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)))
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), "Kelas")
                LegendDot(MaterialTheme.colorScheme.error, "Deadline")
                LegendDot(MaterialTheme.colorScheme.tertiary, "Pengingat")
            }
            SectionLabel(fmtFullDate(selected))
        }
        if (events.isEmpty()) {
            item { Text("Tidak ada agenda di hari ini.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp)) }
        }
        items(events) { e ->
            Card2(Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(when (e.kind) { 0 -> MaterialTheme.colorScheme.onSurfaceVariant; 1 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.tertiary }))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        if (e.sub.isNotBlank()) Text(e.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(e.time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallDot(color, 7.dp); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
