package com.bova.app

import com.bova.app.data.ClassEntity
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

val ID: Locale = Locale("id", "ID")

val dayOrder = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
)
val dayLong = mapOf(
    Calendar.MONDAY to "Senin", Calendar.TUESDAY to "Selasa", Calendar.WEDNESDAY to "Rabu",
    Calendar.THURSDAY to "Kamis", Calendar.FRIDAY to "Jumat", Calendar.SATURDAY to "Sabtu", Calendar.SUNDAY to "Minggu"
)
val dayShort = mapOf(
    Calendar.MONDAY to "Sen", Calendar.TUESDAY to "Sel", Calendar.WEDNESDAY to "Rab",
    Calendar.THURSDAY to "Kam", Calendar.FRIDAY to "Jum", Calendar.SATURDAY to "Sab", Calendar.SUNDAY to "Min"
)

fun rupiah(n: Long): String {
    val sym = DecimalFormatSymbols(ID)
    sym.groupingSeparator = '.'
    val abs = if (n < 0) -n else n
    return (if (n < 0) "-" else "") + "Rp" + DecimalFormat("#,###", sym).format(abs)
}

fun hm(min: Int): String = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

fun fmtDateTime(t: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", ID).format(Date(t))
fun fmtDateShort(t: Long): String = SimpleDateFormat("d MMM, HH:mm", ID).format(Date(t))
fun fmtDayHeader(t: Long): String = SimpleDateFormat("EEEE, d MMM", ID).format(Date(t))
fun fmtFullDate(t: Long): String = SimpleDateFormat("EEEE, d MMMM yyyy", ID).format(Date(t))
fun fmtClock(t: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(t))
fun fmtSeconds(t: Long): String = SimpleDateFormat("ss", Locale.US).format(Date(t))

fun greeting(hour: Int): String = when {
    hour < 11 -> "Selamat pagi"
    hour < 15 -> "Selamat siang"
    hour < 18 -> "Selamat sore"
    else -> "Selamat malam"
}

fun durationLabel(min: Int): String =
    if (min >= 60) (if (min % 60 == 0) "${min / 60} jam" else "${min / 60}j ${min % 60}m") else "$min mnt"

fun countdown(ms: Long): String {
    val total = ms / 1000
    val days = total / 86400
    val hours = (total % 86400) / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (days > 0) String.format(Locale.US, "%d hari %02d:%02d:%02d", days, hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

/** Waktu mulai kelas berikutnya yang STRICTLY setelah [after]. */
fun nextStart(c: ClassEntity, after: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = after
        set(Calendar.HOUR_OF_DAY, c.startMin / 60)
        set(Calendar.MINUTE, c.startMin % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val delta = (c.day - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
    cal.add(Calendar.DAY_OF_YEAR, delta)
    if (cal.timeInMillis <= after) cal.add(Calendar.DAY_OF_YEAR, 7)
    return cal.timeInMillis
}

fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** [start, end) untuk bulan ke-[ym] dengan ym = tahun*12 + bulan(0-11). */
fun monthRange(ym: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
        clear()
        set(ym / 12, ym % 12, 1, 0, 0, 0)
    }
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    return start to cal.timeInMillis
}

fun monthLabel(ym: Int): String {
    val cal = Calendar.getInstance().apply { clear(); set(ym / 12, ym % 12, 1) }
    return SimpleDateFormat("MMMM yyyy", ID).format(cal.time)
}

fun currentYm(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
}
