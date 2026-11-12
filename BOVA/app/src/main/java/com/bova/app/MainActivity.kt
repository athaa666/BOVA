package com.bova.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.*
import com.bova.app.data.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF0D1117)
private val SurfaceDark = Color(0xFF161B22)
private val Accent = Color(0xFF8B9DFF)
private val Muted = Color(0xFF9AA4B2)
private val Green = Color(0xFF56D6A0)
private val Red = Color(0xFFFF7B86)

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        scheduleReminder()
        val dao = BovaDatabase.get(this).dao()
        setContent { BovaApp(dao) }
    }

    private fun scheduleReminder() {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delay = (target.timeInMillis - now.timeInMillis).coerceAtLeast(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "bova_daily_reminder", ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }
}

@Composable
private fun BovaApp(dao: BovaDao) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showMoneyDialog by remember { mutableStateOf(false) }
    val notes by dao.observeNotes().collectAsStateWithLifecycle(initialValue = emptyList())
    val transactions by dao.observeTransactions().collectAsStateWithLifecycle(initialValue = emptyList())
    val income = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("id", "ID")) }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Bg, surface = SurfaceDark,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = SurfaceDark, contentColor = Color.White) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.EditNote, null) }, label = { Text("Catatan") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Payments, null) }, label = { Text("Keuangan") })
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { if (tab == 0) showNoteDialog = true else showMoneyDialog = true },
                    containerColor = Accent, contentColor = Bg
                ) { Icon(Icons.Default.Add, "Tambah") }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(22.dp))
                Text("BOVA", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("Ruang kecil untuk mengatur harimu.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                if (tab == 0) {
                    SectionTitle("Catatanmu", "${notes.size} catatan")
                    if (notes.isEmpty()) EmptyState("Belum ada catatan", "Tekan tombol + untuk menulis sesuatu.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(notes, key = { it.id }) { note ->
                            CardItem {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(note.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                        if (note.body.isNotBlank()) Text(note.body, color = Muted, fontSize = 13.sp, maxLines = 4)
                                        Text(formatDate(note.createdAt), color = Muted, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { scope.launch { dao.deleteNote(note.id) } }) {
                                        Icon(Icons.Default.Delete, null, tint = Muted)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    SectionTitle("Ringkasan keuangan", "Tersimpan di perangkat")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard("Pemasukan", currency.format(income), Green, Modifier.weight(1f))
                        SummaryCard("Pengeluaran", currency.format(expense), Red, Modifier.weight(1f))
                    }
                    CardItem {
                        Text("Saldo bersih", color = Muted, fontSize = 13.sp)
                        Text(currency.format(income - expense), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                    SectionTitle("Riwayat transaksi", "${transactions.size} transaksi")
                    if (transactions.isEmpty()) EmptyState("Belum ada transaksi", "Tekan + untuk mencatat uang masuk atau keluar.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(transactions, key = { it.id }) { item ->
                            CardItem {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.title, fontWeight = FontWeight.SemiBold)
                                        Text("${item.category} • ${formatDate(item.createdAt)}", color = Muted, fontSize = 11.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text((if (item.type == "INCOME") "+" else "−") + currency.format(item.amount),
                                            color = if (item.type == "INCOME") Green else Red, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { scope.launch { dao.deleteTransaction(item.id) } }) {
                                            Icon(Icons.Default.Delete, null, tint = Muted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNoteDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Catatan baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Judul") }, singleLine = true)
                    OutlinedTextField(body, { body = it }, label = { Text("Isi catatan") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) {
                        scope.launch { dao.insertNote(Note(title = title.trim(), body = body.trim())) }
                        showNoteDialog = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("Batal") } }
        )
    }

    if (showMoneyDialog) {
        var title by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Makan") }
        var type by remember { mutableStateOf("EXPENSE") }
        AlertDialog(
            onDismissRequest = { showMoneyDialog = false },
            title = { Text("Transaksi baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = type == "EXPENSE", onClick = { type = "EXPENSE" }, label = { Text("Keluar") })
                        FilterChip(selected = type == "INCOME", onClick = { type = "INCOME" }, label = { Text("Masuk") })
                    }
                    OutlinedTextField(title, { title = it }, label = { Text("Keterangan") }, singleLine = true)
                    OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() } },
                        label = { Text("Nominal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(category, { category = it }, label = { Text("Kategori") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = amount.toLongOrNull()
                    if (title.isNotBlank() && value != null && value > 0) {
                        scope.launch {
                            dao.insertTransaction(MoneyTransaction(title = title.trim(), amount = value, type = type, category = category.trim().ifBlank { "Lainnya" }))
                        }
                        showMoneyDialog = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showMoneyDialog = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(detail, color = Muted, fontSize = 11.sp)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun CardItem(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), content = content)
    }
}

@Composable
private fun SummaryCard(title: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Text(value, color = tint, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    CardItem {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
    Spacer(Modifier.height(10.dp))
}

private fun formatDate(time: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(time))
