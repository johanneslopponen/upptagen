package com.upptagen.app

import com.upptagen.app.data.csv.CsvExporter
import com.upptagen.app.data.model.TagEntity
import com.upptagen.app.data.model.TimeEntryEntity
import com.upptagen.app.data.model.TimeEntryWithTags
import com.upptagen.app.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportImportTest {

    @Test
    fun testFormatDuration() {
        assertEquals("00:00:00", DateTimeUtils.formatDuration(0))
        assertEquals("00:01:00", DateTimeUtils.formatDuration(60))
        assertEquals("01:00:00", DateTimeUtils.formatDuration(3600))
        assertEquals("01:01:05", DateTimeUtils.formatDuration(3665))
    }

    @Test
    fun testCsvExportFormat() {
        val entry = TimeEntryWithTags(
            entry = TimeEntryEntity(
                id = 1,
                project = "CoreApp",
                description = "Implementing feature",
                startTime = "2025-01-01T10:00:00Z",
                endTime = "2025-01-01T12:00:00Z",
                durationSeconds = 7200
            ),
            tags = listOf(
                TagEntity(id = 1, name = "rust"),
                TagEntity(id = 2, name = "backend")
            )
        )

        val csv = CsvExporter.exportToCsvString(listOf(entry))

        // Check header matches desktop format exactly
        assertTrue(csv.contains("ID,Project,Description,Start Time,End Time,Duration (Seconds),Formatted Duration,Tags"))
        // Check row content
        assertTrue(csv.contains("CoreApp"))
        assertTrue(csv.contains("Implementing feature"))
        assertTrue(csv.contains("2025-01-01T10:00:00Z"))
        assertTrue(csv.contains("2025-01-01T12:00:00Z"))
        assertTrue(csv.contains("7200"))
        assertTrue(csv.contains("02:00:00"))
        assertTrue(csv.contains("\"backend,rust\"") || csv.contains("backend,rust"))
    }
}
