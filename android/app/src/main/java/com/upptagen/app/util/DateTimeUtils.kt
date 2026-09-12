package com.upptagen.app.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object DateTimeUtils {

    private val RFC3339_FORMATTER = DateTimeFormatter.ISO_INSTANT

    private val BACKUP_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.US),
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    )

    fun nowIso(): String {
        return Instant.now().toString()
    }

    fun toIso(instant: Instant): String {
        return instant.toString()
    }

    fun parseIsoOrFlexible(input: String): Instant? {
        val trimmed = input.trim()
        try {
            return Instant.parse(trimmed)
        } catch (_: DateTimeParseException) {
            // Try backup formatters with UTC
            for (formatter in BACKUP_FORMATTERS) {
                try {
                    val ldt = LocalDateTime.parse(trimmed, formatter)
                    return ldt.toInstant(ZoneOffset.UTC)
                } catch (_: Exception) {
                    // Try next
                }
            }
        }
        return null
    }

    fun formatDuration(seconds: Long): String {
        val s = if (seconds < 0) 0 else seconds
        val hours = s / 3600
        val mins = (s % 3600) / 60
        val secs = s % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    }

    fun formatDisplayDateTime(isoString: String): String {
        val instant = parseIsoOrFlexible(isoString) ?: return isoString
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
        return instant.atZone(ZoneId.systemDefault()).format(formatter)
    }

    fun formatDisplayDate(isoString: String): String {
        val instant = parseIsoOrFlexible(isoString) ?: return isoString
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
        return instant.atZone(ZoneId.systemDefault()).format(formatter)
    }
}
