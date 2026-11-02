package com.babyschedule.domain

import com.babyschedule.data.Diaper
import com.babyschedule.data.DiaperType
import com.babyschedule.data.Feeding
import com.babyschedule.data.FeedingType
import com.babyschedule.data.Growth
import com.babyschedule.data.Profile
import com.babyschedule.data.Side
import com.babyschedule.data.Sleep
import com.babyschedule.data.durationMs
import com.babyschedule.data.isDirty
import com.babyschedule.data.isWet
import com.babyschedule.data.totalSeconds
import com.babyschedule.util.DAY_MS
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class DayStats(
    val date: LocalDate,
    val breastFeeds: Int = 0,
    val bottleFeeds: Int = 0,
    val leftMin: Float = 0f,
    val rightMin: Float = 0f,
    val bottleMl: Int = 0,
    val wet: Int = 0,
    val dirty: Int = 0,
    val mixed: Int = 0,
    val sleepMin: Float = 0f,
) {
    val feeds: Int get() = breastFeeds + bottleFeeds
    val diapers: Int get() = wet + dirty + mixed
    val breastMin: Float get() = leftMin + rightMin
    val wetTotal: Int get() = wet + mixed
    val dirtyTotal: Int get() = dirty + mixed
    val hasData: Boolean get() = feeds > 0 || diapers > 0 || sleepMin > 0f
}

data class Stats(
    val from: LocalDate,
    val to: LocalDate,
    /** One entry per day, oldest first. */
    val days: List<DayStats>,
    /** Days that have at least one entry; averages are taken over these. */
    val activeDays: Int,
    val totalFeeds: Int,
    val avgFeedsPerDay: Float,
    val avgIntervalMin: Float?,
    val avgBreastSessionMin: Float?,
    /** Share of breastfeeding time on the left side, 0..1. */
    val leftShare: Float?,
    val avgBreastMinPerDay: Float,
    val avgBottleMlPerDay: Float,
    val avgDiapersPerDay: Float,
    val avgWetPerDay: Float,
    val avgDirtyPerDay: Float,
    val avgSleepHoursPerDay: Float,
    val longestSleepMin: Float?,
    /** Number of feeds started in each hour of the day (24 entries). */
    val feedsByHour: List<Int>,
    val feedings: List<Feeding>,
    val diapers: List<Diaper>,
    val sleeps: List<Sleep>,
)

data class WeightPoint(val time: Long, val grams: Int, val isBirth: Boolean = false)

data class GrowthSummary(
    val points: List<WeightPoint>,
    val latest: WeightPoint?,
    val birth: WeightPoint?,
    val changeSinceBirth: Int?,
    /** Latest weight as a percentage of birth weight (e.g. 93.5 = 6.5% below). */
    val percentOfBirth: Float?,
    val lowest: WeightPoint?,
    /** Grams per day over roughly the last week. */
    val recentGainPerDay: Float?,
    /** Grams per day since the first measurement. */
    val overallGainPerDay: Float?,
)

object StatsCalculator {

    /** Feeds starting within this window of each other count as a single feed for interval stats. */
    private const val MIN_INTERVAL_MS = 20 * 60_000L

    fun compute(
        from: LocalDate,
        to: LocalDate,
        feedings: List<Feeding>,
        diapers: List<Diaper>,
        sleeps: List<Sleep>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Stats {
        val dayCount = (ChronoUnit.DAYS.between(from, to) + 1).toInt().coerceAtLeast(1)
        val rangeStart = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val days = (0 until dayCount).map { i ->
            val date = from.plusDays(i.toLong())
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val f = feedings.filter { it.startTime in start until end }
            val d = diapers.filter { it.time in start until end }
            val sleepMs = sleeps.sumOf { s ->
                (minOf(s.endTime, end) - maxOf(s.startTime, start)).coerceAtLeast(0L)
            }
            DayStats(
                date = date,
                breastFeeds = f.count { it.type == FeedingType.BREAST },
                bottleFeeds = f.count { it.type == FeedingType.BOTTLE },
                leftMin = f.sumOf { it.leftSeconds } / 60f,
                rightMin = f.sumOf { it.rightSeconds } / 60f,
                bottleMl = f.filter { it.type == FeedingType.BOTTLE }.sumOf { it.amountMl ?: 0 },
                wet = d.count { it.type == DiaperType.WET },
                dirty = d.count { it.type == DiaperType.DIRTY },
                mixed = d.count { it.type == DiaperType.MIXED },
                sleepMin = sleepMs / 60_000f,
            )
        }

        val inRangeFeeds = feedings.filter { it.startTime in rangeStart until rangeEnd }
        val inRangeDiapers = diapers.filter { it.time in rangeStart until rangeEnd }
        val activeDays = days.count { it.hasData }.coerceAtLeast(1)

        val starts = inRangeFeeds.map { it.startTime }.sorted()
        val intervals = starts.zipWithNext { a, b -> b - a }
            .filter { it >= MIN_INTERVAL_MS && it <= 12 * 3_600_000L }
        val avgInterval = if (intervals.isEmpty()) null else intervals.average().toFloat() / 60_000f

        val breast = inRangeFeeds.filter { it.type == FeedingType.BREAST }
        val avgBreastSession =
            if (breast.isEmpty()) null else breast.map { it.totalSeconds }.average().toFloat() / 60f
        val leftTotal = breast.sumOf { it.leftSeconds }
        val rightTotal = breast.sumOf { it.rightSeconds }
        val leftShare =
            if (leftTotal + rightTotal == 0) null else leftTotal.toFloat() / (leftTotal + rightTotal)

        val longestSleep = sleeps.filter { it.endTime > rangeStart && it.startTime < rangeEnd }
            .maxOfOrNull { it.durationMs }?.let { it / 60_000f }

        val byHour = MutableList(24) { 0 }
        inRangeFeeds.forEach { byHour[Instant.ofEpochMilli(it.startTime).atZone(zone).hour]++ }

        val n = activeDays.toFloat()
        return Stats(
            from = from,
            to = to,
            days = days,
            activeDays = activeDays,
            totalFeeds = inRangeFeeds.size,
            avgFeedsPerDay = days.sumOf { it.feeds } / n,
            avgIntervalMin = avgInterval,
            avgBreastSessionMin = avgBreastSession,
            leftShare = leftShare,
            avgBreastMinPerDay = days.sumOf { it.breastMin.toDouble() }.toFloat() / n,
            avgBottleMlPerDay = days.sumOf { it.bottleMl } / n,
            avgDiapersPerDay = days.sumOf { it.diapers } / n,
            avgWetPerDay = days.sumOf { it.wetTotal } / n,
            avgDirtyPerDay = days.sumOf { it.dirtyTotal } / n,
            avgSleepHoursPerDay = days.sumOf { it.sleepMin.toDouble() }.toFloat() / 60f / n,
            longestSleepMin = longestSleep,
            feedsByHour = byHour,
            feedings = inRangeFeeds,
            diapers = inRangeDiapers,
            sleeps = sleeps,
        )
    }

    fun day(date: LocalDate, feedings: List<Feeding>, diapers: List<Diaper>, sleeps: List<Sleep>): DayStats =
        compute(date, date, feedings, diapers, sleeps).days.first()

    /**
     * Suggests which breast to offer first, based on the most recent breastfeed:
     * if only one side was used, offer the other one; if both were used, start on
     * the side the last feed ended on (so the starting side alternates).
     */
    fun suggestedSide(last: Feeding?): Side {
        if (last == null || last.totalSeconds == 0) return Side.LEFT
        return when {
            last.leftSeconds == 0 -> Side.LEFT
            last.rightSeconds == 0 -> Side.RIGHT
            else -> last.lastSide
                ?: last.firstSide?.other()
                ?: if (last.leftSeconds <= last.rightSeconds) Side.LEFT else Side.RIGHT
        }
    }

    fun growth(entries: List<Growth>, profile: Profile, zone: ZoneId = ZoneId.systemDefault()): GrowthSummary {
        val measured = entries.mapNotNull { g -> g.weightGrams?.let { WeightPoint(g.time, it) } }
        val birthPoint = profile.birthWeightGrams?.let { grams ->
            profile.birthDate?.let { WeightPoint(it.atStartOfDay(zone).toInstant().toEpochMilli(), grams, isBirth = true) }
        }
        val points = (listOfNotNull(birthPoint) + measured).sortedBy { it.time }
        val latest = points.lastOrNull()
        val birth = birthPoint ?: points.firstOrNull()

        fun rate(a: WeightPoint, b: WeightPoint): Float? {
            val days = (b.time - a.time) / DAY_MS.toFloat()
            return if (days < 1f) null else (b.grams - a.grams) / days
        }

        val recent = latest?.let { l ->
            val ref = points.lastOrNull { it.time <= l.time - 7 * DAY_MS }
                ?: points.firstOrNull { it !== l && l.time - it.time >= DAY_MS }
            ref?.let { rate(it, l) }
        }
        val overall = if (latest != null && points.size >= 2) rate(points.first(), latest) else null

        return GrowthSummary(
            points = points,
            latest = latest,
            birth = birth,
            changeSinceBirth = if (latest != null && birth != null && latest !== birth) latest.grams - birth.grams else null,
            percentOfBirth = if (latest != null && birth != null && latest !== birth && birth.grams > 0)
                latest.grams * 100f / birth.grams else null,
            lowest = points.minByOrNull { it.grams },
            recentGainPerDay = recent,
            overallGainPerDay = overall,
        )
    }
}
