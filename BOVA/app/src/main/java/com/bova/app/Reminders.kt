package com.bova.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.bova.app.data.BovaDatabase
import com.bova.app.data.ClassEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object Notifier {
    const val CH_NOTES = "bova_notes"
    const val CH_CLASS = "bova_class_reminders"
    const val CH_STUDY = "bova_study"
    const val CH_TASK = "bova_tasks"
    const val CH_BUDGET = "bova_budget"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val m = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            m.createNotificationChannel(NotificationChannel(CH_NOTES, "Pengingat catatan", NotificationManager.IMPORTANCE_HIGH))
            m.createNotificationChannel(NotificationChannel(CH_CLASS, "Pengingat kelas", NotificationManager.IMPORTANCE_HIGH))
            m.createNotificationChannel(NotificationChannel(CH_STUDY, "Timer belajar", NotificationManager.IMPORTANCE_HIGH))
            m.createNotificationChannel(NotificationChannel(CH_TASK, "Tugas & deadline", NotificationManager.IMPORTANCE_HIGH))
            m.createNotificationChannel(NotificationChannel(CH_BUDGET, "Anggaran", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, channel: String, id: Int, title: String, text: String) {
        if (!canNotify(ctx)) return
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_bova)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply {
                if (AppSettings(ctx).hidePrivate) {
                    setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    setPublicVersion(
                        NotificationCompat.Builder(ctx, channel)
                            .setSmallIcon(R.drawable.ic_stat_bova)
                            .setContentTitle("BOVA")
                            .setContentText("Ada pengingat untukmu")
                            .build()
                    )
                }
            }
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}

object Alarms {
    const val TIMER_CODE = 1_000_000_001
    const val TASK_BASE = 500_000_000
    fun taskCode(id: Long): Int = TASK_BASE + id.toInt()

    private fun intentFor(ctx: Context, code: Int, kind: String, title: String, text: String): Intent =
        Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra("kind", kind); putExtra("title", title); putExtra("text", text); putExtra("rc", code)
        }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun schedule(ctx: Context, code: Int, atMillis: Long, kind: String, title: String, text: String) {
        if (atMillis <= System.currentTimeMillis()) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, code, intentFor(ctx, code, kind, title, text),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    fun cancel(ctx: Context, code: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) { am.cancel(pi); pi.cancel() }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: "note"
        val title = intent.getStringExtra("title") ?: "BOVA"
        val text = intent.getStringExtra("text") ?: ""
        val code = intent.getIntExtra("rc", 0)
        val channel = when (kind) { "study" -> Notifier.CH_STUDY; "task" -> Notifier.CH_TASK; else -> Notifier.CH_NOTES }
        if (kind == "study") Dnd.restore(context, AppSettings(context))
        Notifier.show(context, channel, code, title, text)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Reschedule.all(context.applicationContext) } finally { pending.finish() }
        }
    }
}

object Reschedule {
    /** Pasang ulang semua alarm: pengingat catatan, timer belajar, dan pengingat kelas. */
    suspend fun all(ctx: Context) {
        val db = BovaDatabase.get(ctx)
        val now = System.currentTimeMillis()
        db.dao().upcomingReminders(now).forEach { n ->
            val label = n.title.ifBlank { n.body.lineSequence().firstOrNull { it.isNotBlank() } ?: "Catatan" }
            Alarms.schedule(ctx, n.id.toInt(), n.reminderAt ?: 0L, "note", "Pengingat: $label", n.body.take(120))
        }
        db.dao().allTasks().forEach { t -> scheduleTask(ctx, t) }
        val s = AppSettings(ctx)
        if (s.timerState == 1 && s.timerEndAt > now) {
            Alarms.schedule(ctx, Alarms.TIMER_CODE, s.timerEndAt, "study",
                if (s.timerBreak) "Istirahat selesai" else "Sesi belajar selesai", s.timerLabel)
        }
        ClassReminderScheduler.rescheduleAll(ctx)
    }
}

fun scheduleTask(ctx: Context, t: com.bova.app.data.TaskItem) {
    val code = Alarms.taskCode(t.id)
    Alarms.cancel(ctx, code)
    val dl = t.deadline ?: return
    if (t.done || t.remindMin <= 0) return
    Alarms.schedule(ctx, code, dl - t.remindMin * 60_000L, "task", "Deadline: ${t.title}",
        "Tenggat ${fmtDateTime(dl)}" + (if (t.subject.isNotBlank()) " • ${t.subject}" else ""))
}

object ClassReminderScheduler {
    private const val TAG = "bova_class"

    suspend fun rescheduleAll(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelAllWorkByTag(TAG)
        val settings = AppSettings(ctx)
        if (!settings.classReminders) return
        val classes = BovaDatabase.get(ctx).dao().allClasses()
        val now = System.currentTimeMillis()
        classes.forEach { c ->
            settings.classLeads.forEach { lead -> enqueue(ctx, c.id, lead, nextStart(c, now + lead * 60_000L)) }
        }
    }

    fun enqueue(ctx: Context, classId: Long, lead: Int, startMillis: Long) {
        val fireAt = startMillis - TimeUnit.MINUTES.toMillis(lead.toLong())
        val delay = fireAt - System.currentTimeMillis()
        if (delay <= 0) return
        val data = workDataOf("classId" to classId, "lead" to lead, "start" to startMillis)
        val req = OneTimeWorkRequestBuilder<ClassReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "class_${classId}_${lead}_$startMillis", ExistingWorkPolicy.KEEP, req
        )
    }
}

class ClassReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val id = inputData.getLong("classId", -1L)
        val lead = inputData.getInt("lead", 30)
        val c: ClassEntity = BovaDatabase.get(ctx).dao().getClass(id) ?: return Result.success()
        val settings = AppSettings(ctx)
        if (!settings.classReminders) return Result.success()
        val room = if (c.room.isBlank()) "" else "\nRuangan: ${c.room}"
        Notifier.show(
            ctx, Notifier.CH_CLASS, (id.toInt() * 100 + lead),
            "${c.title} • $lead menit lagi",
            "${c.title} dimulai ${hm(c.startMin)}.$room"
        )
        // jadwalkan pekan berikutnya
        val next = nextStart(c, System.currentTimeMillis() + lead * 60_000L + 5 * 60_000L)
        ClassReminderScheduler.enqueue(ctx, id, lead, next)
        return Result.success()
    }
}
