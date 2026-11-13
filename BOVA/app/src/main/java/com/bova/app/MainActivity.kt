package com.bova.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bova.app.data.BovaDatabase
import com.bova.app.data.DefaultData
import com.bova.app.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(this)) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

        val db = BovaDatabase.get(this)
        val settings = AppSettings(this)
        val env = AppEnv(applicationContext, db, settings)

        lifecycleScope.launch {
            val dao = db.dao()
            if (!settings.seeded && dao.countClasses() == 0) {
                DefaultData.classes.forEach { dao.upsertClass(it) }
            }
            settings.seeded = true
            dao.deleteBlankNotes()
            Reschedule.all(applicationContext)
        }
        setContent { BovaRoot(env) }
    }
}

private val tabs = listOf(
    "Beranda" to Icons.Default.Home,
    "Catatan" to Icons.Default.StickyNote2,
    "Jadwal" to Icons.Default.CalendarMonth,
    "Uang" to Icons.Default.AccountBalanceWallet,
    "Belajar" to Icons.Default.Timer
)

@Composable
private fun BovaRoot(env: AppEnv) {
    val s = env.settings
    val dark = when (s.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val dao = env.dao

    val notes by dao.observeNotes().collectAsStateWithLifecycle(initialValue = emptyList())
    val transactions by dao.observeTransactions().collectAsStateWithLifecycle(initialValue = emptyList())
    val classes by dao.observeClasses().collectAsStateWithLifecycle(initialValue = emptyList())
    val sessions by dao.observeSessions().collectAsStateWithLifecycle(initialValue = emptyList())

    var tab by rememberSaveable { mutableStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pendingNote by remember { mutableStateOf(false) }
    var pendingMoney by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf<Pair<String, Long>?>(null) }

    // timer: cek tiap detik apakah sesi sudah selesai
    LaunchedEffect(Unit) {
        while (true) { TimerController.checkDone(s, dao); delay(1000) }
    }

    // ikon status bar mengikuti tema aplikasi
    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        val c = WindowCompat.getInsetsController(window, view)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }

    BovaTheme(dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (showSettings) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp)) {
                    SettingsScreen(env, onBack = { showSettings = false })
                }
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
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
                    Box(
                        Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal = 18.dp)
                    ) {
                        AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                            when (t) {
                                0 -> DashboardScreen(
                                    env, notes, transactions, classes, sessions,
                                    onGoTab = { tab = it },
                                    onNewNote = { pendingNote = true; tab = 1 },
                                    onNewMoney = { pendingMoney = true; tab = 3 },
                                    onSettings = { showSettings = true }
                                )
                                1 -> NotesScreen(env, notes, dark, pendingNote, { pendingNote = false },
                                    onLogExpense = { title, amount -> prefill = title to amount; tab = 3 })
                                2 -> ScheduleScreen(env, classes)
                                3 -> FinanceScreen(env, transactions, pendingMoney, { pendingMoney = false }, prefill, { prefill = null })
                                else -> StudyScreen(env, sessions)
                            }
                        }
                    }
                }
            }
            if (!s.welcomed) WelcomeDialog(s)
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
                Text("Catatan, jadwal kuliah, keuangan, dan timer belajar dalam satu aplikasi. Semua tersimpan di perangkatmu.")
                OutlinedTextField(name, { name = it }, label = { Text("Siapa namamu?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { s.updateName(name); s.markWelcomed() }) { Text("Mulai") } },
        dismissButton = { TextButton(onClick = { s.markWelcomed() }) { Text("Lewati") } }
    )
}
