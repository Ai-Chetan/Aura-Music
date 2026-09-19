package com.aura.music.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Many-to-many tags for streaming bookmarks — the Saved tab gets the same
 * tag organization as the downloaded vault.
 */
@Entity(
    tableName = "saved_track_tag_cross_ref",
    primaryKeys = ["savedTrackId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = SavedTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["savedTrackId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tagId"])]
)
data class SavedTrackTagCrossRef(
    val savedTrackId: Long,
    val tagId: Long
)
