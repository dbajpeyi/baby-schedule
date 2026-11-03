package com.babyschedule

import com.babyschedule.data.Diaper
import com.babyschedule.data.DiaperType
import com.babyschedule.data.Feeding
import com.babyschedule.data.FeedingTimer
import com.babyschedule.data.FeedingType
import com.babyschedule.data.Growth
import com.babyschedule.data.Profile
import com.babyschedule.data.Segment
import com.babyschedule.data.Segments
import com.babyschedule.data.Side
import com.babyschedule.data.Sleep
import com.babyschedule.data.intakeGrams
import com.babyschedule.data.segmentList
import com.babyschedule.domain.FeedKind
import com.babyschedule.domain.SessionDraft
import com.babyschedule.domain.SleepMode
import com.babyschedule.domain.StatsCalculator
import com.babyschedule.util.resolveClockTime
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
        assertEquals(
            listOf(Segment(Side.LEFT, t0, t0 + 10 * 60_000), Segment(Side.RIGHT, t0 + 10 * 60_000, t0 + 15 * 60_000)),
            f.segmentList,
        )
    }

    @Test
    fun sessionLikeTheNotebook() {
        // Right 1:15, left 1:31, back to right 1:47, end 1:54; weighed 2575 g -> 2620 g.
        val t = FeedingTimer(weightBeforeGrams = 2575)
            .tap(Side.RIGHT, at(1, 15))
            .startSide(Side.LEFT, at(1, 31))
            .startSide(Side.RIGHT, at(1, 47))
            .pause(at(1, 54))
        val f = t.toFeeding(at(2, 30))!!.copy(weightAfterGrams = 2620)
        assertEquals(3, f.segmentList.size)
        assertEquals(at(1, 54), f.endTime)
        assertEquals((16 + 7) * 60, f.rightSeconds)
        assertEquals(16 * 60, f.leftSeconds)
        assertEquals(45, f.intakeGrams)
        assertEquals(f.segmentList, Segments.decode(Segments.encode(f.segmentList)))

        val d = StatsCalculator.compute(day, day, listOf(f), emptyList(), emptyList(), zone).days.single()
        assertEquals(45, d.breastIntakeG)
        assertEquals(1, d.weighedFeeds)
        assertEquals(45, d.foodG)
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
    fun sessionDraftBuildsOneFeedFromSideTimes() {
        val d = SessionDraft.new(at(1, 15)).copy(
            diaperType = DiaperType.MIXED,
            diaperAmount = "Lots",
            diaperNotes = "yellow/green",
            feedKind = FeedKind.BREAST,
            segments = listOf(
                Segment(Side.RIGHT, at(1, 15), at(1, 31)),
                Segment(Side.LEFT, at(1, 31), at(1, 47)),
                Segment(Side.RIGHT, at(1, 47), at(1, 54)),
            ),
            weightBefore = 2575,
            weightAfter = 2620,
            sleepMode = SleepMode.TIMES,
            sleepStart = at(2, 0),
            sleepEnd = at(4, 10),
        )
        assertTrue(d.canSave)
        assertEquals(45, d.intakeGrams)
        val f = d.toFeeding(7)!!
        assertEquals(7L, f.sessionId)
        assertEquals(at(1, 15), f.startTime)
        assertEquals(at(1, 54), f.endTime)
        assertEquals(16 * 60, f.leftSeconds)
        assertEquals(23 * 60, f.rightSeconds)
        assertEquals(Side.RIGHT, f.firstSide)
        assertEquals(Side.RIGHT, f.lastSide)
        assertEquals(3, f.segmentList.size)
        assertEquals("yellow/green", d.toDiaper(7)!!.notes)
        assertEquals(at(4, 10) - at(2, 0), d.toSleep(7)!!.let { it.endTime - it.startTime })

        // Moving the session moves every time in it.
        val moved = d.shiftedTo(at(2, 15))
        assertEquals(at(2, 15), moved.segments.first().start)
        assertEquals(at(5, 10), moved.sleepEnd)

        // Round trip through the saved rows.
        val back = SessionDraft.from(d.toSession(), f, d.toDiaper(7), d.toSleep(7))
        assertEquals(d.segments, back.segments)
        assertEquals(SleepMode.TIMES, back.sleepMode)
        assertEquals(2620, back.weightAfter)
    }

    @Test
    fun sessionDraftValidation() {
        val empty = SessionDraft.new(at(9))
        assertFalse(empty.canSave)
        assertTrue(empty.copy(diaperType = DiaperType.WET).canSave)
        val badSleep = empty.copy(sleepMode = SleepMode.TIMES, sleepStart = at(10), sleepEnd = at(9))
        assertFalse(badSleep.canSave)
        val noSides = empty.copy(feedKind = FeedKind.BREAST)
        assertFalse(noSides.canSave)
    }

    @Test
    fun oldFeedsWithoutSideTimesGetSegments() {
        val f = Feeding(
            type = FeedingType.BREAST, startTime = at(8), endTime = at(8, 30),
            leftSeconds = 600, rightSeconds = 300, firstSide = Side.RIGHT,
        )
        val segs = SessionDraft.segmentsFor(f)
        assertEquals(listOf(Segment(Side.RIGHT, at(8), at(8, 5)), Segment(Side.LEFT, at(8, 5), at(8, 15))), segs)
    }

    @Test
    fun clockTimesResolveAcrossMidnight() {
        val z = java.time.ZoneId.systemDefault()
        fun local(d: LocalDate, h: Int, m: Int) = d.atTime(h, m).atZone(z).toInstant().toEpochMilli()
        val ref = local(day, 23, 40)
        // An end time of 00:20 after 23:40 is the next day.
        assertEquals(local(day.plusDays(1), 0, 20), resolveClockTime(ref, 0, 20, after = true))
        // The nearest 23:10 is the same evening.
        assertEquals(local(day, 23, 10), resolveClockTime(ref, 23, 10, after = false))
        // The nearest 00:05 is just after midnight.
        assertEquals(local(day.plusDays(1), 0, 5), resolveClockTime(ref, 0, 5, after = false))
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
        // Only the bottle has a known amount: food = 90 g over 1 feed.
        assertEquals(90, d.foodG)
        assertEquals(1, d.measuredFeeds)
        assertEquals(90f, s.avgFoodPerFeed!!, 0.01f)
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
        val g = StatsCalculator.growth(entries, profile, zone = zone)
        assertEquals(3, g.points.size)
        assertEquals(200, g.changeSinceBirth)
        assertEquals(3300, g.lowest!!.grams)
        assertNotNull(g.recentGainPerDay)
        assertEquals(400f / 11f, g.recentGainPerDay!!, 0.5f)
    }
}
