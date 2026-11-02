package com.babyschedule.util

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

const val MINUTE_MS = 60_000L
const val HOUR_MS = 60 * MINUTE_MS
const val DAY_MS = 24 * HOUR_MS

fun zone(): ZoneId = ZoneId.systemDefault()

fun startOfDay(date: LocalDate, zone: ZoneId = zone()): Long =
    date.atStartOfDay(zone).toInstant().toEpochMilli()

fun toLocalDate(ms: Long, zone: ZoneId = zone()): LocalDate =
    Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

fun toZoned(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone())

/** A sensible default time for adding an entry on [day]: now for today, midday otherwise. */
fun defaultTimeFor(day: LocalDate): Long {
    val today = LocalDate.now()
    return if (day == today) System.currentTimeMillis()
    else day.atTime(12, 0).atZone(zone()).toInstant().toEpochMilli()
}

private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val shortDateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
private val fullDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val axisDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

fun formatTime(ms: Long): String = timeFormatter.format(toZoned(ms))
fun formatDateShort(date: LocalDate): String = shortDateFormatter.format(date)
fun formatDateFull(date: LocalDate): String = fullDateFormatter.format(date)
fun formatAxisDate(date: LocalDate): String = axisDateFormatter.format(date)

fun formatDayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> formatDateShort(date)
}

/** "1h 05m", "12m", "45s". */
fun formatDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}

fun formatMinutes(minutes: Float): String = formatDuration((minutes * 60).roundToInt().toLong())

/** Stopwatch style: "05:12" or "1:05:12". */
fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val mm = m.toString().padStart(2, '0')
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
}

fun formatAgo(ms: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - ms
    if (diff < MINUTE_MS) return "just now"
    val minutes = diff / MINUTE_MS
    if (minutes < 60) return "${minutes}m ago"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ${(minutes % 60).toString().padStart(2, '0')}m ago"
    val days = hours / 24
    return if (days == 1L) "1 day ago" else "$days days ago"
}

fun formatWeight(grams: Int): String = String.format(Locale.getDefault(), "%.2f kg", grams / 1000.0)

fun formatWeightChange(grams: Int): String {
    val sign = if (grams >= 0) "+" else "−"
    return "$sign${abs(grams)} g"
}

fun formatNumber(v: Float, decimals: Int = 1): String =
    if (v == v.roundToInt().toFloat()) v.roundToInt().toString()
    else String.format(Locale.getDefault(), "%.${decimals}f", v)

fun formatAge(birth: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(birth, today)
    if (days < 0) return "Due in ${-days} days"
    if (days == 0L) return "Born today"
    if (days < 14) return if (days == 1L) "1 day old" else "$days days old"
    if (days < 56) {
        val w = days / 7
        val d = days % 7
        return "$w weeks" + if (d > 0) " $d days" else ""
    }
    val p = Period.between(birth, today)
    val months = p.years * 12 + p.months
    return "$months months" + if (p.days > 0) " ${p.days} days" else ""
}
