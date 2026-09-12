package com.upptagen.app.data.repository

import com.upptagen.app.data.AppDatabase
import com.upptagen.app.data.model.ActiveTimerEntity
import com.upptagen.app.data.model.TagEntity
import com.upptagen.app.data.model.TagStat
import com.upptagen.app.data.model.TimeEntryEntity
import com.upptagen.app.data.model.TimeEntryWithTags
import com.upptagen.app.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import java.time.Instant

class TimeTrackingRepository(private val db: AppDatabase) {

    private val timeEntryDao = db.timeEntryDao()
    private val tagDao = db.tagDao()
    private val activeTimerDao = db.activeTimerDao()

    fun getActiveTimerFlow(): Flow<ActiveTimerEntity?> = activeTimerDao.getActiveTimerFlow()

    suspend fun getActiveTimer(): ActiveTimerEntity? = activeTimerDao.getActiveTimerSync()

    fun getAllEntriesWithTagsFlow(): Flow<List<TimeEntryWithTags>> = timeEntryDao.getAllEntriesWithTagsFlow()

    suspend fun getAllEntriesWithTags(): List<TimeEntryWithTags> = timeEntryDao.getAllEntriesWithTagsSync()

    fun getTagStatsFlow(): Flow<List<TagStat>> = tagDao.getTagStatsFlow()

    fun getAllTagsFlow(): Flow<List<TagEntity>> = tagDao.getAllTagsFlow()

    suspend fun startTimer(
        project: String?,
        description: String?,
        tags: List<String>
    ): Result<ActiveTimerEntity> {
        val current = activeTimerDao.getActiveTimerSync()
        if (current != null) {
            return Result.failure(IllegalStateException("A timer is already running. Please stop it before starting a new one."))
        }

        val cleanProject = project?.trim()?.ifEmpty { null }
        val cleanDesc = description?.trim()?.ifEmpty { null }
        val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        val activeTimer = ActiveTimerEntity(
            id = 1,
            project = cleanProject,
            description = cleanDesc,
            startTime = DateTimeUtils.nowIso(),
            tagsJson = tagsToJson(cleanTags)
        )
        activeTimerDao.setActiveTimer(activeTimer)
        return Result.success(activeTimer)
    }

    suspend fun stopTimer(
        overrideDescription: String? = null,
        additionalTags: List<String> = emptyList()
    ): Result<Long> {
        val timer = activeTimerDao.getActiveTimerSync()
            ?: return Result.failure(IllegalStateException("No active timer running."))

        val startInstant = DateTimeUtils.parseIsoOrFlexible(timer.startTime)
            ?: return Result.failure(IllegalStateException("Failed to parse timer start time: ${timer.startTime}"))
        val endInstant = Instant.now()

        val durationSeconds = (endInstant.epochSecond - startInstant.epochSecond).coerceAtLeast(0)

        val finalDesc = overrideDescription?.trim()?.ifEmpty { null }
            ?: timer.description

        val baseTags = jsonToTags(timer.tagsJson)
        val combinedTags = (baseTags + additionalTags.map { it.trim() })
            .filter { it.isNotEmpty() }
            .distinct()

        val entry = TimeEntryEntity(
            project = timer.project,
            description = finalDesc,
            startTime = timer.startTime,
            endTime = DateTimeUtils.toIso(endInstant),
            durationSeconds = durationSeconds
        )

        val tagIds = getOrCreateTagIds(combinedTags)
        val entryId = timeEntryDao.insertEntryWithTags(entry, tagIds)
        activeTimerDao.clearActiveTimer()

        return Result.success(entryId)
    }

    suspend fun addManualEntry(
        project: String?,
        description: String?,
        startTime: Instant,
        endTime: Instant,
        tags: List<String>
    ): Result<Long> {
        if (endTime.isBefore(startTime)) {
            return Result.failure(IllegalArgumentException("End time cannot be earlier than start time."))
        }

        val durationSeconds = (endTime.epochSecond - startTime.epochSecond).coerceAtLeast(0)
        val cleanProject = project?.trim()?.ifEmpty { null }
        val cleanDesc = description?.trim()?.ifEmpty { null }
        val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        val entry = TimeEntryEntity(
            project = cleanProject,
            description = cleanDesc,
            startTime = DateTimeUtils.toIso(startTime),
            endTime = DateTimeUtils.toIso(endTime),
            durationSeconds = durationSeconds
        )

        val tagIds = getOrCreateTagIds(cleanTags)
        val entryId = timeEntryDao.insertEntryWithTags(entry, tagIds)
        return Result.success(entryId)
    }

    suspend fun editEntry(
        id: Long,
        project: String?,
        description: String?,
        startTime: Instant?,
        endTime: Instant?,
        tags: List<String>?
    ): Result<Unit> {
        val existing = timeEntryDao.getEntryWithTagsById(id)
            ?: return Result.failure(IllegalArgumentException("Entry #$id not found."))

        val newStartInstant = startTime ?: DateTimeUtils.parseIsoOrFlexible(existing.entry.startTime)
            ?: return Result.failure(IllegalArgumentException("Invalid existing start time."))
        val newEndInstant = endTime ?: DateTimeUtils.parseIsoOrFlexible(existing.entry.endTime)
            ?: return Result.failure(IllegalArgumentException("Invalid existing end time."))

        if (newEndInstant.isBefore(newStartInstant)) {
            return Result.failure(IllegalArgumentException("End time cannot be earlier than start time."))
        }

        val newDurationSeconds = (newEndInstant.epochSecond - newStartInstant.epochSecond).coerceAtLeast(0)
        val newProject = project?.trim()?.ifEmpty { null } ?: existing.entry.project
        val newDesc = description?.trim()?.ifEmpty { null } ?: existing.entry.description

        val updatedEntry = existing.entry.copy(
            project = newProject,
            description = newDesc,
            startTime = DateTimeUtils.toIso(newStartInstant),
            endTime = DateTimeUtils.toIso(newEndInstant),
            durationSeconds = newDurationSeconds
        )

        val finalTags = tags ?: existing.tagNames
        val cleanTags = finalTags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val tagIds = getOrCreateTagIds(cleanTags)

        timeEntryDao.updateEntryWithTags(updatedEntry, tagIds)
        return Result.success(Unit)
    }

    suspend fun deleteEntry(id: Long): Boolean {
        return timeEntryDao.deleteEntryById(id) > 0
    }

    private suspend fun getOrCreateTagIds(tags: List<String>): List<Long> {
        val ids = mutableListOf<Long>()
        for (tag in tags) {
            val clean = tag.trim()
            if (clean.isNotEmpty()) {
                val tagId = tagDao.getOrCreateTagId(clean)
                if (tagId != -1L) {
                    ids.add(tagId)
                }
            }
        }
        return ids
    }

    companion object {
        fun tagsToJson(tags: List<String>): String {
            val jsonArray = JSONArray()
            tags.forEach { jsonArray.put(it) }
            return jsonArray.toString()
        }

        fun jsonToTags(json: String): List<String> {
            return try {
                val array = JSONArray(json)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val item = array.optString(i)?.trim()
                    if (!item.isNullOrEmpty()) {
                        list.add(item)
                    }
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
