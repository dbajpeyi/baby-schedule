package com.babyschedule.domain

import com.babyschedule.data.Diaper
import com.babyschedule.data.DiaperType
import com.babyschedule.data.Feeding
import com.babyschedule.data.FeedingTimer
import com.babyschedule.data.FeedingType
import com.babyschedule.data.MilkType
import com.babyschedule.data.Segment
import com.babyschedule.data.Segments
import com.babyschedule.data.Session
import com.babyschedule.data.Side
import com.babyschedule.data.Sleep
import com.babyschedule.data.segmentList

enum class FeedKind(val label: String) { NONE("None"), BREAST("Breast"), BOTTLE("Bottle") }

enum class SleepMode(val label: String) { NONE("No"), TIMES("Add times"), NOW("Sleeping now") }

/** Everything in the "Log a session" form, before it is saved. */
data class SessionDraft(
    val sessionId: Long = 0,
    val start: Long,
    val notes: String = "",
    // Diaper (null type = no change)
    val diaperId: Long = 0,
    val diaperType: DiaperType? = null,
    val diaperAmount: String = "",
    val diaperNotes: String = "",
    // Feeding
    val feedingId: Long = 0,
    val feedKind: FeedKind = FeedKind.NONE,
    val segments: List<Segment> = emptyList(),
    val weightBefore: Int? = null,
    val weightAfter: Int? = null,
    val bottleMl: Int? = null,
    val milkType: MilkType = MilkType.FORMULA,
    // Sleep
    val sleepId: Long = 0,
    val sleepMode: SleepMode = SleepMode.NONE,
    val sleepStart: Long? = null,
    val sleepEnd: Long? = null,
    /** Opened from the live feeding timer; saving clears the timer. */
    val fromTimer: Boolean = false,
) {
    val isNew: Boolean get() = sessionId == 0L

    private val breastValid: Boolean get() = segments.isNotEmpty() && segments.all { it.end > it.start }

    val feedValid: Boolean
        get() = when (feedKind) {
            FeedKind.NONE -> true
            FeedKind.BREAST -> breastValid
            FeedKind.BOTTLE -> (bottleMl ?: 0) > 0
        }

    val sleepValid: Boolean
        get() = when (sleepMode) {
            SleepMode.NONE -> true
            SleepMode.TIMES -> sleepStart != null && sleepEnd != null && sleepEnd > sleepStart
            SleepMode.NOW -> sleepStart != null
        }

    val hasContent: Boolean
        get() = diaperType != null || feedKind != FeedKind.NONE || sleepMode != SleepMode.NONE || notes.isNotBlank()

    val canSave: Boolean get() = hasContent && feedValid && sleepValid

    /** When the feed ended, or the session start if there is no breastfeed. */
    val feedEnd: Long get() = if (feedKind == FeedKind.BREAST) segments.maxOfOrNull { it.end } ?: start else start

    val intakeGrams: Int?
        get() {
            val b = weightBefore ?: return null
            val a = weightAfter ?: return null
            return a - b
        }

    /** Moves the whole session (feed and sleep times too) to a new start time. */
    fun shiftedTo(newStart: Long): SessionDraft {
        val d = newStart - start
        return copy(
            start = newStart,
            segments = segments.map { it.copy(start = it.start + d, end = it.end + d) },
            sleepStart = sleepStart?.plus(d),
            sleepEnd = sleepEnd?.plus(d),
        )
    }

    fun toSession(): Session = Session(id = sessionId, startTime = start, notes = notes.trim())

    fun toFeeding(sessionId: Long): Feeding? = when (feedKind) {
        FeedKind.NONE -> null
        FeedKind.BREAST -> {
            val segs = segments.sortedBy { it.start }
            Feeding(
                id = feedingId,
                type = FeedingType.BREAST,
                startTime = segs.first().start,
                endTime = segs.maxOf { it.end },
                leftSeconds = (segs.filter { it.side == Side.LEFT }.sumOf { it.durationMs } / 1000).toInt(),
                rightSeconds = (segs.filter { it.side == Side.RIGHT }.sumOf { it.durationMs } / 1000).toInt(),
                firstSide = segs.first().side,
                lastSide = segs.last().side,
                weightBeforeGrams = weightBefore,
                weightAfterGrams = weightAfter,
                segments = Segments.encode(segs),
                sessionId = sessionId,
            )
        }
        FeedKind.BOTTLE -> Feeding(
            id = feedingId,
            type = FeedingType.BOTTLE,
            startTime = start,
            endTime = start,
            amountMl = bottleMl,
            milkType = milkType,
            sessionId = sessionId,
        )
    }

    fun toDiaper(sessionId: Long): Diaper? = diaperType?.let {
        Diaper(
            id = diaperId,
            time = start,
            type = it,
            amount = diaperAmount,
            notes = diaperNotes.trim(),
            sessionId = sessionId,
        )
    }

    fun toSleep(sessionId: Long): Sleep? {
        if (sleepMode != SleepMode.TIMES) return null
        return Sleep(id = sleepId, startTime = sleepStart ?: return null, endTime = sleepEnd ?: return null, sessionId = sessionId)
    }

    companion object {
        fun new(start: Long) = SessionDraft(start = start)

        /** Builds side segments for feeds saved before side times were recorded. */
        fun segmentsFor(f: Feeding): List<Segment> {
            f.segmentList.takeIf { it.isNotEmpty() }?.let { return it }
            val first = f.firstSide ?: if (f.leftSeconds > 0) Side.LEFT else Side.RIGHT
            val firstMs = (if (first == Side.LEFT) f.leftSeconds else f.rightSeconds) * 1000L
            val secondMs = (if (first == Side.LEFT) f.rightSeconds else f.leftSeconds) * 1000L
            return buildList {
                if (firstMs > 0) add(Segment(first, f.startTime, f.startTime + firstMs))
                if (secondMs > 0) add(Segment(first.other(), f.startTime + firstMs, f.startTime + firstMs + secondMs))
            }
        }

        fun from(
            session: Session?,
            feeding: Feeding?,
            diaper: Diaper?,
            sleep: Sleep?,
            liveSleepStart: Long? = null,
        ): SessionDraft {
            val start = session?.startTime ?: feeding?.startTime ?: diaper?.time ?: sleep?.startTime ?: System.currentTimeMillis()
            return SessionDraft(
                sessionId = session?.id ?: 0,
                start = start,
                notes = listOfNotNull(session?.notes, feeding?.notes).filter { it.isNotBlank() }.joinToString("\n"),
                diaperId = diaper?.id ?: 0,
                diaperType = diaper?.type,
                diaperAmount = diaper?.amount ?: "",
                diaperNotes = listOf(diaper?.color ?: "", diaper?.notes ?: "").filter { it.isNotBlank() }.joinToString(", "),
                feedingId = feeding?.id ?: 0,
                feedKind = when (feeding?.type) {
                    FeedingType.BREAST -> FeedKind.BREAST
                    FeedingType.BOTTLE -> FeedKind.BOTTLE
                    null -> FeedKind.NONE
                },
                segments = feeding?.takeIf { it.type == FeedingType.BREAST }?.let { segmentsFor(it) } ?: emptyList(),
                weightBefore = feeding?.weightBeforeGrams,
                weightAfter = feeding?.weightAfterGrams,
                bottleMl = feeding?.amountMl,
                milkType = feeding?.milkType ?: MilkType.FORMULA,
                sleepId = sleep?.id ?: 0,
                sleepMode = when {
                    sleep != null -> SleepMode.TIMES
                    liveSleepStart != null -> SleepMode.NOW
                    else -> SleepMode.NONE
                },
                sleepStart = sleep?.startTime ?: liveSleepStart,
                sleepEnd = sleep?.endTime,
            )
        }

        /** A draft for a feed that was just timed live. */
        fun fromTimer(timer: FeedingTimer, now: Long): SessionDraft {
            val end = timer.endTime(now)
            return SessionDraft(
                start = timer.sessionStart ?: now,
                feedKind = FeedKind.BREAST,
                segments = timer.segmentsAt(end),
                weightBefore = timer.weightBeforeGrams,
                fromTimer = true,
            )
        }
    }
}
