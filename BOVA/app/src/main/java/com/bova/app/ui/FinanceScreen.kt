package com.bova.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.bova.app.*
import com.bova.app.data.MoneyTransaction
import kotlinx.coroutines.launch

val expenseCategories = listOf("Makan", "Transport", "Kuliah", "Kos", "Belanja", "Hiburan", "Pulsa & Data", "Lainnya")
val incomeCategories = listOf("Uang saku", "Beasiswa", "Gaji / Proyek", "Lainnya")

@Composable
fun FinanceScreen(
    env: AppEnv,
    transactions: List<MoneyTransaction>,
    pendingNew: Boolean,
    onConsumed: () -> Unit,
    prefill: Pair<String, Long>?,
    onPrefillConsumed: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var ym by remember { mutableIntStateOf(currentYm()) }
    var editing by remember { mutableStateOf<MoneyTransaction?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pendingNew) { if (pendingNew) { editing = null; showDialog = true; onConsumed() } }
    LaunchedEffect(prefill) {
        if (prefill != null) {
            editing = MoneyTransaction(0, prefill.first, prefill.second, "EXPENSE", "Lainnya")
            showDialog = true
            onPrefillConsumed()
        }
    }

    val (start, end) = remember(ym) { monthRange(ym) }
    val month = transactions.filter { it.createdAt in start until end }
    val income = month.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense = month.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val totalBalance = transactions.sumOf { if (it.type == "INCOME") it.amount else -it.amount }
    val budget = env.settings.budget
    val byCategory = month.filter { it.type == "EXPENSE" }.groupBy { it.category }
        .map { it.key to it.value.sumOf { t -> t.amount } }.sortedByDescending { it.second }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { ScreenTitle("Keuangan", "Saldo total ${rupiah(totalBalance)}") }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { ym -= 1 }) { Icon(Icons.Default.ChevronLeft, "Bulan sebelumnya") }
                    Text(monthLabel(ym), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { ym += 1 }, enabled = ym < currentYm()) { Icon(Icons.Default.ChevronRight, "Bulan berikutnya") }
                }
            }
            item {
                Card2(color = MaterialTheme.colorScheme.primary) {
                    Text("Sisa bulan ini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                    Text(rupiah(income - expense), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Masuk", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                            Text(rupiah(income), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Keluar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                            Text(rupiah(expense), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
            if (budget > 0 && ym == currentYm()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    val frac = (expense.toFloat() / budget.toFloat())
                    Card2 {
                        Row {
                            Text("Anggaran bulanan", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text("${rupiah(expense)} / ${rupiah(budget)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { frac.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                            color = if (frac >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            if (frac >= 1f) "Anggaran terlampaui ${rupiah(expense - budget)}" else "Sisa ${rupiah(budget - expense)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (frac >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            if (byCategory.isNotEmpty()) {
                item { SectionLabel("Pengeluaran per kategori") }
                item {
                    Card2 {
                        val maxV = byCategory.first().second.toFloat().coerceAtLeast(1f)
                        byCategory.forEach { (cat, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                                Text(cat, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                    Box(Modifier.fillMaxWidth(v / maxV).fillMaxHeight().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
                                }
                                Text(rupiah(v), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Riwayat") }
            if (month.isEmpty()) {
                item { EmptyState(Icons.Default.AccountBalanceWallet, "Belum ada transaksi", "Ketuk + untuk mencatat uang masuk atau keluar.") }
            }
            val grouped = month.groupBy { startOfDay(it.createdAt) }.toList().sortedByDescending { it.first }
            grouped.forEach { (day, list) ->
                item(key = "h$day") {
                    val net = list.sumOf { if (it.type == "INCOME") it.amount else -it.amount }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
                        Text(fmtDayHeader(day), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(rupiah(net), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(list, key = { it.id }) { t ->
                    val isIn = t.type == "INCOME"
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { editing = t; showDialog = true }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (isIn) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null,
                                tint = if (isIn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text("${t.category} • ${fmtClock(t.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text((if (isIn) "+" else "−") + rupiah(t.amount), style = MaterialTheme.typography.titleSmall,
                            color = if (isIn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = null; showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
        ) { Icon(Icons.Default.Add, "Transaksi baru") }
    }

    if (showDialog) {
        MoneyDialog(
            initial = editing,
            onDismiss = { showDialog = false },
            onSave = { t -> scope.launch { env.dao.upsertTransaction(t) }; showDialog = false },
            onDelete = editing?.takeIf { it.id != 0L }?.let { e -> { scope.launch { env.dao.deleteTransaction(e.id) }; showDialog = false } }
        )
    }
}

@Composable
private fun MoneyDialog(
    initial: MoneyTransaction?,
    onDismiss: () -> Unit,
    onSave: (MoneyTransaction) -> Unit,
    onDelete: (() -> Unit)?
) {
    var type by remember { mutableStateOf(initial?.type ?: "EXPENSE") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Makan") }
    var whenMs by remember { mutableStateOf(initial?.createdAt ?: System.currentTimeMillis()) }
    var pickDate by remember { mutableStateOf(false) }
    val cats = if (type == "EXPENSE") expenseCategories else incomeCategories
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null || initial.id == 0L) "Transaksi baru" else "Ubah transaksi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillChip("Keluar", type == "EXPENSE") { type = "EXPENSE"; if (category !in expenseCategories) category = "Makan" }
                    PillChip("Masuk", type == "INCOME") { type = "INCOME"; if (category !in incomeCategories) category = "Uang saku" }
                }
                NumberField(amount, { amount = it }, "Nominal (Rp)", Modifier.fillMaxWidth())
                OutlinedTextField(title, { title = it }, label = { Text("Keterangan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text(fmtDateTime(whenMs)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    cats.forEach { c -> PillChip(c, category == c) { category = c } }
                    if (category !in cats) PillChip(category, true) { }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = amount.toLongOrNull()
                if (n != null && n > 0) {
                    onSave(MoneyTransaction(
                        id = initial?.id ?: 0L, title = title.trim().ifBlank { category }, amount = n,
                        type = type, category = category, createdAt = whenMs
                    ))
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
    if (pickDate) DateTimeDialog(initial = whenMs, onDismiss = { pickDate = false }, onConfirm = { whenMs = it; pickDate = false })
}
