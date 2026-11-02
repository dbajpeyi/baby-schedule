package com.babyschedule.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Profile(
    val name: String = "",
    val birthDate: LocalDate? = null,
    val birthWeightGrams: Int? = null,
) {
    val isSet: Boolean get() = name.isNotBlank() || birthDate != null
}

/**
 * State of an in-progress breastfeeding session. Persisted so the timer keeps
 * running if the app is closed or the phone restarts.
 */
data class FeedingTimer(
    val sessionStart: Long? = null,
    val activeSide: Side? = null,
    val segmentStart: Long = 0,
    val leftMs: Long = 0,
    val rightMs: Long = 0,
    val firstSide: Side? = null,
    val lastSide: Side? = null,
) {
    val isActive: Boolean get() = sessionStart != null
    val isRunning: Boolean get() = activeSide != null

    private fun running(side: Side, now: Long): Long =
        if (activeSide == side) (now - segmentStart).coerceAtLeast(0) else 0

    fun leftAt(now: Long): Long = leftMs + running(Side.LEFT, now)
    fun rightAt(now: Long): Long = rightMs + running(Side.RIGHT, now)
    fun sideAt(side: Side, now: Long): Long = if (side == Side.LEFT) leftAt(now) else rightAt(now)
    fun totalAt(now: Long): Long = leftAt(now) + rightAt(now)

    /** Moves the running segment into the per-side totals. */
    private fun settle(now: Long): FeedingTimer = copy(
        leftMs = leftAt(now),
        rightMs = rightAt(now),
        segmentStart = now,
    )

    /** Tap a side: start it, switch to it, or pause it if it is already running. */
    fun tap(side: Side, now: Long): FeedingTimer {
        val settled = settle(now)
        return if (activeSide == side) {
            settled.copy(activeSide = null)
        } else {
            settled.copy(
                sessionStart = sessionStart ?: now,
                activeSide = side,
                segmentStart = now,
                firstSide = firstSide ?: side,
                lastSide = side,
            )
        }
    }

    /** Starts (or switches to) [side]; does nothing if it is already running. */
    fun startSide(side: Side, now: Long): FeedingTimer = if (activeSide == side) this else tap(side, now)

    fun pause(now: Long): FeedingTimer = if (activeSide == null) this else settle(now).copy(activeSide = null)

    /** When the feed ended: now if running, otherwise the moment it was paused. */
    fun endTime(now: Long): Long = if (isRunning) now else maxOf(segmentStart, sessionStart ?: segmentStart)

    /** Converts the session into a feeding record, or null if nothing was timed. */
    fun toFeeding(now: Long): Feeding? {
        val start = sessionStart ?: return null
        val left = (leftAt(now) / 1000).toInt()
        val right = (rightAt(now) / 1000).toInt()
        if (left + right <= 0) return null
        return Feeding(
            type = FeedingType.BREAST,
            startTime = start,
            endTime = endTime(now),
            leftSeconds = left,
            rightSeconds = right,
            firstSide = firstSide,
            lastSide = lastSide,
        )
    }
}

/** Notify when a sleep has lasted [minutes]. */
data class SleepAlert(val enabled: Boolean = true, val minutes: Int = 120)

class AppPrefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("baby_schedule_prefs", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private val _timer = MutableStateFlow(readTimer())
    val timer: StateFlow<FeedingTimer> = _timer.asStateFlow()

    private val _sleepStart = MutableStateFlow(sp.getLong(K_SLEEP_START, 0L).takeIf { it > 0 })
    val sleepStart: StateFlow<Long?> = _sleepStart.asStateFlow()

    private val _sleepAlert = MutableStateFlow(
        SleepAlert(sp.getBoolean(K_ALERT_ON, true), sp.getInt(K_ALERT_MINUTES, 120))
    )
    val sleepAlert: StateFlow<SleepAlert> = _sleepAlert.asStateFlow()

    fun setSleepAlert(a: SleepAlert) {
        sp.edit().putBoolean(K_ALERT_ON, a.enabled).putInt(K_ALERT_MINUTES, a.minutes).apply()
        _sleepAlert.value = a
    }

    /** The sleep start for which the alert was already shown (to avoid repeats). */
    var sleepAlertShownFor: Long
        get() = sp.getLong(K_ALERT_SHOWN_FOR, 0L)
        set(v) {
            sp.edit().putLong(K_ALERT_SHOWN_FOR, v).apply()
        }

    fun saveProfile(p: Profile) {
        sp.edit()
            .putString(K_NAME, p.name)
            .putLongOrRemove(K_BIRTH, p.birthDate?.toEpochDay())
            .putLongOrRemove(K_BIRTH_WEIGHT, p.birthWeightGrams?.toLong())
            .apply()
        _profile.value = p
    }

    fun setTimer(t: FeedingTimer) {
        sp.edit()
            .putLongOrRemove(K_T_START, t.sessionStart)
            .putString(K_T_ACTIVE, t.activeSide?.name)
            .putLong(K_T_SEGMENT, t.segmentStart)
            .putLong(K_T_LEFT, t.leftMs)
            .putLong(K_T_RIGHT, t.rightMs)
            .putString(K_T_FIRST, t.firstSide?.name)
            .putString(K_T_LAST, t.lastSide?.name)
            .apply()
        _timer.value = t
    }

    fun setSleepStart(start: Long?) {
        sp.edit().putLongOrRemove(K_SLEEP_START, start).apply()
        _sleepStart.value = start
    }

    private fun readProfile() = Profile(
        name = sp.getString(K_NAME, "") ?: "",
        birthDate = sp.longOrNull(K_BIRTH)?.let { LocalDate.ofEpochDay(it) },
        birthWeightGrams = sp.longOrNull(K_BIRTH_WEIGHT)?.toInt(),
    )

    private fun readTimer() = FeedingTimer(
        sessionStart = sp.longOrNull(K_T_START),
        activeSide = sp.sideOrNull(K_T_ACTIVE),
        segmentStart = sp.getLong(K_T_SEGMENT, 0),
        leftMs = sp.getLong(K_T_LEFT, 0),
        rightMs = sp.getLong(K_T_RIGHT, 0),
        firstSide = sp.sideOrNull(K_T_FIRST),
        lastSide = sp.sideOrNull(K_T_LAST),
    )

    private fun SharedPreferences.longOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0) else null

    private fun SharedPreferences.sideOrNull(key: String): Side? =
        getString(key, null)?.let { name -> Side.entries.firstOrNull { it.name == name } }

    private fun SharedPreferences.Editor.putLongOrRemove(key: String, v: Long?) =
        if (v == null) remove(key) else putLong(key, v)

    private companion object {
        const val K_NAME = "name"
        const val K_BIRTH = "birth_epoch_day"
        const val K_BIRTH_WEIGHT = "birth_weight_g"
        const val K_SLEEP_START = "sleep_start"
        const val K_ALERT_ON = "sleep_alert_on"
        const val K_ALERT_MINUTES = "sleep_alert_minutes"
        const val K_ALERT_SHOWN_FOR = "sleep_alert_shown_for"
        const val K_T_START = "timer_start"
        const val K_T_ACTIVE = "timer_active"
        const val K_T_SEGMENT = "timer_segment"
        const val K_T_LEFT = "timer_left"
        const val K_T_RIGHT = "timer_right"
        const val K_T_FIRST = "timer_first"
        const val K_T_LAST = "timer_last"
    }
}
