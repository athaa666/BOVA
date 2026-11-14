package com.bova.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bova.app.*
import com.bova.app.data.*
import kotlinx.coroutines.delay
import java.util.Calendar

/** Jam besar: hanya komponen ini yang menggambar ulang tiap detik. */
@Composable
private fun LiveClockCard(s: AppSettings) {
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
    Card2(color = MaterialTheme.colorScheme.primary) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(clockText(now, s.use24h, false), fontSize = 52.sp, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary)
            if (s.showSeconds) Text(" ${fmtSeconds(now)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(fmtFullDate(now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
    }
}

@Composable
fun DashboardScreen(
    env: AppEnv,
    notes: List<Note>,
    transactions: List<MoneyTransaction>,
    classes: List<ClassEntity>,
    sessions: List<StudySession>,
    tasks: List<TaskItem>,
    onGoTab: (Int) -> Unit,
    onNewNote: () -> Unit,
    onNewTask: () -> Unit,
    onNewMoney: () -> Unit,
    onFocus: () -> Unit,
    onSettings: () -> Unit
) {
    val s = env.settings
    // Tick kasar (20 dtk) untuk data turunan; detik-an ditangani komponen jam & countdown sendiri.
    val tick by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(20_000) } }
    val cal = remember(tick / 60_000) { Calendar.getInstance().apply { timeInMillis = tick } }
    val today = cal.get(Calendar.DAY_OF_WEEK)
    val name = s.userName.ifBlank { "teman" }

    val next = remember(classes, tick / 60_000) { classes.map { it to nextStart(it, tick) }.minByOrNull { it.second } }
    val todayClasses = remember(classes, today) { classes.filter { it.day == today }.sortedBy { it.startMin } }
    val minuteNow = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    val (mStart, mEnd) = remember(tick / 3_600_000) { monthRange(currentYm()) }
    val balance = remember(transactions, mStart) {
        val m = transactions.filter { it.createdAt in mStart until mEnd }
        m.filter { it.type == "INCOME" }.sumOf { it.amount } - m.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    }
    val studyToday = remember(sessions, tick / 60_000) { minutesOn(sessions, startOfDay(tick)) }
    val dueTasks = remember(tasks, tick / 60_000) { tasks.filter { !it.done && it.deadline != null }.sortedBy { it.deadline }.take(3) }
    val openCount = remember(tasks) { tasks.count { !it.done } }
    val reminders = remember(notes, tick / 60_000) { notes.filter { (it.reminderAt ?: 0L) > tick }.sortedBy { it.reminderAt }.take(3) }
    val pinned = remember(notes) { notes.filter { it.isPinned }.take(3) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BovaLogo(38.dp)
                Spacer(Modifier.width(10.dp))
                Text("BOVA", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onFocus) { Icon(Icons.Default.Timer, "Mode Fokus") }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Pengaturan") }
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
            Text("${greeting(cal.get(Calendar.HOUR_OF_DAY))},", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name, style = MaterialTheme.typography.headlineLarge)
            Text(
                if (openCount > 0) "Kamu punya $openCount tugas yang belum selesai." else "Semoga harimu lancar. Ini ringkasanmu hari ini.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { Spacer(Modifier.height(16.dp)); LiveClockCard(s) }
        if (next != null) {
            item {
                Spacer(Modifier.height(10.dp))
                Card2(onClick = { onGoTab(3) }) {
                    Text("Kelas berikutnya", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(next.first.title, style = MaterialTheme.typography.titleMedium)
                    Text("${dayLong[next.first.day]}, ${hm(next.first.startMin)}" + (if (next.first.room.isNotBlank()) " • ${next.first.room}" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    LiveCountdown(next.second, MaterialTheme.typography.headlineSmall)
                }
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card2(Modifier.weight(1f), onClick = { onGoTab(4) }) {
                    Text("Sisa bulan ini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(rupiah(balance), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                Card2(Modifier.weight(1f), onClick = onFocus) {
                    Text("Fokus hari ini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(durationLabel(studyToday), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
            }
        }
        item {
            SectionLabel("Aksi cepat")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onNewNote, label = { Text("Catatan") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
                AssistChip(onClick = onNewTask, label = { Text("Tugas") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
                AssistChip(onClick = onNewMoney, label = { Text("Uang") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
            }
        }
        if (dueTasks.isNotEmpty()) {
            item { SectionLabel("Tugas terdekat") }
            item {
                Card2(onClick = { onGoTab(2) }) {
                    dueTasks.forEachIndexed { i, t ->
                        val late = (t.deadline ?: 0L) < tick
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                if (t.subject.isNotBlank()) Text(t.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Text(deadlineLabel(t, tick), style = MaterialTheme.typography.labelMedium, color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (i < dueTasks.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        if (todayClasses.isNotEmpty()) {
            item { SectionLabel("Kelas hari ini") }
            item {
                Card2 {
                    todayClasses.forEachIndexed { i, c ->
                        val done = c.endMin <= minuteNow
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(hm(c.startMin), Modifier.width(56.dp), style = MaterialTheme.typography.titleSmall,
                                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Column(Modifier.weight(1f)) {
                                Text(c.title, style = MaterialTheme.typography.bodyMedium,
                                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                if (c.room.isNotBlank()) Text(c.room, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (done) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (i < todayClasses.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        if (reminders.isNotEmpty()) {
            item { SectionLabel("Pengingat mendatang") }
            item {
                Card2(onClick = { onGoTab(1) }) {
                    reminders.forEach { n ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Text(n.title.ifBlank { n.body.lineSequence().firstOrNull { it.isNotBlank() } ?: "Catatan" }, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                            Text(fmtDateShort(n.reminderAt ?: 0L), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (pinned.isNotEmpty()) {
            item { SectionLabel("Catatan disematkan") }
            item {
                Card2(onClick = { onGoTab(1) }) {
                    pinned.forEach { n ->
                        Text(n.title.ifBlank { "Tanpa judul" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                        if (n.body.isNotBlank()) Text(n.body.removePrefix("[ ] ").removePrefix("[x] "), style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            Text("Dibuat oleh Atha Bima", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
