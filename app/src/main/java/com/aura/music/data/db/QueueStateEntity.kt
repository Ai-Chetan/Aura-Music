package com.aura.music.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val songIdsJson: String,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: String
)