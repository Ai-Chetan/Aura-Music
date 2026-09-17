package com.aura.music.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueStateDao {
    @Query("SELECT * FROM queue_state WHERE id = 0")
    fun observeQueueState(): Flow<QueueStateEntity?>

    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun getQueueState(): QueueStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQueueState(state: QueueStateEntity)

    @Query("DELETE FROM queue_state")
    suspend fun clearQueueState()
}