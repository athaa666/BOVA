package com.bova.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
    val imageUri: String? = null,
    val isPinned: Boolean = false,
    val color: Int = 0,
    val isChecklist: Boolean = false,
    val reminderAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val tags: String = ""
)

@Entity(tableName = "transactions")
data class MoneyTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val type: String,
    val category: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "classes")
data class ClassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val room: String,
    val day: Int,        // java.util.Calendar.MONDAY ... SUNDAY
    val startMin: Int,   // menit sejak 00:00
    val endMin: Int,
    val code: String = "",
    val sks: Int = 0
)

@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val minutes: Int,
    val finishedAt: Long
)

@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val detail: String = "",
    val subject: String = "",
    val deadline: Long? = null,
    val priority: Int = 1,      // 0 rendah, 1 sedang, 2 tinggi
    val done: Boolean = false,
    val doneAt: Long? = null,
    val remindMin: Int = 0,     // menit sebelum deadline, 0 = tanpa pengingat
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "category_budgets")
data class CategoryBudget(
    @PrimaryKey val category: String,
    val amount: Long
)

@Dao
interface BovaDao {
    // Tugas
    @Query("SELECT * FROM tasks ORDER BY done, deadline IS NULL, deadline, priority DESC")
    fun observeTasks(): Flow<List<TaskItem>>
    @Query("SELECT * FROM tasks") suspend fun allTasks(): List<TaskItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTask(item: TaskItem): Long
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteTask(id: Long)
    @Query("DELETE FROM tasks") suspend fun clearTasks()

    // Anggaran per kategori
    @Query("SELECT * FROM category_budgets")
    fun observeBudgets(): Flow<List<CategoryBudget>>
    @Query("SELECT * FROM category_budgets") suspend fun allBudgets(): List<CategoryBudget>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBudget(item: CategoryBudget)
    @Query("DELETE FROM category_budgets WHERE category = :category") suspend fun deleteBudget(category: String)
    @Query("DELETE FROM category_budgets") suspend fun clearBudgets()

    // Catatan
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun observeNotes(): Flow<List<Note>>
    @Query("SELECT * FROM notes") suspend fun allNotes(): List<Note>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertNote(note: Note): Long
    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteNote(id: Long)
    @Query("DELETE FROM notes WHERE title = '' AND body = '' AND imageUri IS NULL") suspend fun deleteBlankNotes()
    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL AND reminderAt > :now")
    suspend fun upcomingReminders(now: Long): List<Note>
    @Query("DELETE FROM notes") suspend fun clearNotes()

    // Keuangan
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeTransactions(): Flow<List<MoneyTransaction>>
    @Query("SELECT * FROM transactions") suspend fun allTransactions(): List<MoneyTransaction>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTransaction(item: MoneyTransaction): Long
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTransaction(id: Long)
    @Query("DELETE FROM transactions") suspend fun clearTransactions()

    // Jadwal
    @Query("SELECT * FROM classes ORDER BY day, startMin")
    fun observeClasses(): Flow<List<ClassEntity>>
    @Query("SELECT * FROM classes") suspend fun allClasses(): List<ClassEntity>
    @Query("SELECT * FROM classes WHERE id = :id") suspend fun getClass(id: Long): ClassEntity?
    @Query("SELECT COUNT(*) FROM classes") suspend fun countClasses(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertClass(item: ClassEntity): Long
    @Query("DELETE FROM classes WHERE id = :id") suspend fun deleteClass(id: Long)
    @Query("DELETE FROM classes") suspend fun clearClasses()

    // Belajar
    @Query("SELECT * FROM study_sessions ORDER BY finishedAt DESC")
    fun observeSessions(): Flow<List<StudySession>>
    @Query("SELECT * FROM study_sessions") suspend fun allSessions(): List<StudySession>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(item: StudySession): Long
    @Query("DELETE FROM study_sessions") suspend fun clearSessions()
}

@Database(
    entities = [Note::class, MoneyTransaction::class, ClassEntity::class, StudySession::class, TaskItem::class, CategoryBudget::class],
    version = 4,
    exportSchema = false
)
abstract class BovaDatabase : RoomDatabase() {
    abstract fun dao(): BovaDao

    companion object {
        @Volatile private var INSTANCE: BovaDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN imageUri TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN isChecklist INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN reminderAt INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE notes SET updatedAt = createdAt")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS classes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, room TEXT NOT NULL, day INTEGER NOT NULL, " +
                        "startMin INTEGER NOT NULL, endMin INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS study_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "label TEXT NOT NULL, minutes INTEGER NOT NULL, finishedAt INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE classes ADD COLUMN code TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE classes ADD COLUMN sks INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, detail TEXT NOT NULL, subject TEXT NOT NULL, deadline INTEGER, " +
                        "priority INTEGER NOT NULL, done INTEGER NOT NULL, doneAt INTEGER, " +
                        "remindMin INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS category_budgets (category TEXT NOT NULL, " +
                        "amount INTEGER NOT NULL, PRIMARY KEY(category))"
                )
            }
        }

        fun get(context: Context): BovaDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, BovaDatabase::class.java, "bova_local.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { INSTANCE = it }
        }
    }
}
