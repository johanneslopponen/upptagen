package com.upptagen.app

import com.upptagen.app.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DateTimeUtilsTest {

    @Test
    fun testFormatDate() {
        val date = LocalDate.of(2025, 5, 10)
        assertEquals("2025-05-10", DateTimeUtils.formatDate(date))
    }

    @Test
    fun testFormatTime() {
        val time = LocalTime.of(14, 30)
        assertEquals("14:30", DateTimeUtils.formatTime(time))
    }

    @Test
    fun testFormatDisplayDateFriendly() {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val otherDay = LocalDate.of(2024, 1, 1)

        assertTrue(DateTimeUtils.formatDisplayDateFriendly(today).startsWith("Today"))
        assertTrue(DateTimeUtils.formatDisplayDateFriendly(yesterday).startsWith("Yesterday"))
        assertEquals("2024-01-01", DateTimeUtils.formatDisplayDateFriendly(otherDay))
    }

    @Test
    fun testParseTimeOrNull() {
        assertEquals(LocalTime.of(9, 15), DateTimeUtils.parseTimeOrNull("09:15"))
        assertEquals(LocalTime.of(14, 0), DateTimeUtils.parseTimeOrNull("14:00"))
        assertEquals(LocalTime.of(8, 5), DateTimeUtils.parseTimeOrNull("8:05"))
        assertNull(DateTimeUtils.parseTimeOrNull("invalid"))
    }
    @Test
    fun metadataEditsPreserveSecondsAndNanoseconds() {
        val zone = java.time.ZoneId.of("Europe/Stockholm")
        val start = java.time.Instant.parse("2026-10-26T09:00:59.123456789Z")
        val end = java.time.Instant.parse("2026-10-26T09:01:01.987654321Z")
        val edited = DateTimeUtils.editedEntryTimes(
            start, end, start.atZone(zone).toLocalDate(),
            start.atZone(zone).toLocalTime(), end.atZone(zone).toLocalTime(), zone
        )
        assertEquals(start to end, edited)
    }

    @Test
    fun metadataEditsPreserveMultiDaySessionsAndAmbiguousDstOffset() {
        val zone = java.time.ZoneId.of("Europe/Stockholm")
        val start = java.time.Instant.parse("2026-10-25T01:30:12Z")
        val end = java.time.Instant.parse("2026-10-27T09:00:34Z")
        val edited = DateTimeUtils.editedEntryTimes(
            start, end, start.atZone(zone).toLocalDate(),
            start.atZone(zone).toLocalTime(), end.atZone(zone).toLocalTime(), zone
        )
        assertEquals(start to end, edited)
    }

    @Test
    fun editingEndTimeAcrossMidnightKeepsNextDay() {
        val zone = java.time.ZoneId.of("Europe/Stockholm")
        val start = java.time.Instant.parse("2026-10-26T22:30:00Z")
        val end = java.time.Instant.parse("2026-10-26T22:45:00Z")
        val edited = DateTimeUtils.editedEntryTimes(
            start, end, LocalDate.of(2026, 10, 26), LocalTime.of(23, 30), LocalTime.of(0, 15), zone
        )
        assertEquals(start, edited.first)
        assertEquals(java.time.Instant.parse("2026-10-26T23:15:00Z"), edited.second)
    }

    @Test
    fun movingStartPastAnUnchangedEndRollsOverToNextDay() {
        val zone = java.time.ZoneId.of("Europe/Stockholm")
        val start = java.time.Instant.parse("2026-10-26T09:00:00Z")
        val end = java.time.Instant.parse("2026-10-26T10:00:12.123Z")
        val edited = DateTimeUtils.editedEntryTimes(
            start, end, LocalDate.of(2026, 10, 26), LocalTime.of(12, 0), end.atZone(zone).toLocalTime(), zone
        )
        assertEquals(java.time.Instant.parse("2026-10-26T11:00:00Z"), edited.first)
        assertEquals(java.time.Instant.parse("2026-10-27T10:00:12.123Z"), edited.second)
    }
}
