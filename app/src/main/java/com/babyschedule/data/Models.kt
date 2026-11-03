package com.babyschedule.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Side {
    LEFT, RIGHT;

    fun other(): Side = if (this == LEFT) RIGHT else LEFT
    val label: String get() = if (this == LEFT) "Left" else "Right"
}

enum class FeedingType { BREAST, BOTTLE }

enum class MilkType(val label: String) { BREAST_MILK("Breast milk"), FORMULA("Formula") }

enum class DiaperType(val label: String) { WET("Pee"), DIRTY("Poop"), MIXED("Pee + poop") }

/** Simple choices for diaper details; stored as plain text so they stay readable. */
object DiaperOptions {
    val amounts = listOf("Little", "Normal", "Lots")
}

/** One stretch of feeding on one side. */
data class Segment(val side: Side, val start: Long, val end: Long) {
    val durationMs: Long get() = (end - start).coerceAtLeast(0)
}

/** Encodes segments as "LEFT:start:end;RIGHT:start:end" for storage. */
object Segments {
    fun encode(list: List<Segment>): String =
        list.joinToString(";") { "${it.side.name}:${it.start}:${it.end}" }

    fun decode(text: String): List<Segment> =
        if (text.isBlank()) emptyList() else text.split(";").mapNotNull { part ->
            val p = part.split(":")
            if (p.size != 3) return@mapNotNull null
            val side = Side.entries.firstOrNull { it.name == p[0] } ?: return@mapNotNull null
            val start = p[1].toLongOrNull() ?: return@mapNotNull null
            val end = p[2].toLongOrNull() ?: return@mapNotNull null
            Segment(side, start, end)
        }
}

@Entity(tableName = "feedings")
data class Feeding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: FeedingType,
    val startTime: Long,
    val endTime: Long,
    val leftSeconds: Int = 0,
    val rightSeconds: Int = 0,
    val firstSide: Side? = null,
    val lastSide: Side? = null,
    val amountMl: Int? = null,
    val milkType: MilkType? = null,
    val notes: String = "",
    val weightBeforeGrams: Int? = null,
    val weightAfterGrams: Int? = null,
    /** Side switches in order, see [Segments]. Empty for manually entered feeds. */
    val segments: String = "",
    /** The [Session] this feed belongs to, if any. */
    val sessionId: Long? = null,
)

val Feeding.totalSeconds: Int get() = leftSeconds + rightSeconds
val Feeding.segmentList: List<Segment> get() = Segments.decode(segments)

/** Weight gained during the feed (≈ ml of milk drunk), if weighed before and after. */
val Feeding.intakeGrams: Int?
    get() {
        val before = weightBeforeGrams ?: return null
        val after = weightAfterGrams ?: return null
        return after - before
    }

@Entity(tableName = "diapers")
data class Diaper(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val type: DiaperType,
    val notes: String = "",
    /** Unused (colour is written in notes); kept for database compatibility. */
    val color: String = "",
    /** One of [DiaperOptions.amounts] or empty. */
    val amount: String = "",
    /** Legacy link from version 2; replaced by [sessionId]. */
    val feedingId: Long? = null,
    val sessionId: Long? = null,
)

val Diaper.isWet: Boolean get() = type == DiaperType.WET || type == DiaperType.MIXED
val Diaper.isDirty: Boolean get() = type == DiaperType.DIRTY || type == DiaperType.MIXED

@Entity(tableName = "sleeps")
data class Sleep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val notes: String = "",
    val sessionId: Long? = null,
)

val Sleep.durationMs: Long get() = (endTime - startTime).coerceAtLeast(0)

/** A growth measurement. Any of the measurements may be missing. */
@Entity(tableName = "growth")
data class Growth(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val weightGrams: Int? = null,
    val lengthCm: Float? = null,
    val headCm: Float? = null,
    val notes: String = "",
)

/**
 * One care session, e.g. diaper change, weighing, a feed and the nap after it.
 * The feed, diaper and sleep rows point to it through their `sessionId`.
 */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val notes: String = "",
)
