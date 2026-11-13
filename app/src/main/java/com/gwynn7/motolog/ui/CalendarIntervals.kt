package com.gwynn7.motolog.ui

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Calendar dates, not 24-hour blocks: a daylight-saving change is still one day. */
fun calendarDaysBetween(earlier: Long, later: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    ChronoUnit.DAYS.between(Instant.ofEpochMilli(earlier).atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(later).atZone(zone).toLocalDate())

data class LogInterval(val years: Long, val months: Long, val days: Long)

/** Display convention requested for logs: 30 days per month, 12 months per year. */
fun logInterval(days: Long): LogInterval {
    val positive = days.coerceAtLeast(0)
    return LogInterval(positive / 360, positive % 360 / 30, positive % 30)
}
