package com.aura.music.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class SavedTrackWithTags(
    @Embedded val track: SavedTrackEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            SavedTrackTagCrossRef::class,
            parentColumn = "savedTrackId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)
