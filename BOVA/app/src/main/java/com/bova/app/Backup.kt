package com.bova.app

import androidx.room.withTransaction
import com.bova.app.data.*
import org.json.JSONArray
import org.json.JSONObject

/** Ekspor/impor seluruh database offline ke satu berkas JSON. */
object Backup {
    suspend fun export(db: BovaDatabase): String {
        val dao = db.dao()
        val root = JSONObject()
        root.put("app", "BOVA").put("version", 4).put("exportedAt", System.currentTimeMillis())
        root.put("notes", JSONArray().also { a ->
            dao.allNotes().forEach { n ->
                a.put(JSONObject()
                    .put("id", n.id).put("title", n.title).put("body", n.body).put("createdAt", n.createdAt)
                    .put("imageUri", n.imageUri ?: JSONObject.NULL).put("isPinned", n.isPinned)
                    .put("color", n.color).put("isChecklist", n.isChecklist)
                    .put("reminderAt", n.reminderAt ?: JSONObject.NULL)
                    .put("updatedAt", n.updatedAt).put("tags", n.tags))
            }
        })
        root.put("transactions", JSONArray().also { a ->
            dao.allTransactions().forEach { t ->
                a.put(JSONObject().put("id", t.id).put("title", t.title).put("amount", t.amount)
                    .put("type", t.type).put("category", t.category).put("createdAt", t.createdAt))
            }
        })
        root.put("classes", JSONArray().also { a ->
            dao.allClasses().forEach { c ->
                a.put(JSONObject().put("id", c.id).put("title", c.title).put("room", c.room)
                    .put("day", c.day).put("startMin", c.startMin).put("endMin", c.endMin)
                    .put("code", c.code).put("sks", c.sks))
            }
        })
        root.put("sessions", JSONArray().also { a ->
            dao.allSessions().forEach { s ->
                a.put(JSONObject().put("id", s.id).put("label", s.label).put("minutes", s.minutes).put("finishedAt", s.finishedAt))
            }
        })
        root.put("tasks", JSONArray().also { a ->
            dao.allTasks().forEach { t ->
                a.put(JSONObject().put("id", t.id).put("title", t.title).put("detail", t.detail).put("subject", t.subject)
                    .put("deadline", t.deadline ?: JSONObject.NULL).put("priority", t.priority).put("done", t.done)
                    .put("doneAt", t.doneAt ?: JSONObject.NULL).put("remindMin", t.remindMin).put("createdAt", t.createdAt))
            }
        })
        root.put("budgets", JSONArray().also { a ->
            dao.allBudgets().forEach { b -> a.put(JSONObject().put("category", b.category).put("amount", b.amount)) }
        })
        return root.toString(2)
    }

    /** Mengganti seluruh isi database dengan isi backup. Mengembalikan jumlah baris yang dipulihkan. */
    suspend fun restore(db: BovaDatabase, json: String): Int {
        val root = JSONObject(json)
        if (root.optString("app") != "BOVA") error("Berkas ini bukan backup BOVA.")
        val dao = db.dao()
        val notes = root.optJSONArray("notes") ?: JSONArray()
        val tx = root.optJSONArray("transactions") ?: JSONArray()
        val classes = root.optJSONArray("classes") ?: JSONArray()
        val sessions = root.optJSONArray("sessions") ?: JSONArray()
        val tasks = root.optJSONArray("tasks") ?: JSONArray()
        val budgets = root.optJSONArray("budgets") ?: JSONArray()
        var count = 0
        db.withTransaction {
            dao.clearNotes(); dao.clearTransactions(); dao.clearClasses(); dao.clearSessions(); dao.clearTasks(); dao.clearBudgets()
            for (i in 0 until notes.length()) {
                val o = notes.getJSONObject(i)
                dao.upsertNote(Note(
                    id = o.getLong("id"), title = o.optString("title"), body = o.optString("body"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    imageUri = if (o.isNull("imageUri")) null else o.optString("imageUri"),
                    isPinned = o.optBoolean("isPinned"), color = o.optInt("color"),
                    isChecklist = o.optBoolean("isChecklist"),
                    reminderAt = if (o.isNull("reminderAt")) null else o.optLong("reminderAt"),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()), tags = o.optString("tags")
                )); count++
            }
            for (i in 0 until tx.length()) {
                val o = tx.getJSONObject(i)
                dao.upsertTransaction(MoneyTransaction(o.getLong("id"), o.optString("title"), o.getLong("amount"),
                    o.optString("type", "EXPENSE"), o.optString("category", "Lainnya"), o.optLong("createdAt"))); count++
            }
            for (i in 0 until classes.length()) {
                val o = classes.getJSONObject(i)
                dao.upsertClass(ClassEntity(o.getLong("id"), o.optString("title"), o.optString("room"),
                    o.getInt("day"), o.getInt("startMin"), o.getInt("endMin"), o.optString("code"), o.optInt("sks"))); count++
            }
            for (i in 0 until sessions.length()) {
                val o = sessions.getJSONObject(i)
                dao.upsertSession(StudySession(o.getLong("id"), o.optString("label"), o.getInt("minutes"), o.getLong("finishedAt"))); count++
            }
            for (i in 0 until tasks.length()) {
                val o = tasks.getJSONObject(i)
                dao.upsertTask(TaskItem(o.getLong("id"), o.optString("title"), o.optString("detail"), o.optString("subject"),
                    if (o.isNull("deadline")) null else o.optLong("deadline"), o.optInt("priority", 1), o.optBoolean("done"),
                    if (o.isNull("doneAt")) null else o.optLong("doneAt"), o.optInt("remindMin"), o.optLong("createdAt"))); count++
            }
            for (i in 0 until budgets.length()) {
                val o = budgets.getJSONObject(i)
                dao.upsertBudget(CategoryBudget(o.getString("category"), o.getLong("amount"))); count++
            }
        }
        return count
    }
}

object DefaultData {
    private fun c(title: String, room: String, day: Int, sh: Int, sm: Int, eh: Int, em: Int, code: String, sks: Int) =
        ClassEntity(0, title, room, day, sh * 60 + sm, eh * 60 + em, code, sks)
    val classes = listOf(
        c("Praktikum Fisika Dasar", "Lab Fisika TPB 3 • Labtek 1 Lt. 2", java.util.Calendar.MONDAY, 10, 20, 12, 0, "WT25-00003", 1),
        c("Biologi Dasar", "GKU1120 (60)", java.util.Calendar.MONDAY, 13, 0, 14, 40, "WT25-00002", 2),
        c("Matriks dan Ruang Vektor", "GK1.206B (60)", java.util.Calendar.TUESDAY, 7, 30, 9, 10, "IA25-11002", 2),
        c("Kimia Dasar", "GK1.301 (80)", java.util.Calendar.TUESDAY, 15, 0, 16, 40, "WT25-00004", 2),
        c("Dasar Teknologi Digital", "Lab MM TPB 2 • Labtek 3 Lt. 3", java.util.Calendar.WEDNESDAY, 15, 0, 16, 40, "WI25-00002", 2),
        c("Transformasi Berkelanjutan Sumatera", "GK1.106 (80)", java.util.Calendar.THURSDAY, 8, 0, 9, 40, "WI25-00001", 2),
        c("Matematika Dasar", "GK1.120 (80)", java.util.Calendar.THURSDAY, 13, 0, 15, 30, "WT25-00001", 3),
        c("Fisika Dasar", "GK1.119 (80)", java.util.Calendar.FRIDAY, 10, 20, 12, 0, "WT25-00003", 2),
        c("Pengantar Instrumentasi dan Automasi", "GK1.206B (60)", java.util.Calendar.FRIDAY, 15, 0, 16, 40, "IA25-11001", 2)
    )
}
