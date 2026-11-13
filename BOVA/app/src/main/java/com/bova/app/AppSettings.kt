package com.bova.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*

/** Pengaturan + status timer belajar, disimpan di SharedPreferences dan dapat diamati Compose. */
class AppSettings(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("bova_prefs", Context.MODE_PRIVATE)

    var userName by mutableStateOf(sp.getString("name", "") ?: "")
        private set
    var welcomed by mutableStateOf(sp.getBoolean("welcomed", false))
        private set
    var themeMode by mutableIntStateOf(sp.getInt("theme", 0)) // 0 sistem, 1 terang, 2 gelap
        private set
    var classReminders by mutableStateOf(sp.getBoolean("class_rem", true))
        private set
    var classLeads by mutableStateOf(parseLeads(sp.getString("class_leads", "30,15")))
        private set
    var budget by mutableLongStateOf(sp.getLong("budget", 0L))
        private set
    var aiKey by mutableStateOf(sp.getString("ai_key", "") ?: "")
        private set
    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) { sp.edit().putBoolean("seeded", v).apply() }

    // Timer belajar: 0 diam, 1 berjalan, 2 jeda
    var timerState by mutableIntStateOf(sp.getInt("t_state", 0))
        private set
    var timerEndAt by mutableLongStateOf(sp.getLong("t_end", 0L))
        private set
    var timerRemaining by mutableLongStateOf(sp.getLong("t_rem", 0L))
        private set
    var timerTotal by mutableLongStateOf(sp.getLong("t_total", 0L))
        private set
    var timerLabel by mutableStateOf(sp.getString("t_label", "Belajar") ?: "Belajar")
        private set
    var timerBreak by mutableStateOf(sp.getBoolean("t_break", false))
        private set

    fun updateName(v: String) { userName = v.trim(); sp.edit().putString("name", userName).apply() }
    fun markWelcomed() { welcomed = true; sp.edit().putBoolean("welcomed", true).apply() }
    fun updateTheme(v: Int) { themeMode = v; sp.edit().putInt("theme", v).apply() }
    fun updateClassReminders(v: Boolean) { classReminders = v; sp.edit().putBoolean("class_rem", v).apply() }
    fun updateClassLeads(v: Set<Int>) {
        classLeads = v
        sp.edit().putString("class_leads", v.sortedDescending().joinToString(",")).apply()
    }
    fun updateBudget(v: Long) { budget = v; sp.edit().putLong("budget", v).apply() }
    fun updateAiKey(v: String) { aiKey = v.trim(); sp.edit().putString("ai_key", aiKey).apply() }

    fun setTimer(state: Int, endAt: Long, remaining: Long, total: Long, label: String, isBreak: Boolean) {
        timerState = state; timerEndAt = endAt; timerRemaining = remaining
        timerTotal = total; timerLabel = label; timerBreak = isBreak
        sp.edit().putInt("t_state", state).putLong("t_end", endAt).putLong("t_rem", remaining)
            .putLong("t_total", total).putString("t_label", label).putBoolean("t_break", isBreak).apply()
    }

    private fun parseLeads(raw: String?): Set<Int> =
        (raw ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
}
