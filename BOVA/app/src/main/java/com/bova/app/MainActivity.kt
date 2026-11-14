package com.bova.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bova.app.data.BovaDao
import com.bova.app.data.BovaDatabase
import com.bova.app.data.Note
import com.bova.app.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var settingsRef: AppSettings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(this)) {
            LockState.external()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val db = BovaDatabase.get(this)
        val settings = AppSettings(this)
        settingsRef = settings
        val env = AppEnv(applicationContext, db, settings)
        if (savedInstanceState == null) LockState.locked = settings.lockEnabled

        lifecycleScope.launch {
            try {
                val dao = db.dao()
                if (!settings.seeded && dao.countClasses() == 0) DefaultData.classes.forEach { dao.upsertClass(it) }
                else fillDefaultCodes(dao)
                settings.seeded = true
                dao.deleteBlankNotes()
                Reschedule.all(applicationContext)
            } catch (e: Exception) {
                env.toast("Gagal menyiapkan data: ${e.message}")
            }
        }
        setContent { BovaRoot(env) }
    }

    override fun onStop() {
        super.onStop()
        LockState.lastBackground = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val suppressed = LockState.consumeSuppress()
        val s = settingsRef ?: return
        if (suppressed || !s.lockEnabled || LockState.locked || LockState.lastBackground == 0L) return
        val away = (SystemClock.elapsedRealtime() - LockState.lastBackground) / 1000L
        if (away >= s.autoLockSec) LockState.locked = true
    }

    private suspend fun fillDefaultCodes(dao: BovaDao) {
        val existing = dao.allClasses()
        existing.filter { it.code.isBlank() }.forEach { c ->
            val d = DefaultData.classes.firstOrNull { it.title == c.title && it.day == c.day && it.startMin == c.startMin }
            if (d != null) dao.upsertClass(c.copy(code = d.code, sks = d.sks))
        }
    }
}

private val tabs = listOf(
    "Beranda" to Icons.Default.Home,
    "Catatan" to Icons.Default.StickyNote2,
    "Tugas" to Icons.Default.CheckCircle,
    "Jadwal" to Icons.Default.CalendarMonth,
    "Uang" to Icons.Default.AccountBalanceWallet
)

@Composable
private fun BovaRoot(env: AppEnv) {
    val s = env.settings
    val ctx = LocalContext.current
    val dark = when (s.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val dao = env.dao

    val notes by dao.observeNotes().collectAsStateWithLifecycle(initialValue = emptyList())
    val transactions by dao.observeTransactions().collectAsStateWithLifecycle(initialValue = emptyList())
    val classes by dao.observeClasses().collectAsStateWithLifecycle(initialValue = emptyList())
    val sessions by dao.observeSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by dao.observeTasks().collectAsStateWithLifecycle(initialValue = emptyList())
    val budgets by dao.observeBudgets().collectAsStateWithLifecycle(initialValue = emptyList())

    var tab by rememberSaveable { mutableStateOf(s.startTab) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showFocus by rememberSaveable { mutableStateOf(false) }
    var openNoteId by remember { mutableStateOf<Long?>(null) }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var lastNote by remember { mutableStateOf<Note?>(null) }
    var lastPhoto by remember { mutableStateOf<String?>(null) }
    var pendingNote by remember { mutableStateOf(false) }
    var pendingTask by remember { mutableStateOf(false) }
    var pendingMoney by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var splash by remember { mutableStateOf(true) }

    val openNote = notes.firstOrNull { it.id == openNoteId }
    LaunchedEffect(openNote) { if (openNote != null) lastNote = openNote }
    LaunchedEffect(photoUri) { if (photoUri != null) lastPhoto = photoUri }
    LaunchedEffect(Unit) { delay(900); splash = false }

    // timer: cek tiap detik apakah sesi sudah selesai
    LaunchedEffect(Unit) {
        while (true) { TimerController.checkDone(ctx, s, dao); delay(1000) }
    }

    // ikon status bar + FLAG_SECURE mengikuti pengaturan
    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        val c = WindowCompat.getInsetsController(window, view)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
        if (s.secureWindow) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    BovaTheme(dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (LockState.locked && !splash) {
                LockScreen(s) { LockState.locked = false }
            } else if (!LockState.locked) {
                Box(Modifier.fillMaxSize()) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        snackbarHost = { SnackbarHost(env.snackbar) },
                        bottomBar = {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                                tabs.forEachIndexed { i, (label, icon) ->
                                    NavigationBarItem(
                                        selected = tab == i, onClick = { tab = i },
                                        icon = { Icon(icon, label) }, label = { Text(label, maxLines = 1) },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal = 18.dp)) {
                            if (tab != 0) MiniClock(s)
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                                label = "tab", modifier = Modifier.weight(1f)
                            ) { t ->
                                when (t) {
                                    0 -> DashboardScreen(
                                        env, notes, transactions, classes, sessions, tasks,
                                        onGoTab = { tab = it },
                                        onNewNote = { pendingNote = true; tab = 1 },
                                        onNewTask = { pendingTask = true; tab = 2 },
                                        onNewMoney = { pendingMoney = true; tab = 4 },
                                        onFocus = { showFocus = true },
                                        onSettings = { showSettings = true }
                                    )
                                    1 -> NotesScreen(env, notes, dark, pendingNote, { pendingNote = false }, onOpen = { openNoteId = it })
                                    2 -> TasksScreen(env, tasks, classes, pendingTask, { pendingTask = false })
                                    3 -> ScheduleScreen(env, classes, tasks, notes)
                                    else -> FinanceScreen(env, transactions, budgets, pendingMoney, { pendingMoney = false }, prefill, { prefill = null })
                                }
                            }
                        }
                    }

                    // Layar penuh: Mode Fokus, Pengaturan, Editor catatan, Foto
                    AnimatedVisibility(
                        visible = showFocus,
                        enter = slideInVertically(tween(260)) { it / 8 } + fadeIn(tween(220)),
                        exit = slideOutVertically(tween(200)) { it / 8 } + fadeOut(tween(160))
                    ) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            FocusScreen(env, sessions, tasks, onBack = { showFocus = false })
                        }
                    }
                    AnimatedVisibility(
                        visible = showSettings,
                        enter = slideInVertically(tween(260)) { it / 8 } + fadeIn(tween(220)),
                        exit = slideOutVertically(tween(200)) { it / 8 } + fadeOut(tween(160))
                    ) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp)) {
                                SettingsScreen(env, onBack = { showSettings = false })
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = openNote != null,
                        enter = slideInVertically(tween(260)) { it / 10 } + fadeIn(tween(220)),
                        exit = slideOutVertically(tween(200)) { it / 10 } + fadeOut(tween(160))
                    ) {
                        val shown = openNote ?: lastNote
                        if (shown != null) {
                            NoteEditorScreen(
                                env, shown, dark,
                                onClose = { openNoteId = null },
                                onOpenPhoto = { photoUri = it },
                                onLogExpense = { title, amount -> prefill = title to amount; openNoteId = null; tab = 4 }
                            )
                        }
                    }
                    AnimatedVisibility(visible = photoUri != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(150))) {
                        val u = photoUri ?: lastPhoto
                        if (u != null) PhotoViewerScreen(u, onClose = { photoUri = null })
                    }
                    if (!s.welcomed && !splash) WelcomeDialog(s)
                }
            }

            // Splash
            AnimatedVisibility(visible = splash, exit = fadeOut(tween(300))) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    BovaLogo(72.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("BOVA", style = MaterialTheme.typography.headlineMedium)
                    Text("All-in-one untuk mahasiswa", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(36.dp))
                    Text("Dibuat oleh Atha Bima", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WelcomeDialog(s: AppSettings) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { },
        icon = { BovaLogo(48.dp) },
        title = { Text("Selamat datang di BOVA") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Catatan, tugas, jadwal kuliah, keuangan, dan mode fokus dalam satu aplikasi. Semua tersimpan di perangkatmu.")
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Siapa namamu?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Dibuat oleh Atha Bima", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { s.updateName(name); s.markWelcomed() }) { Text("Mulai") } },
        dismissButton = { TextButton(onClick = { s.markWelcomed() }) { Text("Lewati") } }
    )
}
