package com.upptagen.app.data.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class TimeEntryWithTags(
    @Embedded
    val entry: TimeEntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntryTagCrossRef::class,
            parentColumn = "entry_id",
            entityColumn = "tag_id"
        )
    )
    val tags: List<TagEntity>
) {
    val tagNames: List<String>
        get() = tags.map { it.name }.sorted()
}
