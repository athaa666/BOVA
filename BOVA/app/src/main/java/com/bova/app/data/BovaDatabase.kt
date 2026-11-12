package com.bova.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class MoneyTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val type: String, // INCOME or EXPENSE
    val category: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface BovaDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeNotes(): Flow<List<Note>>

    @Insert
    suspend fun insertNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeTransactions(): Flow<List<MoneyTransaction>>

    @Insert
    suspend fun insertTransaction(item: MoneyTransaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)
}

@Database(entities = [Note::class, MoneyTransaction::class], version = 1, exportSchema = false)
abstract class BovaDatabase : RoomDatabase() {
    abstract fun dao(): BovaDao

    companion object {
        @Volatile private var INSTANCE: BovaDatabase? = null
        fun get(context: Context): BovaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BovaDatabase::class.java,
                    "bova_local.db"
                ).build().also { INSTANCE = it }
            }
    }
}
