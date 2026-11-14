package com.bova.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*

/** Pengaturan + status timer belajar + kunci aplikasi, disimpan di SharedPreferences dan dapat diamati Compose. */
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
    var aiKey by mutableStateOf(loadKey())
        private set
    var use24h by mutableStateOf(sp.getBoolean("use24h", true))
        private set
    var showSeconds by mutableStateOf(sp.getBoolean("show_sec", true))
        private set
    var startTab by mutableIntStateOf(sp.getInt("start_tab", 0))
        private set
    var focusMin by mutableIntStateOf(sp.getInt("focus_min", 25))
        private set
    var breakMin by mutableIntStateOf(sp.getInt("break_min", 5))
        private set
    var photoQuality by mutableIntStateOf(sp.getInt("photo_q", 80))
        private set
    var focusDnd by mutableStateOf(sp.getBoolean("focus_dnd", false))
        private set
    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) { sp.edit().putBoolean("seeded", v).apply() }
    var dndPrevious: Int
        get() = sp.getInt("dnd_prev", -1)
        set(v) { sp.edit().putInt("dnd_prev", v).apply() }

    // Keamanan
    var lockEnabled by mutableStateOf(sp.getBoolean("lock_on", false))
        private set
    var lockMethod by mutableIntStateOf(sp.getInt("lock_method", 0)) // 0 biometrik/PIN HP, 1 PIN BOVA
        private set
    var biometricOnPin by mutableStateOf(sp.getBoolean("bio_on_pin", true))
        private set
    var autoLockSec by mutableIntStateOf(sp.getInt("auto_lock", 30))
        private set
    var secureWindow by mutableStateOf(sp.getBoolean("secure_win", false))
        private set
    var hidePrivate by mutableStateOf(sp.getBoolean("hide_private", true))
        private set
    val hasPin: Boolean get() = sp.getString("pin_hash", null) != null

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

    private fun loadKey(): String {
        val enc = sp.getString("ai_key_enc", null)
        if (enc != null) return Crypto.decrypt(enc) ?: ""
        val legacy = sp.getString("ai_key", null)
        if (!legacy.isNullOrBlank()) {
            try { sp.edit().putString("ai_key_enc", Crypto.encrypt(legacy)).remove("ai_key").apply() } catch (_: Exception) {}
            return legacy
        }
        return ""
    }

    fun updateName(v: String) { userName = v.trim().take(40); sp.edit().putString("name", userName).apply() }
    fun markWelcomed() { welcomed = true; sp.edit().putBoolean("welcomed", true).apply() }
    fun updateTheme(v: Int) { themeMode = v; sp.edit().putInt("theme", v).apply() }
    fun updateClassReminders(v: Boolean) { classReminders = v; sp.edit().putBoolean("class_rem", v).apply() }
    fun updateClassLeads(v: Set<Int>) {
        classLeads = v
        sp.edit().putString("class_leads", v.sortedDescending().joinToString(",")).apply()
    }
    fun updateBudget(v: Long) { budget = v.coerceIn(0L, 999_999_999_999L); sp.edit().putLong("budget", budget).apply() }
    fun updateAiKey(v: String) {
        aiKey = v.trim()
        try {
            if (aiKey.isEmpty()) sp.edit().remove("ai_key_enc").apply()
            else sp.edit().putString("ai_key_enc", Crypto.encrypt(aiKey)).apply()
        } catch (_: Exception) {}
    }
    fun updateUse24h(v: Boolean) { use24h = v; sp.edit().putBoolean("use24h", v).apply() }
    fun updateShowSeconds(v: Boolean) { showSeconds = v; sp.edit().putBoolean("show_sec", v).apply() }
    fun updateStartTab(v: Int) { startTab = v; sp.edit().putInt("start_tab", v).apply() }
    fun updateFocusMin(v: Int) { focusMin = v.coerceIn(1, 600); sp.edit().putInt("focus_min", focusMin).apply() }
    fun updateBreakMin(v: Int) { breakMin = v.coerceIn(1, 120); sp.edit().putInt("break_min", breakMin).apply() }
    fun updatePhotoQuality(v: Int) { photoQuality = v; sp.edit().putInt("photo_q", v).apply() }
    fun updateFocusDnd(v: Boolean) { focusDnd = v; sp.edit().putBoolean("focus_dnd", v).apply() }

    fun updateLockEnabled(v: Boolean) { lockEnabled = v; sp.edit().putBoolean("lock_on", v).apply() }
    fun updateLockMethod(v: Int) { lockMethod = v; sp.edit().putInt("lock_method", v).apply() }
    fun updateBiometricOnPin(v: Boolean) { biometricOnPin = v; sp.edit().putBoolean("bio_on_pin", v).apply() }
    fun updateAutoLock(v: Int) { autoLockSec = v; sp.edit().putInt("auto_lock", v).apply() }
    fun updateSecureWindow(v: Boolean) { secureWindow = v; sp.edit().putBoolean("secure_win", v).apply() }
    fun updateHidePrivate(v: Boolean) { hidePrivate = v; sp.edit().putBoolean("hide_private", v).apply() }

    fun setPin(pin: String) {
        val salt = Crypto.randomBytes(16)
        sp.edit().putString("pin_salt", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .putString("pin_hash", Crypto.hashPin(pin, salt)).putInt("pin_fail", 0).putLong("pin_until", 0L).apply()
    }
    fun clearPin() { sp.edit().remove("pin_salt").remove("pin_hash").putInt("pin_fail", 0).putLong("pin_until", 0L).apply() }

    /** Mengembalikan 0 jika benar; selain itu sisa detik penguncian (>0) atau -1 jika salah tanpa penguncian. */
    fun checkPin(pin: String): Int {
        val now = System.currentTimeMillis()
        val until = sp.getLong("pin_until", 0L)
        if (until > now) return ((until - now) / 1000L + 1).toInt()
        val saltB = sp.getString("pin_salt", null) ?: return -1
        val hash = sp.getString("pin_hash", null) ?: return -1
        val ok = Crypto.constantTimeEquals(hash, Crypto.hashPin(pin, android.util.Base64.decode(saltB, android.util.Base64.NO_WRAP)))
        if (ok) { sp.edit().putInt("pin_fail", 0).putLong("pin_until", 0L).apply(); return 0 }
        val fails = sp.getInt("pin_fail", 0) + 1
        if (fails >= 5) {
            val step = (fails - 5).coerceAtMost(5)
            val lockMs = (30_000L shl step).coerceAtMost(15 * 60_000L)
            sp.edit().putInt("pin_fail", fails).putLong("pin_until", now + lockMs).apply()
            return (lockMs / 1000L).toInt()
        }
        sp.edit().putInt("pin_fail", fails).apply()
        return -1
    }
    fun pinLockRemaining(): Int {
        val d = sp.getLong("pin_until", 0L) - System.currentTimeMillis()
        return if (d > 0) (d / 1000L + 1).toInt() else 0
    }

    fun setTimer(state: Int, endAt: Long, remaining: Long, total: Long, label: String, isBreak: Boolean) {
        timerState = state; timerEndAt = endAt; timerRemaining = remaining
        timerTotal = total; timerLabel = label; timerBreak = isBreak
        sp.edit().putInt("t_state", state).putLong("t_end", endAt).putLong("t_rem", remaining)
            .putLong("t_total", total).putString("t_label", label).putBoolean("t_break", isBreak).apply()
    }

    private fun parseLeads(raw: String?): Set<Int> =
        (raw ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
}
