package com.aura.music.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "song_tag_cross_ref",
    primaryKeys = ["songId", "tagId"],
    indices = [Index(value = ["tagId"]), Index(value = ["songId"])],
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SongTagCrossRef(
    val songId: Long,
    val tagId: Long
)