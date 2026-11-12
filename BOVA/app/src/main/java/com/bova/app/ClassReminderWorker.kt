package com.bova.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

internal data class ReminderClass(val day:Int,val hour:Int,val minute:Int,val title:String,val room:String)

internal object ClassReminderScheduler {
    private val schedule = listOf(
        ReminderClass(Calendar.MONDAY,10,20,"Praktikum Fisika Dasar","Lab Fisika TPB 3"),
        ReminderClass(Calendar.MONDAY,13,0,"Biologi Dasar","GKU1120 (60)"),
        ReminderClass(Calendar.TUESDAY,7,30,"Matriks dan Ruang Vektor","GK1.206B (60)"),
        ReminderClass(Calendar.TUESDAY,15,0,"Kimia Dasar","GK1.301 (80)"),
        ReminderClass(Calendar.WEDNESDAY,15,0,"Dasar Teknologi Digital","Lab MM TPB 2"),
        ReminderClass(Calendar.THURSDAY,8,0,"Transformasi Berkelanjutan Sumatera","GK1.106 (80)"),
        ReminderClass(Calendar.THURSDAY,13,0,"Matematika Dasar","GK1.120 (80)"),
        ReminderClass(Calendar.FRIDAY,10,20,"Fisika Dasar","GK1.119 (80)"),
        ReminderClass(Calendar.FRIDAY,15,0,"Pengantar Instrumentasi dan Automasi","GK1.206B (60)")
    )

    fun scheduleUpcoming(context: Context) {
        schedule.forEach { item -> listOf(30,15).forEach { lead -> enqueue(context,item,lead,nextStart(item)) } }
    }

    fun scheduleNextWeek(context:Context, day:Int, hour:Int, minute:Int, title:String, room:String, lead:Int) {
        val item=ReminderClass(day,hour,minute,title,room)
        val next=Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY,hour); set(Calendar.MINUTE,minute); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0)
            add(Calendar.DAY_OF_YEAR,7)
        }.timeInMillis
        enqueue(context,item,lead,next)
    }

    private fun nextStart(item:ReminderClass):Long {
        val now=Calendar.getInstance()
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY,item.hour); set(Calendar.MINUTE,item.minute); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0)
            val delta=(item.day-get(Calendar.DAY_OF_WEEK)+7)%7
            add(Calendar.DAY_OF_YEAR,delta)
            if(timeInMillis<=now.timeInMillis) add(Calendar.DAY_OF_YEAR,7)
        }.timeInMillis
    }

    private fun enqueue(context:Context,item:ReminderClass,lead:Int,startMillis:Long) {
        val fireAt=startMillis-TimeUnit.MINUTES.toMillis(lead.toLong())
        if (fireAt <= System.currentTimeMillis()) return
        val delay=fireAt-System.currentTimeMillis()
        val data=workDataOf(
            "title" to item.title, "room" to item.room, "day" to item.day,
            "hour" to item.hour, "minute" to item.minute, "lead" to lead
        )
        val request=OneTimeWorkRequestBuilder<ClassReminderWorker>()
            .setInitialDelay(delay,TimeUnit.MILLISECONDS).setInputData(data).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "class_${item.day}_${item.hour}_${item.minute}_${lead}_$startMillis",
            ExistingWorkPolicy.KEEP,request
        )
    }
}

class ClassReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val context=applicationContext
        val title=inputData.getString("title") ?: "Kelas berikutnya"
        val room=inputData.getString("room") ?: ""
        val lead=inputData.getInt("lead",30)
        val day=inputData.getInt("day",Calendar.MONDAY)
        val hour=inputData.getInt("hour",8)
        val minute=inputData.getInt("minute",0)
        val channelId="bova_class_reminders"
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId,"Pengingat kelas BOVA",NotificationManager.IMPORTANCE_HIGH))
        }
        if(Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) {
            val notification=NotificationCompat.Builder(context,channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("$title • $lead menit lagi")
                .setContentText("Siapkan diri menuju kelas. Ruangan: $room")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$title dimulai dalam $lead menit lagi.\nRuangan: $room"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true).build()
            NotificationManagerCompat.from(context).notify((title+lead+System.currentTimeMillis()).hashCode(),notification)
        }
        ClassReminderScheduler.scheduleNextWeek(context,day,hour,minute,title,room,lead)
        return Result.success()
    }
}
