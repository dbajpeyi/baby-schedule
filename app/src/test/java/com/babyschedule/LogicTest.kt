package com.babyschedule

import com.babyschedule.data.Diaper
import com.babyschedule.data.DiaperType
import com.babyschedule.data.Feeding
import com.babyschedule.data.FeedingTimer
import com.babyschedule.data.FeedingType
import com.babyschedule.data.Growth
import com.babyschedule.data.Profile
import com.babyschedule.data.Side
import com.babyschedule.data.Sleep
import com.babyschedule.domain.StatsCalculator
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 1, 10)
    private fun at(hour: Int, minute: Int = 0, date: LocalDate = day) =
        date.atTime(hour, minute).toInstant(zone).toEpochMilli()

    @Test
    fun timerTracksBothSidesAndSwitches() {
        val t0 = at(8)
        var t = FeedingTimer().tap(Side.LEFT, t0)
        assertTrue(t.isRunning)
        t = t.tap(Side.RIGHT, t0 + 10 * 60_000) // switch after 10 min
        t = t.pause(t0 + 15 * 60_000) // 5 min on right
        assertFalse(t.isRunning)
        assertEquals(10 * 60_000L, t.leftAt(t0 + 60 * 60_000))
        assertEquals(5 * 60_000L, t.rightAt(t0 + 60 * 60_000))

        val f = t.toFeeding(t0 + 16 * 60_000)!!
        assertEquals(600, f.leftSeconds)
        assertEquals(300, f.rightSeconds)
        assertEquals(Side.LEFT, f.firstSide)
        assertEquals(Side.RIGHT, f.lastSide)
        assertEquals(t0, f.startTime)
    }

    @Test
    fun tappingRunningSidePauses() {
        val t = FeedingTimer().tap(Side.LEFT, 0).tap(Side.LEFT, 60_000)
        assertFalse(t.isRunning)
        assertEquals(60_000L, t.leftAt(1_000_000))
        assertNull(FeedingTimer().toFeeding(10))
    }

    @Test
    fun pausedSessionEndsWhenPaused() {
        val t0 = at(9)
        val t = FeedingTimer().tap(Side.LEFT, t0)
            .startSide(Side.RIGHT, t0 + 5 * 60_000)
            .startSide(Side.RIGHT, t0 + 6 * 60_000) // already running: no change
            .pause(t0 + 8 * 60_000)
        val f = t.toFeeding(t0 + 30 * 60_000)!!
        assertEquals(t0 + 8 * 60_000, f.endTime)
        assertEquals(300, f.leftSeconds)
        assertEquals(180, f.rightSeconds)
    }

    @Test
    fun suggestedSide() {
        assertEquals(Side.LEFT, StatsCalculator.suggestedSide(null))
        val onlyLeft = Feeding(type = FeedingType.BREAST, startTime = 0, endTime = 0, leftSeconds = 600)
        assertEquals(Side.RIGHT, StatsCalculator.suggestedSide(onlyLeft))
        val both = onlyLeft.copy(rightSeconds = 300, firstSide = Side.LEFT, lastSide = Side.RIGHT)
        assertEquals(Side.RIGHT, StatsCalculator.suggestedSide(both))
    }

    @Test
    fun dailyStats() {
        val feedings = listOf(
            Feeding(type = FeedingType.BREAST, startTime = at(1), endTime = at(1, 20), leftSeconds = 600, rightSeconds = 600),
            Feeding(type = FeedingType.BREAST, startTime = at(4), endTime = at(4, 10), leftSeconds = 600),
            Feeding(type = FeedingType.BOTTLE, startTime = at(7), endTime = at(7), amountMl = 90),
        )
        val diapers = listOf(
            Diaper(time = at(2), type = DiaperType.WET),
            Diaper(time = at(5), type = DiaperType.MIXED),
        )
        // Sleep crossing midnight into the day: only 2h of it count for this day.
        val sleeps = listOf(Sleep(startTime = at(22, date = day.minusDays(1)), endTime = at(2)))
        val s = StatsCalculator.compute(day, day, feedings, diapers, sleeps, zone)
        val d = s.days.single()
        assertEquals(2, d.breastFeeds)
        assertEquals(1, d.bottleFeeds)
        assertEquals(20f, d.leftMin, 0.01f)
        assertEquals(10f, d.rightMin, 0.01f)
        assertEquals(90, d.bottleMl)
        assertEquals(2, d.wetTotal)
        assertEquals(1, d.dirtyTotal)
        assertEquals(120f, d.sleepMin, 0.01f)
        assertEquals(180f, s.avgIntervalMin!!, 0.01f)
        assertEquals(2f / 3f, s.leftShare!!, 0.001f)
    }

    @Test
    fun growthSummaryUsesBirthWeight() {
        val profile = Profile(name = "A", birthDate = day, birthWeightGrams = 3500)
        val entries = listOf(
            Growth(time = at(10, date = day.plusDays(3)), weightGrams = 3300),
            Growth(time = at(10, date = day.plusDays(14)), weightGrams = 3700),
        )
        val g = StatsCalculator.growth(entries, profile, zone)
        assertEquals(3, g.points.size)
        assertEquals(200, g.changeSinceBirth)
        assertEquals(3300, g.lowest!!.grams)
        assertNotNull(g.recentGainPerDay)
        assertEquals(400f / 11f, g.recentGainPerDay!!, 0.5f)
    }
}
