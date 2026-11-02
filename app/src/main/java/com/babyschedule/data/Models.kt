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

enum class DiaperType(val label: String) { WET("Wet"), DIRTY("Dirty"), MIXED("Wet + dirty") }

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
)

val Feeding.totalSeconds: Int get() = leftSeconds + rightSeconds

@Entity(tableName = "diapers")
data class Diaper(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val type: DiaperType,
    val notes: String = "",
)

val Diaper.isWet: Boolean get() = type == DiaperType.WET || type == DiaperType.MIXED
val Diaper.isDirty: Boolean get() = type == DiaperType.DIRTY || type == DiaperType.MIXED

@Entity(tableName = "sleeps")
data class Sleep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val notes: String = "",
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
