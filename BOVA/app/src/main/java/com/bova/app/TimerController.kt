package com.bova.app

import android.content.Context
import com.bova.app.data.BovaDao
import com.bova.app.data.StudySession

/** Logika timer belajar. State disimpan di [AppSettings] supaya tetap jalan saat aplikasi ditutup. */
object TimerController {
    fun start(ctx: Context, s: AppSettings, minutes: Int, label: String, isBreak: Boolean) {
        val total = minutes * 60_000L
        val end = System.currentTimeMillis() + total
        val name = label.ifBlank { if (isBreak) "Istirahat" else "Belajar" }
        s.setTimer(1, end, total, total, name, isBreak)
        if (!isBreak) Dnd.enable(ctx, s)
        Alarms.schedule(ctx, Alarms.TIMER_CODE, end, "study",
            if (isBreak) "Istirahat selesai" else "Sesi belajar selesai", name)
    }

    fun pause(ctx: Context, s: AppSettings) {
        if (s.timerState != 1) return
        val rem = (s.timerEndAt - System.currentTimeMillis()).coerceAtLeast(0L)
        Alarms.cancel(ctx, Alarms.TIMER_CODE)
        Dnd.restore(ctx, s)
        s.setTimer(2, 0L, rem, s.timerTotal, s.timerLabel, s.timerBreak)
    }

    fun resume(ctx: Context, s: AppSettings) {
        if (s.timerState != 2) return
        val end = System.currentTimeMillis() + s.timerRemaining
        s.setTimer(1, end, 0L, s.timerTotal, s.timerLabel, s.timerBreak)
        if (!s.timerBreak) Dnd.enable(ctx, s)
        Alarms.schedule(ctx, Alarms.TIMER_CODE, end, "study",
            if (s.timerBreak) "Istirahat selesai" else "Sesi belajar selesai", s.timerLabel)
    }

    /** Hentikan sesi. Jika sesi fokus sudah berjalan >= 1 menit, waktunya tetap dicatat. */
    suspend fun stop(ctx: Context, s: AppSettings, dao: BovaDao) {
        val remaining = when (s.timerState) {
            1 -> (s.timerEndAt - System.currentTimeMillis()).coerceAtLeast(0L)
            2 -> s.timerRemaining
            else -> return
        }
        val elapsedMin = ((s.timerTotal - remaining) / 60_000L).toInt()
        val wasBreak = s.timerBreak
        val label = s.timerLabel
        Alarms.cancel(ctx, Alarms.TIMER_CODE)
        Dnd.restore(ctx, s)
        s.setTimer(0, 0L, 0L, 0L, label, wasBreak)
        if (!wasBreak && elapsedMin >= 1) {
            dao.upsertSession(StudySession(label = label, minutes = elapsedMin, finishedAt = System.currentTimeMillis()))
        }
    }

    /** Dipanggil tiap detik dari root UI; mencatat sesi bila waktu habis. */
    suspend fun checkDone(ctx: Context, s: AppSettings, dao: BovaDao) {
        if (s.timerState != 1) return
        if (System.currentTimeMillis() < s.timerEndAt) return
        val total = s.timerTotal
        val label = s.timerLabel
        val wasBreak = s.timerBreak
        val end = s.timerEndAt
        Dnd.restore(ctx, s)
        s.setTimer(0, 0L, 0L, 0L, label, wasBreak)
        if (!wasBreak) {
            dao.upsertSession(StudySession(label = label, minutes = (total / 60_000L).toInt().coerceAtLeast(1), finishedAt = end))
        }
    }
}
