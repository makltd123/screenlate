package com.screenlate.app.cache

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity(tableName = "translations")
data class CachedTranslation(
    @PrimaryKey val source: String,
    val translation: String,
    val bytes: Int,
    @ColumnInfo(name = "last_used") val lastUsed: Long,
)

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations WHERE source IN (:sources)")
    suspend fun find(sources: List<String>): List<CachedTranslation>

    @Upsert
    suspend fun upsert(entry: CachedTranslation)

    @Query("UPDATE translations SET last_used = :time WHERE source IN (:sources)")
    suspend fun touch(sources: List<String>, time: Long)

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM translations")
    suspend fun totalBytes(): Long

    @Query("SELECT COUNT(*) FROM translations")
    suspend fun count(): Int

    @Query("DELETE FROM translations WHERE source IN (SELECT source FROM translations ORDER BY last_used ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    @Query("DELETE FROM translations")
    suspend fun clear()
}

@Database(entities = [CachedTranslation::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translations(): TranslationDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "translations.db").build()
    }
}
