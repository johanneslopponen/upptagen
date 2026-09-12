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
}
