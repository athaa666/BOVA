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
    val isPinned: Boolean = false
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

@Dao
interface BovaDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, createdAt DESC")
    fun observeNotes(): Flow<List<Note>>
    @Insert suspend fun insertNote(note: Note)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteNote(id: Long)
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeTransactions(): Flow<List<MoneyTransaction>>
    @Insert suspend fun insertTransaction(item: MoneyTransaction)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTransaction(id: Long)
}

@Database(entities = [Note::class, MoneyTransaction::class], version = 2, exportSchema = false)
abstract class BovaDatabase : RoomDatabase() {
    abstract fun dao(): BovaDao
    companion object {
        @Volatile private var INSTANCE: BovaDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN imageUri TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }
        fun get(context: Context): BovaDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext,BovaDatabase::class.java,"bova_local.db")
                .addMigrations(MIGRATION_1_2).build().also { INSTANCE=it }
        }
    }
}
