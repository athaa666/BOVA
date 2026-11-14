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
