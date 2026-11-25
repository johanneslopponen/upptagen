package com.upptagen.app.data.csv

import com.upptagen.app.data.repository.TimeTrackingRepository
import com.upptagen.app.util.DateTimeUtils
import kotlinx.coroutines.CancellationException
import org.apache.commons.csv.CSVFormat
import java.io.InputStream

object CsvImporter {

    suspend fun importCsv(
        repository: TimeTrackingRepository,
        inputStream: InputStream
    ): Result<Int> {
        return try {
            val count = inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                // A BOM may precede the first column in files saved by spreadsheet apps.
                reader.mark(1)
                if (reader.read() != 0xFEFF) reader.reset()
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
                    .parse(reader).use parse@{ parser ->
                        val records = parser.records
                        if (parser.headerMap.isEmpty() && records.isEmpty()) return@parse 0
                        require(parser.headerMap.containsKey("Start Time")) { "Missing 'Start Time' column" }
                        require(parser.headerMap.containsKey("End Time")) { "Missing 'End Time' column" }
                        repository.withTransaction {
                            val seen = repository.getAllEntriesWithTags().map { item ->
                                DedupKey(
                                    item.entry.project, item.entry.description,
                                    DateTimeUtils.parseIsoOrFlexible(item.entry.startTime)?.epochSecond
                                        ?: error("Invalid existing start time"),
                                    DateTimeUtils.parseIsoOrFlexible(item.entry.endTime)?.epochSecond
                                        ?: error("Invalid existing end time"),
                                    item.tagNames.distinct().sorted()
                                )
                            }.toMutableSet()
                            var importedCount = 0
                            for (record in records) {
                                val label = "CSV record ${record.recordNumber}"
                                require(record.isConsistent) { "$label: Incorrect number of columns" }
                                val start = DateTimeUtils.parseIsoOrFlexible(record.get("Start Time"))
                                    ?: error("$label: Invalid 'Start Time'")
                                val end = DateTimeUtils.parseIsoOrFlexible(record.get("End Time"))
                                    ?: error("$label: Invalid 'End Time'")
                                require(!end.isBefore(start)) { "$label: End time cannot be earlier than start time" }
                                fun optional(name: String): String? =
                                    if (record.isMapped(name)) record.get(name).trim().ifEmpty { null } else null
                                val tags = optional("Tags").orEmpty().split(',')
                                    .map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
                                val project = optional("Project")
                                val description = optional("Description")
                                val key = DedupKey(project, description, start.epochSecond, end.epochSecond, tags)
                                if (key in seen) continue
                                try {
                                    repository.addManualEntry(project, description, start, end, tags).getOrThrow()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    throw IllegalStateException("$label: ${e.message}", e)
                                }
                                seen.add(key)
                                importedCount++
                            }
                            importedCount
                        }
                    }
            }
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
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
}
