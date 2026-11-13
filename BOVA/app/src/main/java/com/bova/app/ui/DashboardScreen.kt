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

@Composable
fun DashboardScreen(
    env: AppEnv,
    notes: List<Note>,
    transactions: List<MoneyTransaction>,
    classes: List<ClassEntity>,
    sessions: List<StudySession>,
    onGoTab: (Int) -> Unit,
    onNewNote: () -> Unit,
    onNewMoney: () -> Unit,
    onSettings: () -> Unit
) {
    val s = env.settings
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val today = cal.get(Calendar.DAY_OF_WEEK)
    val name = s.userName.ifBlank { "teman" }

    val next = classes.map { it to nextStart(it, now) }.minByOrNull { it.second }
    val todayClasses = classes.filter { it.day == today }.sortedBy { it.startMin }
    val minuteNow = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    val (mStart, mEnd) = monthRange(currentYm())
    val month = transactions.filter { it.createdAt in mStart until mEnd }
    val income = month.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense = month.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val studyToday = minutesOn(sessions, startOfDay(now))
    val reminders = notes.filter { (it.reminderAt ?: 0L) > now }.sortedBy { it.reminderAt }.take(3)
    val pinned = notes.filter { it.isPinned }.take(3)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BovaLogo(38.dp)
                Spacer(Modifier.width(10.dp))
                Text("BOVA", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Pengaturan") }
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
            Text("${greeting(cal.get(Calendar.HOUR_OF_DAY))},", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name, style = MaterialTheme.typography.headlineLarge)
            Text("Semoga harimu lancar. Ini ringkasanmu hari ini.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Spacer(Modifier.height(16.dp))
            Card2(color = MaterialTheme.colorScheme.primary) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmtClock(now), fontSize = 52.sp, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary)
                    Text(" ${fmtSeconds(now)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(fmtFullDate(now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
            }
        }
        if (next != null) {
            item {
                Spacer(Modifier.height(10.dp))
                Card2(onClick = { onGoTab(2) }) {
                    Text("Kelas berikutnya", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(next.first.title, style = MaterialTheme.typography.titleMedium)
                    Text("${dayLong[next.first.day]}, ${hm(next.first.startMin)}" + (if (next.first.room.isNotBlank()) " • ${next.first.room}" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(countdown((next.second - now).coerceAtLeast(0)), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card2(Modifier.weight(1f), onClick = { onGoTab(3) }) {
                    Text("Sisa bulan ini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(rupiah(income - expense), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                Card2(Modifier.weight(1f), onClick = { onGoTab(4) }) {
                    Text("Belajar hari ini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(durationLabel(studyToday), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
            }
        }
        item {
            SectionLabel("Aksi cepat")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onNewNote, label = { Text("Catatan") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
                AssistChip(onClick = onNewMoney, label = { Text("Transaksi") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { onGoTab(4) }, label = { Text("Fokus") }, leadingIcon = { Icon(Icons.Default.Timer, null, Modifier.size(18.dp)) })
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
    }
}
