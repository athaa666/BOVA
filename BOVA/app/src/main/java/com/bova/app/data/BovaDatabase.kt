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
    val endMin: Int
)

@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val minutes: Int,
    val finishedAt: Long
)

@Dao
interface BovaDao {
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
    entities = [Note::class, MoneyTransaction::class, ClassEntity::class, StudySession::class],
    version = 3,
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

        fun get(context: Context): BovaDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, BovaDatabase::class.java, "bova_local.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { INSTANCE = it }
        }
    }
}
