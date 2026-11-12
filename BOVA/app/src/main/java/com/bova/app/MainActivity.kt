package com.bova.app

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bova.app.data.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val Bg = Color(0xFF0C0A10)
private val Panel = Color(0xFF191521)
private val Panel2 = Color(0xFF211B2D)
private val Accent = Color(0xFFB69AFF)
private val Muted = Color(0xFFAAA2B8)
private val Green = Color(0xFF70D6A5)
private val Red = Color(0xFFFF8795)

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        ClassReminderScheduler.scheduleUpcoming(this)
        setContent { BovaApp(BovaDatabase.get(this).dao()) }
    }
}

private data class ClassItem(val day: Int, val startHour: Int, val startMinute: Int, val end: String, val title: String, val room: String)
private val classes = listOf(
    ClassItem(Calendar.MONDAY,10,20,"12.00","Praktikum Fisika Dasar","Lab Fisika TPB 3 • Labtek 1 Lt. 2"),
    ClassItem(Calendar.MONDAY,13,0,"14.40","Biologi Dasar","GKU1120 (60)"),
    ClassItem(Calendar.TUESDAY,7,30,"09.10","Matriks dan Ruang Vektor","GK1.206B (60)"),
    ClassItem(Calendar.TUESDAY,15,0,"16.40","Kimia Dasar","GK1.301 (80)"),
    ClassItem(Calendar.WEDNESDAY,15,0,"16.40","Dasar Teknologi Digital","Lab MM TPB 2 • Labtek 3 Lt. 3"),
    ClassItem(Calendar.THURSDAY,8,0,"09.40","Transformasi Berkelanjutan Sumatera","GK1.106 (80)"),
    ClassItem(Calendar.THURSDAY,13,0,"15.30","Matematika Dasar","GK1.120 (80)"),
    ClassItem(Calendar.FRIDAY,10,20,"12.00","Fisika Dasar","GK1.119 (80)"),
    ClassItem(Calendar.FRIDAY,15,0,"16.40","Pengantar Instrumentasi dan Automasi","GK1.206B (60)")
)
private val dayNames = mapOf(
    Calendar.MONDAY to "Senin", Calendar.TUESDAY to "Selasa", Calendar.WEDNESDAY to "Rabu",
    Calendar.THURSDAY to "Kamis", Calendar.FRIDAY to "Jumat"
)

@Composable
private fun BovaApp(dao: BovaDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var noteDialog by remember { mutableStateOf(false) }
    var moneyDialog by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val notes by dao.observeNotes().collectAsStateWithLifecycle(initialValue = emptyList())
    val transactions by dao.observeTransactions().collectAsStateWithLifecycle(initialValue = emptyList())
    val income = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("id","ID")) }
    val nextClass = remember { classes.map { it to nextClassTime(it) }.minByOrNull { it.second } }
    val millisNow by rememberMinuteTicker()

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, secondary = Accent, background = Bg, surface = Panel,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = Color.White) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.StickyNote2, null) }, label = { Text("Catatan") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("Jadwal") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.AccountBalanceWallet, null) }, label = { Text("Uang") })
                }
            },
            floatingActionButton = {
                if (tab != 1) FloatingActionButton(
                    onClick = { if (tab == 0) noteDialog = true else moneyDialog = true },
                    containerColor = Accent, contentColor = Bg
                ) { Icon(Icons.Default.Add, "Tambah") }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Panel2, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Text("B", color = Accent, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text("BOVA", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Your day, beautifully organized.", color = Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (tab == 0) {
                    Text("Ruang catatan", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Simpan ide, momen, dan hal penting.", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Cari catatan...") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true, shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(12.dp))
                    val shown = notes.filter { (it.title + " " + it.body).contains(search, true) }
                    if (shown.isEmpty()) EmptyState("Belum ada catatan", "Tekan tombol + untuk membuat catatan pertamamu.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(shown, key = { it.id }) { note ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(note.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                        if (note.isPinned) Icon(Icons.Default.PushPin, null, tint = Accent, modifier = Modifier.size(17.dp))
                                        IconButton(onClick = { scope.launch { dao.deleteNote(note.id) } }) { Icon(Icons.Default.DeleteOutline, null, tint = Muted) }
                                    }
                                    if (note.imageUri != null) NotePhoto(note.imageUri!!)
                                    if (note.body.isNotBlank()) Text(note.body, color = Muted, fontSize = 13.sp)
                                    Text(formatDate(note.createdAt), color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                } else if (tab == 1) {
                    Text("Jadwal kuliah", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("TPB 61 • Pengingat 30 & 15 menit sebelumnya", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    if (nextClass != null) {
                        val remaining = (nextClass.second - millisNow).coerceAtLeast(0)
                        Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("KELAS BERIKUTNYA", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(5.dp))
                                Text(nextClass.first.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(nextClass.first.room, color = Muted, fontSize = 12.sp)
                                Spacer(Modifier.height(10.dp))
                                Text(countdown(remaining), color = Accent, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Menuju kelas • ${dayNames[nextClass.first.day]}", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(classes.groupBy { it.day }.toList()) { (day, items) ->
                            Text(dayNames[day] ?: "", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            items.forEach { item ->
                                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.width(83.dp)) {
                                            Text(String.format("%02d:%02d",item.startHour,item.startMinute), fontWeight = FontWeight.Bold, color = Accent)
                                            Text("– ${item.end}", color = Muted, fontSize = 11.sp)
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text(item.room, color = Muted, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("Keuangan", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Pantau uang masuk dan keluar.", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        FinanceCard("Pemasukan", currency.format(income), Green, Modifier.weight(1f))
                        FinanceCard("Pengeluaran", currency.format(expense), Red, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp)) {
                            Text("Saldo bersih", color = Muted, fontSize = 12.sp)
                            Text(currency.format(income-expense), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    Text("Riwayat transaksi", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (transactions.isEmpty()) EmptyState("Belum ada transaksi", "Tekan + untuk mencatat uang.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(transactions, key = { it.id }) { item ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
                                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.title, fontWeight = FontWeight.SemiBold)
                                        Text("${item.category} • ${formatDate(item.createdAt)}", color = Muted, fontSize = 10.sp)
                                    }
                                    Text((if(item.type=="INCOME") "+" else "−")+currency.format(item.amount),
                                        color=if(item.type=="INCOME") Green else Red, fontWeight=FontWeight.Bold, fontSize=12.sp)
                                    IconButton(onClick={ scope.launch { dao.deleteTransaction(item.id) } }) { Icon(Icons.Default.DeleteOutline,null,tint=Muted) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (noteDialog) NoteDialog(onDismiss = { noteDialog = false }, onSave = { title, body, image, pinned ->
        scope.launch { dao.insertNote(Note(title=title, body=body, imageUri=image, isPinned=pinned)) }
        noteDialog = false
    })
    if (moneyDialog) MoneyDialog(onDismiss = { moneyDialog = false }, onSave = { title, amount, type, category ->
        scope.launch { dao.insertTransaction(MoneyTransaction(title=title, amount=amount, type=type, category=category)) }
        moneyDialog = false
    })
}

@Composable
private fun rememberMinuteTicker(): State<Long> {
    val state = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(30_000); state.longValue = System.currentTimeMillis() }
    }
    return state
}

@Composable
private fun NoteDialog(onDismiss: () -> Unit, onSave: (String,String,String?,Boolean) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<String?>(null) }
    var pinned by remember { mutableStateOf(false) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            image = uri.toString()
        }
    }
    AlertDialog(onDismissRequest=onDismiss, title={ Text("Catatan baru") },
        text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title,{title=it},label={Text("Judul")},singleLine=true)
            OutlinedTextField(body,{body=it},label={Text("Isi catatan")},minLines=3)
            Row(verticalAlignment=Alignment.CenterVertically) {
                OutlinedButton(onClick={ picker.launch(arrayOf("image/*")) }) { Icon(Icons.Default.AddPhotoAlternate,null); Spacer(Modifier.width(5.dp)); Text(if(image==null) "Tambah foto" else "Ganti foto") }
                Spacer(Modifier.width(8.dp))
                if(image!=null) Text("Foto dipilih",color=Accent,fontSize=11.sp)
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=pinned,onCheckedChange={pinned=it})
                Text("Sematkan catatan")
            }
        } },
        confirmButton={ TextButton(onClick={ if(title.isNotBlank()) onSave(title.trim(),body.trim(),image,pinned) }) { Text("Simpan") } },
        dismissButton={ TextButton(onClick=onDismiss) { Text("Batal") } })
}

@Composable
private fun NotePhoto(uri: String) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        try { context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
    }
    if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max=210.dp), contentScale=ContentScale.Crop)
}

@Composable
private fun MoneyDialog(onDismiss:()->Unit,onSave:(String,Long,String,String)->Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Makan") }
    var type by remember { mutableStateOf("EXPENSE") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Transaksi baru")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=type=="EXPENSE",onClick={type="EXPENSE"},label={Text("Keluar")})
                FilterChip(selected=type=="INCOME",onClick={type="INCOME"},label={Text("Masuk")})
            }
            OutlinedTextField(title,{title=it},label={Text("Keterangan")},singleLine=true)
            OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},label={Text("Nominal (Rp)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
            OutlinedTextField(category,{category=it},label={Text("Kategori")},singleLine=true)
        }
    },confirmButton={TextButton(onClick={val n=amount.toLongOrNull(); if(title.isNotBlank() && n!=null && n>0) onSave(title.trim(),n,type,category.ifBlank{"Lainnya"})}){Text("Simpan")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Batal")}})
}

@Composable
private fun FinanceCard(title:String,value:String,tint:Color,modifier:Modifier=Modifier) {
    Card(modifier,colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(13.dp)) { Text(title,color=Muted,fontSize=11.sp); Text(value,color=tint,fontWeight=FontWeight.Bold,fontSize=15.sp) }
    }
}
@Composable
private fun EmptyState(title:String,subtitle:String) {
    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(title,fontWeight=FontWeight.SemiBold); Text(subtitle,color=Muted,fontSize=12.sp) }
    }
}
private fun nextClassTime(item:ClassItem):Long {
    val now=Calendar.getInstance()
    val target=Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY,item.startHour); set(Calendar.MINUTE,item.startMinute); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0)
        val delta=(item.day-get(Calendar.DAY_OF_WEEK)+7)%7
        add(Calendar.DAY_OF_YEAR,delta)
        if(timeInMillis<=now.timeInMillis) add(Calendar.DAY_OF_YEAR,7)
    }
    return target.timeInMillis
}
private fun countdown(ms:Long):String {
    val total=ms/1000; val days=total/86400; val hours=(total%86400)/3600; val minutes=(total%3600)/60; val seconds=total%60
    return if(days>0) String.format("%d hari %02d:%02d:%02d",days,hours,minutes,seconds)
    else String.format("%02d:%02d:%02d",hours,minutes,seconds)
}
private fun formatDate(time:Long):String=SimpleDateFormat("dd MMM yyyy, HH:mm",Locale("id","ID")).format(Date(time))
