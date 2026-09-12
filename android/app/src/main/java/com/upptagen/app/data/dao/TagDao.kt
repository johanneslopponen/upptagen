package com.upptagen.app.data.dao

import androidx.room.*
import com.upptagen.app.data.model.TagEntity
import com.upptagen.app.data.model.TagStat
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTagsFlow(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAllTagsSync(): List<TagEntity>

    @Transaction
    suspend fun getOrCreateTagId(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1
        val existing = getTagByName(trimmed)
        if (existing != null) {
            return existing.id
        }
        val insertedId = insertTag(TagEntity(name = trimmed))
        return if (insertedId != -1L) {
            insertedId
        } else {
            getTagByName(trimmed)?.id ?: -1L
        }
    }

    @Query("""
        SELECT t.name, COUNT(et.entry_id) AS usageCount, COALESCE(SUM(e.duration_seconds), 0) AS totalDurationSeconds
        FROM tags t
        LEFT JOIN entry_tags et ON t.id = et.tag_id
        LEFT JOIN entries e ON et.entry_id = e.id
        GROUP BY t.id, t.name
        ORDER BY t.name ASC
    """)
    fun getTagStatsFlow(): Flow<List<TagStat>>

    @Query("""
        SELECT t.name, COUNT(et.entry_id) AS usageCount, COALESCE(SUM(e.duration_seconds), 0) AS totalDurationSeconds
        FROM tags t
        LEFT JOIN entry_tags et ON t.id = et.tag_id
        LEFT JOIN entries e ON et.entry_id = e.id
        GROUP BY t.id, t.name
        ORDER BY t.name ASC
    """)
    suspend fun getTagStatsSync(): List<TagStat>
}
