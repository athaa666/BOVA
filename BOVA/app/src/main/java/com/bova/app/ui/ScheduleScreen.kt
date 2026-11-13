package com.bova.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bova.app.*
import com.bova.app.data.ClassEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun ScheduleScreen(env: AppEnv, classes: List<ClassEntity>) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var selectedDay by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) }
    var editing by remember { mutableStateOf<ClassEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }

    val next = classes.map { it to nextStart(it, now) }.minByOrNull { it.second }
    val dayClasses = classes.filter { it.day == selectedDay }.sortedBy { it.startMin }

    fun afterChange() { scope.launch { ClassReminderScheduler.rescheduleAll(ctx) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { ScreenTitle("Jadwal kuliah", if (env.settings.classReminders) "Pengingat aktif ${env.settings.classLeads.sortedDescending().joinToString(" & ")} menit sebelum kelas" else "Pengingat kelas dimatikan") }
            if (next != null) {
                item {
                    Card2(color = MaterialTheme.colorScheme.primary) {
                        Text("Kelas berikutnya", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        Text(next.first.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                        Text("${dayLong[next.first.day]} ${hm(next.first.startMin)}" + (if (next.first.room.isNotBlank()) " • ${next.first.room}" else ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        Spacer(Modifier.height(10.dp))
                        Text(countdown((next.second - now).coerceAtLeast(0)), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
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
                item { EmptyState(Icons.Default.CalendarMonth, "Tidak ada kelas hari ${dayLong[selectedDay]}", "Ketuk + untuk menambah mata kuliah.") }
            }
            items(dayClasses, key = { it.id }) { c ->
                Card2(modifier = Modifier.padding(bottom = 10.dp), onClick = { editing = c; showDialog = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(84.dp)) {
                            Text(hm(c.startMin), style = MaterialTheme.typography.titleMedium)
                            Text("– ${hm(c.endMin)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.titleSmall)
                            if (c.room.isNotBlank()) Text(c.room, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = null; showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
        ) { Icon(Icons.Default.Add, "Tambah kelas") }
    }

    if (showDialog) {
        ClassDialog(
            initial = editing, defaultDay = selectedDay,
            onDismiss = { showDialog = false },
            onSave = { c -> scope.launch { env.dao.upsertClass(c); ClassReminderScheduler.rescheduleAll(ctx) }; showDialog = false },
            onDelete = editing?.let { e -> { scope.launch { env.dao.deleteClass(e.id); ClassReminderScheduler.rescheduleAll(ctx) }; showDialog = false } }
        )
    }
}

@Composable
private fun ClassDialog(
    initial: ClassEntity?, defaultDay: Int,
    onDismiss: () -> Unit, onSave: (ClassEntity) -> Unit, onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var room by remember { mutableStateOf(initial?.room ?: "") }
    var day by remember { mutableIntStateOf(initial?.day ?: defaultDay) }
    var startMin by remember { mutableIntStateOf(initial?.startMin ?: 8 * 60) }
    var endMin by remember { mutableIntStateOf(initial?.endMin ?: (8 * 60 + 100)) }
    var pick by remember { mutableIntStateOf(0) } // 1 mulai, 2 selesai

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Kelas baru" else "Ubah kelas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Mata kuliah") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(room, { room = it }, label = { Text("Ruangan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dayOrder.forEach { d -> PillChip(dayShort[d] ?: "", day == d) { day = d } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { pick = 1 }, modifier = Modifier.weight(1f)) { Text("Mulai ${hm(startMin)}") }
                    OutlinedButton(onClick = { pick = 2 }, modifier = Modifier.weight(1f)) { Text("Selesai ${hm(endMin)}") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) {
                    val end = if (endMin <= startMin) startMin + 60 else endMin
                    onSave(ClassEntity(initial?.id ?: 0L, title.trim(), room.trim(), day, startMin, end))
                }
            }) { Text("Simpan") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        }
    )
    if (pick == 1) TimeDialog(startMin, "Jam mulai", { pick = 0 }) { startMin = it; if (endMin <= it) endMin = it + 100; pick = 0 }
    if (pick == 2) TimeDialog(endMin, "Jam selesai", { pick = 0 }) { endMin = it; pick = 0 }
}
