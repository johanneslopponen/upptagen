package com.upptagen.app.data.repository

import androidx.room.withTransaction
import com.upptagen.app.data.AppDatabase
import com.upptagen.app.data.model.ActiveTimerEntity
import com.upptagen.app.data.model.TagEntity
import com.upptagen.app.data.model.TagStat
import com.upptagen.app.data.model.TimeEntryEntity
import com.upptagen.app.data.model.TimeEntryWithTags
import com.upptagen.app.util.DateTimeUtils
import kotlinx.coroutines.CancellationException
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
        return transactionResult {
            check(activeTimerDao.getActiveTimerSync() == null) {
                "A timer is already running. Please stop it before starting a new one."
            }
            val activeTimer = ActiveTimerEntity(
                id = 1,
                project = project?.trim()?.ifEmpty { null },
                description = description?.trim()?.ifEmpty { null },
                startTime = DateTimeUtils.nowIso(),
                tagsJson = tagsToJson(tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
            )
            activeTimerDao.setActiveTimer(activeTimer)
            activeTimer
        }
    }

    suspend fun stopTimer(
        overrideDescription: String? = null,
        additionalTags: List<String> = emptyList()
    ): Result<Long> {
        return transactionResult {
            val timer = activeTimerDao.getActiveTimerSync()
                ?: error("No active timer running.")
            val startInstant = DateTimeUtils.parseIsoOrFlexible(timer.startTime)
                ?: error("Failed to parse timer start time: ${timer.startTime}")
            val endInstant = Instant.now()
            check(!endInstant.isBefore(startInstant)) { "End time cannot be earlier than start time." }
            val combinedTags = (jsonToTags(timer.tagsJson) + additionalTags.map { it.trim() })
                .filter { it.isNotEmpty() }
                .distinct()
            val entry = TimeEntryEntity(
                project = timer.project,
                description = overrideDescription?.trim()?.ifEmpty { null } ?: timer.description,
                startTime = timer.startTime,
                endTime = DateTimeUtils.toIso(endInstant),
                durationSeconds = java.time.Duration.between(startInstant, endInstant).seconds
            )
            val entryId = timeEntryDao.insertEntryWithTags(entry, getOrCreateTagIds(combinedTags))
            activeTimerDao.clearActiveTimer()
            entryId
        }
    }

    // CSV imports share one transaction so a failed row cannot leave a partial import.
    internal suspend fun <T> withTransaction(block: suspend () -> T): T = db.withTransaction { block() }

    private suspend fun <T> transactionResult(block: suspend () -> T): Result<T> {
        return try {
            Result.success(withTransaction(block))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
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

        val durationSeconds = java.time.Duration.between(startTime, endTime).seconds
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

        val existingStart = DateTimeUtils.parseIsoOrFlexible(existing.entry.startTime)
            ?: return Result.failure(IllegalArgumentException("Invalid existing start time."))
        val existingEnd = DateTimeUtils.parseIsoOrFlexible(existing.entry.endTime)
            ?: return Result.failure(IllegalArgumentException("Invalid existing end time."))
        val newStartInstant = startTime ?: existingStart
        val newEndInstant = endTime ?: existingEnd

        if (newEndInstant.isBefore(newStartInstant)) {
            return Result.failure(IllegalArgumentException("End time cannot be earlier than start time."))
        }

        val newDurationSeconds = if (newStartInstant == existingStart && newEndInstant == existingEnd) {
            existing.entry.durationSeconds
        } else {
            java.time.Duration.between(newStartInstant, newEndInstant).seconds
        }
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
