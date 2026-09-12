package com.upptagen.app.data.csv

import com.upptagen.app.data.repository.TimeTrackingRepository
import com.upptagen.app.util.DateTimeUtils
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object CsvImporter {

    suspend fun importCsv(
        repository: TimeTrackingRepository,
        inputStream: InputStream
    ): Result<Int> {
        return try {
            val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
            val lines = reader.readLines()
            if (lines.isEmpty()) {
                return Result.success(0)
            }

            // Parse header line to determine column indices
            val headerTokens = parseCsvLine(lines[0])
            val colMap = headerTokens.mapIndexed { index, name -> name.trim() to index }.toMap()

            val projIdx = colMap["Project"]
            val descIdx = colMap["Description"]
            val startIdx = colMap["Start Time"] ?: return Result.failure(IllegalArgumentException("Missing 'Start Time' column"))
            val endIdx = colMap["End Time"] ?: return Result.failure(IllegalArgumentException("Missing 'End Time' column"))
            val tagsIdx = colMap["Tags"]

            val existingEntries = repository.getAllEntriesWithTags()
            val seen = existingEntries.map { entry ->
                val startSec = DateTimeUtils.parseIsoOrFlexible(entry.entry.startTime)?.epochSecond ?: 0L
                val endSec = DateTimeUtils.parseIsoOrFlexible(entry.entry.endTime)?.epochSecond ?: 0L
                val sortedTags = entry.tagNames.sorted()
                DedupKey(
                    project = entry.entry.project,
                    description = entry.entry.description,
                    startSec = startSec,
                    endSec = endSec,
                    tags = sortedTags
                )
            }.toMutableSet()

            var importedCount = 0

            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue

                val tokens = parseCsvLine(line)
                if (tokens.size <= maxOf(startIdx, endIdx)) continue

                val startStr = tokens[startIdx].trim()
                val endStr = tokens[endIdx].trim()

                val startInstant = DateTimeUtils.parseIsoOrFlexible(startStr) ?: continue
                val endInstant = DateTimeUtils.parseIsoOrFlexible(endStr) ?: continue

                val project = projIdx?.let { if (it < tokens.size) tokens[it].trim().ifEmpty { null } else null }
                val description = descIdx?.let { if (it < tokens.size) tokens[it].trim().ifEmpty { null } else null }

                val tags = if (tagsIdx != null && tagsIdx < tokens.size) {
                    tokens[tagsIdx].split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }

                val sortedTags = tags.sorted()
                val key = DedupKey(
                    project = project,
                    description = description,
                    startSec = startInstant.epochSecond,
                    endSec = endInstant.epochSecond,
                    tags = sortedTags
                )

                if (seen.contains(key)) {
                    continue
                }

                repository.addManualEntry(
                    project = project,
                    description = description,
                    startTime = startInstant,
                    endTime = endInstant,
                    tags = tags
                )

                seen.add(key)
                importedCount++
            }

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class DedupKey(
        val project: String?,
        val description: String?,
        val startSec: Long,
        val endSec: Long,
        val tags: List<String>
    )

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++ // skip escaped quote
                    } else {
                        inQuotes = false
                    }
                } else {
                    sb.append(c)
                }
            } else {
                if (c == '"') {
                    inQuotes = true
                } else if (c == ',') {
                    tokens.add(sb.toString())
                    sb.clear()
                } else {
                    sb.append(c)
                }
            }
            i++
        }
        tokens.add(sb.toString())
        return tokens
    }
}
