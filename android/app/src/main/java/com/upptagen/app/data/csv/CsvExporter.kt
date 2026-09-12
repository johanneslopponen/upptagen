package com.upptagen.app.data.csv

import com.upptagen.app.data.model.TimeEntryWithTags
import com.upptagen.app.util.DateTimeUtils
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object CsvExporter {

    private val HEADERS = listOf(
        "ID",
        "Project",
        "Description",
        "Start Time",
        "End Time",
        "Duration (Seconds)",
        "Formatted Duration",
        "Tags"
    )

    fun exportToCsvString(entries: List<TimeEntryWithTags>): String {
        val sb = StringBuilder()
        sb.append(HEADERS.joinToString(",")).append("\r\n")

        for (item in entries) {
            val entry = item.entry
            val tagsStr = item.tagNames.joinToString(",")

            val row = listOf(
                entry.id.toString(),
                entry.project ?: "",
                entry.description ?: "",
                entry.startTime,
                entry.endTime,
                entry.durationSeconds.toString(),
                DateTimeUtils.formatDuration(entry.durationSeconds),
                tagsStr
            )
            sb.append(row.joinToString(",") { escapeCsv(it) }).append("\r\n")
        }
        return sb.toString()
    }

    fun exportToStream(entries: List<TimeEntryWithTags>, outputStream: OutputStream) {
        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(exportToCsvString(entries))
            writer.flush()
        }
    }

    fun exportToFile(entries: List<TimeEntryWithTags>, file: File) {
        file.outputStream().use { exportToStream(entries, it) }
    }

    private fun escapeCsv(value: String): String {
        val containsSpecial = value.contains(',') ||
                value.contains('"') ||
                value.contains('\n') ||
                value.contains('\r')

        return if (containsSpecial) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
