package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtractionDao {
    @Query("SELECT * FROM extraction_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ExtractionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExtractionEntity): Long

    @Query("DELETE FROM extraction_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM extraction_history")
    suspend fun clearAll()

    @Query("UPDATE extraction_history SET cloudSyncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String)

    @Query("SELECT COUNT(*) FROM extraction_history")
    fun getCount(): Flow<Int>

    @Query("SELECT SUM(fileSize) FROM extraction_history")
    fun getTotalBytes(): Flow<Long?>
}
