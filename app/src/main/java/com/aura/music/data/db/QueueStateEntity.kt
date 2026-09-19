package com.aura.music.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val songIdsJson: String,
    val currentIndex: Int,
    /** Vault id of the current song (-1 when it was a stream). */
    val currentSongId: Long = -1L,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: String
)