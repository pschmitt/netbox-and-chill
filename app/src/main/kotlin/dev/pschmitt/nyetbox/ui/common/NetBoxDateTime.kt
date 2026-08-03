package dev.pschmitt.nyetbox.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

private val ISO_DATE_ONLY = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Formats NetBox's ISO values for people, using the device's current locale and timezone. Date-only
 * API values intentionally remain date-only: converting them through an instant would incorrectly
 * move them to the previous/next day for some users.
 */
fun formatNetBoxDateTime(
    value: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return trimmed
    runCatching { LocalDate.parse(trimmed, ISO_DATE_ONLY) }
        .getOrNull()
        ?.let { date ->
            return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale)
                .format(date)
        }

    val instant = parseInstant(trimmed) ?: return trimmed
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zoneId)
        .format(instant)
}

private fun parseInstant(value: String): Instant? {
    runCatching { Instant.parse(value) }
        .getOrNull()
        ?.let {
            return it
        }
    runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }
        .getOrNull()
        ?.let {
            return it
        }
    return try {
        DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .toFormatter()
            .parse(value)
            .let { java.time.LocalDateTime.from(it).atZone(ZoneId.systemDefault()).toInstant() }
    } catch (_: DateTimeParseException) {
        null
    }
}
