package com.bova.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

data class SmartResult(
    val title: String,
    val tags: List<String>,
    val reminderAt: Long?,
    val amount: Long?,
    val summary: String,
    val wordCount: Int,
    val canChecklist: Boolean
)

/** Analisis catatan secara lokal (offline) — tanpa internet dan tanpa akun. */
object Smart {
    private val tagRules = listOf(
        "Tugas" to listOf("tugas", "deadline", "kumpul", "laporan", "makalah"),
        "Ujian" to listOf("uts", "uas", "kuis", "ujian", "quiz"),
        "Praktikum" to listOf("praktikum", "modul", "lab "),
        "Keuangan" to listOf("bayar", "uang", "rp", "beli", "ukt", "kos", "tabungan"),
        "Rapat" to listOf("rapat", "meeting", "organisasi", "himpunan"),
        "Ide" to listOf("ide", "proyek", "project"),
        "Belajar" to listOf("belajar", "materi", "rumus", "rangkuman")
    )
    private val stop = setOf(
        "yang", "dan", "untuk", "dengan", "dari", "pada", "ini", "itu", "atau", "juga", "akan", "agar",
        "dalam", "adalah", "karena", "saya", "kita", "kamu", "nanti", "sudah", "belum", "tidak"
    )

    fun cleanLines(body: String): List<String> =
        body.lines().map { it.removePrefix("[x] ").removePrefix("[ ] ").trim() }.filter { it.isNotBlank() }

    fun analyze(title: String, body: String, now: Long = System.currentTimeMillis()): SmartResult {
        val lines = cleanLines(body)
        val text = (title + "\n" + lines.joinToString("\n"))
        val lower = text.lowercase(ID)

        val suggestedTitle = if (title.isNotBlank()) title.trim()
        else (lines.firstOrNull() ?: "").trim().trimEnd('.', ':', ',').take(40)

        val tags = tagRules.filter { (_, keys) -> keys.any { lower.contains(it) } }.map { it.first }
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        val bullets = lines.count { it.startsWith("-") || it.startsWith("*") || it.startsWith("•") || Regex("^\\d+[.)]\\s").containsMatchIn(it) }
        val canChecklist = lines.size >= 2

        return SmartResult(
            title = suggestedTitle,
            tags = tags,
            reminderAt = detectDateTime(text, now),
            amount = detectAmount(text),
            summary = extractiveSummary(lines.joinToString(". ")),
            wordCount = words,
            canChecklist = canChecklist || bullets > 0
        )
    }

    fun extractiveSummary(text: String): String {
        val sentences = text.split(Regex("(?<=[.!?])\\s+|\\n")).map { it.trim() }.filter { it.length > 3 }
        if (sentences.size <= 2) return text.trim().take(220)
        val freq = HashMap<String, Int>()
        Regex("[a-zA-Z]{4,}").findAll(text.lowercase(ID)).forEach { m ->
            if (m.value !in stop) freq[m.value] = (freq[m.value] ?: 0) + 1
        }
        val scored = sentences.mapIndexed { i, s ->
            val score = Regex("[a-zA-Z]{4,}").findAll(s.lowercase(ID)).sumOf { freq[it.value] ?: 0 }
            Triple(i, s, score.toDouble() / (s.length.coerceAtLeast(20)))
        }
        val top = scored.sortedByDescending { it.third }.take(2).sortedBy { it.first }
        return top.joinToString(" ") { it.second.trimEnd('.') + "." }.take(260)
    }

    fun detectAmount(text: String): Long? {
        val re = Regex("(?i)(?:\\brp\\.?\\s*([\\d.,]+))|(?:\\b(\\d+(?:[.,]\\d+)?)\\s*(rb|ribu|k|jt|juta)\\b)")
        val m = re.find(text) ?: return null
        if (m.groupValues[1].isNotEmpty()) {
            return m.groupValues[1].filter { it.isDigit() }.toLongOrNull()?.takeIf { it > 0 }
        }
        val num = m.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
        val mult = when (m.groupValues[3].lowercase()) { "rb", "ribu", "k" -> 1000.0; else -> 1_000_000.0 }
        return (num * mult).toLong().takeIf { it > 0 }
    }

    /** Mengenali "besok jam 10", "senin 08.30", "14/11", "tgl 20", dll. Mengembalikan waktu di masa depan atau null. */
    fun detectDateTime(text: String, now: Long): Long? {
        val t = text.lowercase(ID)
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        var dateFound = false
        var dayName = false

        if (Regex("\\blusa\\b").containsMatchIn(t)) { cal.add(Calendar.DAY_OF_YEAR, 2); dateFound = true }
        else if (Regex("\\bbesok\\b").containsMatchIn(t)) { cal.add(Calendar.DAY_OF_YEAR, 1); dateFound = true }
        else if (Regex("\\bhari ini\\b").containsMatchIn(t)) { dateFound = true }
        else {
            val names = mapOf(
                "senin" to Calendar.MONDAY, "selasa" to Calendar.TUESDAY, "rabu" to Calendar.WEDNESDAY,
                "kamis" to Calendar.THURSDAY, "jumat" to Calendar.FRIDAY, "sabtu" to Calendar.SATURDAY
            )
            val hit = names.entries.firstOrNull { Regex("\\b${it.key}\\b").containsMatchIn(t) }
            if (hit != null) {
                val delta = (hit.value - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                cal.add(Calendar.DAY_OF_YEAR, delta)
                dateFound = true; dayName = true
            } else {
                val dm = Regex("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b").find(t)
                val tg = Regex("\\b(?:tgl|tanggal)\\.?\\s*(\\d{1,2})\\b").find(t)
                if (dm != null) {
                    val d = dm.groupValues[1].toInt(); val mo = dm.groupValues[2].toInt()
                    if (d in 1..31 && mo in 1..12) {
                        val yStr = dm.groupValues[3]
                        var y = if (yStr.isNotEmpty()) yStr.toInt().let { if (it < 100) it + 2000 else it } else cal.get(Calendar.YEAR)
                        cal.set(y, mo - 1, d)
                        if (yStr.isEmpty() && cal.timeInMillis < now - 86_400_000L) { y += 1; cal.set(y, mo - 1, d) }
                        dateFound = true
                    }
                } else if (tg != null) {
                    val d = tg.groupValues[1].toInt()
                    if (d in 1..31) {
                        cal.set(Calendar.DAY_OF_MONTH, d)
                        if (cal.timeInMillis < now - 86_400_000L) cal.add(Calendar.MONTH, 1)
                        dateFound = true
                    }
                }
            }
        }

        val tm = Regex("(?:jam|pukul|pkl)\\.?\\s*(\\d{1,2})(?:[.:](\\d{2}))?").find(t)
            ?: Regex("\\b(\\d{1,2})[:.](\\d{2})\\b").find(t)
        if (!dateFound && tm == null) return null

        var hour = 8; var minute = 0
        if (tm != null) {
            hour = tm.groupValues[1].toInt()
            minute = tm.groupValues[2].toIntOrNull() ?: 0
            if ((t.contains("malam") || t.contains("sore")) && hour < 12) hour += 12
            if (t.contains("siang") && hour in 1..10) hour += 12
            if (hour > 23 || minute > 59) return null
        }
        cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)

        if (!dateFound && cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        if (cal.timeInMillis <= now && dayName) cal.add(Calendar.DAY_OF_YEAR, 7)
        return if (cal.timeInMillis > now) cal.timeInMillis else null
    }
}

/** Ringkasan online lewat Claude API — memakai API key milik pengguna sendiri. Opsional. */
object AiClient {
    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun summarize(key: String, title: String, body: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = "Ringkas catatan kuliah berikut dalam Bahasa Indonesia, maksimal 4 poin singkat. " +
                "Jika ada tenggat atau tugas, sebutkan di akhir.\n\nJudul: $title\n\n$body"
            val payload = JSONObject()
                .put("model", "claude-haiku-5-5")
                .put("max_tokens", 500)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = 40_000
                conn.doOutput = true
                conn.setRequestProperty("content-type", "application/json")
                conn.setRequestProperty("x-api-key", key)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) error("Gagal ($code). Periksa API key di Pengaturan.")
                val arr = JSONObject(raw).getJSONArray("content")
                val sb = StringBuilder()
                for (i in 0 until arr.length()) {
                    val b = arr.getJSONObject(i)
                    if (b.optString("type") == "text") sb.append(b.optString("text"))
                }
                sb.toString().trim().ifEmpty { error("Jawaban kosong.") }
            } finally {
                conn.disconnect()
            }
        }
    }
}
