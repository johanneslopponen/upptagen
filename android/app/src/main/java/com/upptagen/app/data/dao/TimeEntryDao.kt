package com.upptagen.app.data.dao

import androidx.room.*
import com.upptagen.app.data.model.EntryTagCrossRef
import com.upptagen.app.data.model.TimeEntryEntity
import com.upptagen.app.data.model.TimeEntryWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: TimeEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: TimeEntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long): Int

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getEntryWithTagsById(id: Long): TimeEntryWithTags?

    @Transaction
    @Query("SELECT * FROM entries ORDER BY start_time DESC")
    fun getAllEntriesWithTagsFlow(): Flow<List<TimeEntryWithTags>>

    @Transaction
    @Query("SELECT * FROM entries ORDER BY start_time DESC")
    suspend fun getAllEntriesWithTagsSync(): List<TimeEntryWithTags>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntryTagCrossRef(crossRef: EntryTagCrossRef)

    @Query("DELETE FROM entry_tags WHERE entry_id = :entryId")
    suspend fun deleteEntryTagsForEntry(entryId: Long)

    @Transaction
    suspend fun insertEntryWithTags(
        entry: TimeEntryEntity,
        tagIds: List<Long>
    ): Long {
        val entryId = insertEntry(entry)
        for (tagId in tagIds) {
            insertEntryTagCrossRef(EntryTagCrossRef(entryId, tagId))
        }
        return entryId
    }

    @Transaction
    suspend fun updateEntryWithTags(
        entry: TimeEntryEntity,
        tagIds: List<Long>
    ) {
        updateEntry(entry)
        deleteEntryTagsForEntry(entry.id)
        for (tagId in tagIds) {
            insertEntryTagCrossRef(EntryTagCrossRef(entry.id, tagId))
        }
    }
}
