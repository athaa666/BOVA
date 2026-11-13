package com.bova.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bova.app.*
import com.bova.app.data.StudySession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

fun minutesOn(sessions: List<StudySession>, dayStart: Long): Int =
    sessions.filter { it.finishedAt >= dayStart && it.finishedAt < dayStart + 86_400_000L }.sumOf { it.minutes }

@Composable
fun StudyScreen(env: AppEnv, sessions: List<StudySession>) {
    val s = env.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(250) } }
    var breakMode by remember { mutableStateOf(false) }
    var minutes by remember { mutableIntStateOf(25) }
    var custom by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    val state = s.timerState
    val remaining = when (state) {
        1 -> (s.timerEndAt - now).coerceAtLeast(0L)
        2 -> s.timerRemaining
        else -> minutes * 60_000L
    }
    val total = if (state == 0) minutes * 60_000L else s.timerTotal.coerceAtLeast(1L)
    val progress = if (state == 0) 1f else (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val view = LocalView.current
    DisposableEffect(state) {
        view.keepScreenOn = state == 1
        onDispose { view.keepScreenOn = false }
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val ringColor = MaterialTheme.colorScheme.primary
    val today = startOfDay(now)
    val todayMin = minutesOn(sessions, today)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle("Belajar", "Atur timer fokus, catat waktunya otomatis") }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(248.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 14.dp.toPx()
                        val inset = stroke / 2
                        val sz = Size(size.width - stroke, size.height - stroke)
                        drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
                        if (progress > 0f) drawArc(ringColor, -90f, 360f * progress, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val sec = (remaining + 999) / 1000
                        Text(String.format(java.util.Locale.US, "%02d:%02d", sec / 60, sec % 60), fontSize = 54.sp, style = MaterialTheme.typography.headlineLarge)
                        Text(
                            when (state) {
                                1 -> if (s.timerBreak) "Istirahat" else s.timerLabel
                                2 -> "Dijeda"
                                else -> if (breakMode) "Siap istirahat" else "Siap fokus"
                            },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (state == 0) {
            item {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PillChip("Fokus", !breakMode) { breakMode = false; minutes = 25 }
                    PillChip("Istirahat", breakMode) { breakMode = true; minutes = 5 }
                }
                Spacer(Modifier.height(8.dp))
                val presets = if (breakMode) listOf(5, 10, 15) else listOf(15, 25, 45, 60, 90)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { m -> PillChip("$m mnt", minutes == m && custom.isBlank()) { minutes = m; custom = "" } }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField(custom, { v -> custom = v.take(3); v.toIntOrNull()?.let { if (it in 1..600) minutes = it } }, "Durasi sendiri (menit)", Modifier.weight(1f))
                }
                if (!breakMode) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(label, { label = it }, label = { Text("Belajar apa? (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                when (state) {
                    0 -> Button(
                        onClick = { TimerController.start(ctx, s, minutes, label, breakMode) },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Mulai") }
                    1 -> {
                        Button(onClick = { TimerController.pause(ctx, s) }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.Default.Pause, null); Spacer(Modifier.width(6.dp)); Text("Jeda")
                        }
                        OutlinedButton(onClick = { scope.launch { TimerController.stop(ctx, s, env.dao) } }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Selesai")
                        }
                    }
                    else -> {
                        Button(onClick = { TimerController.resume(ctx, s) }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Lanjut")
                        }
                        OutlinedButton(onClick = { scope.launch { TimerController.stop(ctx, s, env.dao) } }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Selesai")
                        }
                    }
                }
            }
        }

        item { SectionLabel("Statistik") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Hari ini", durationLabel(todayMin), Modifier.weight(1f))
                StatTile("Pekan ini", durationLabel((0..6).sumOf { minutesOn(sessions, today - it * 86_400_000L) }), Modifier.weight(1f))
                StatTile("Total", durationLabel(sessions.sumOf { it.minutes }), Modifier.weight(1f))
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            WeekBars(sessions, today)
        }
        if (sessions.isNotEmpty()) {
            item { SectionLabel("Sesi terakhir") }
            items(sessions.take(5), key = { it.id }) { se ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(se.label, style = MaterialTheme.typography.titleSmall)
                        Text(fmtDateTime(se.finishedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(durationLabel(se.minutes), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
fun StatTile(title: String, value: String, modifier: Modifier = Modifier) {
    Card2(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
fun WeekBars(sessions: List<StudySession>, today: Long) {
    val days = (6 downTo 0).map { today - it * 86_400_000L }
    val values = days.map { minutesOn(sessions, it) }
    val maxV = (values.maxOrNull() ?: 0).coerceAtLeast(30)
    Card2 {
        Text("7 hari terakhir", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, d ->
                val cal = Calendar.getInstance().apply { timeInMillis = d }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    val h = maxOf((70f * values[i] / maxV).dp, 4.dp)
                    Box(
                        Modifier.width(20.dp).height(h).clip(RoundedCornerShape(6.dp))
                            .background(if (values[i] > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(dayShort[cal.get(Calendar.DAY_OF_WEEK)]?.take(2) ?: "", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
