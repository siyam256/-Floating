package com.example.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_links")
data class SavedLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SavedLinkDao {
    @Query("SELECT * FROM saved_links ORDER BY timestamp DESC")
    fun getAllLinks(): Flow<List<SavedLink>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: SavedLink)

    @Query("DELETE FROM saved_links WHERE id = :id")
    suspend fun deleteLink(id: Long)

    @Query("DELETE FROM saved_links")
    suspend fun deleteAllLinks()
}

@Database(entities = [SavedLink::class], version = 1, exportSchema = false)
abstract class SavedLinkDatabase : RoomDatabase() {
    abstract fun savedLinkDao(): SavedLinkDao

    companion object {
        @Volatile
        private var INSTANCE: SavedLinkDatabase? = null

        fun getDatabase(context: Context): SavedLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SavedLinkDatabase::class.java,
                    "saved_links_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class SavedLinkRepository(private val savedLinkDao: SavedLinkDao) {
    val allLinks: Flow<List<SavedLink>> = savedLinkDao.getAllLinks()

    suspend fun insert(link: SavedLink) {
        savedLinkDao.insertLink(link)
    }

    suspend fun delete(id: Long) {
        savedLinkDao.deleteLink(id)
    }

    suspend fun deleteAll() {
        savedLinkDao.deleteAllLinks()
    }
}
