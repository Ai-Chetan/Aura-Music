package com.aura.music.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class SongWithTags(
    @Embedded val song: SongEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            SongTagCrossRef::class,
            parentColumn = "songId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)