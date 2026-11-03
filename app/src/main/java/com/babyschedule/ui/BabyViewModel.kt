package com.babyschedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.babyschedule.BabyScheduleApp
import com.babyschedule.data.BabyRepository
import com.babyschedule.data.Diaper
import com.babyschedule.data.DiaperType
import com.babyschedule.data.Feeding
import com.babyschedule.data.FeedingTimer
import com.babyschedule.data.Growth
import com.babyschedule.data.Profile
import com.babyschedule.data.Side
import com.babyschedule.data.FeedingType
import com.babyschedule.data.Session
import com.babyschedule.data.SessionBundle
import com.babyschedule.data.Sleep
import com.babyschedule.data.SleepAlert
import com.babyschedule.notify.SleepAlertScheduler
import com.babyschedule.domain.DayStats
import com.babyschedule.domain.GrowthSummary
import com.babyschedule.domain.SessionDraft
import com.babyschedule.domain.SleepMode
import com.babyschedule.domain.Stats
import com.babyschedule.domain.StatsCalculator
import com.babyschedule.util.startOfDay
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything logged on one calendar day. */
data class DayData(
    val date: LocalDate,
    val feedings: List<Feeding> = emptyList(),
    val diapers: List<Diaper> = emptyList(),
    val sleeps: List<Sleep> = emptyList(),
    val growth: List<Growth> = emptyList(),
    /** Sessions that started on this day. */
    val sessions: List<Session> = emptyList(),
    /** Everything belonging to [sessions], even if it falls on another day. */
    val sessionFeedings: List<Feeding> = emptyList(),
    val sessionDiapers: List<Diaper> = emptyList(),
    val sessionSleeps: List<Sleep> = emptyList(),
) {
    val summary: DayStats by lazy { StatsCalculator.day(date, feedings, diapers, sleeps) }
}

class BabyViewModel(
    private val repo: BabyRepository,
    private val sleepAlerts: SleepAlertScheduler,
) : ViewModel() {

    val profile: StateFlow<Profile> = repo.prefs.profile
    val timer: StateFlow<FeedingTimer> = repo.prefs.timer
    val sleepStart: StateFlow<Long?> = repo.prefs.sleepStart
    val sleepSessionId: StateFlow<Long?> = repo.prefs.sleepSessionId
    val sleepAlert: StateFlow<SleepAlert> = repo.prefs.sleepAlert
    val confetti: StateFlow<Boolean> = repo.prefs.confetti

    fun setConfetti(on: Boolean) = repo.prefs.setConfetti(on)

    /** Emits the current date and changes at midnight. */
    private val today: StateFlow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(30_000)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    val todayData: StateFlow<DayData> = today
        .flatMapLatest { dayData(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayData(LocalDate.now()))

    private val _selectedDay = MutableStateFlow(LocalDate.now())
    val selectedDay: StateFlow<LocalDate> = _selectedDay

    val historyData: StateFlow<DayData> = _selectedDay
        .flatMapLatest { dayData(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayData(LocalDate.now()))

    private val _statsRange = MutableStateFlow(7)
    val statsRange: StateFlow<Int> = _statsRange

    val stats: StateFlow<Stats?> = combine(_statsRange, today) { range, day -> range to day }
        .flatMapLatest { (range, day) ->
            val from = day.minusDays(range - 1L)
            val start = startOfDay(from)
            val end = startOfDay(day.plusDays(1))
            combine(
                repo.feedings(start, end),
                repo.diapers(start, end),
                repo.sleeps(start, end),
            ) { f, d, s -> StatsCalculator.compute(from, day, f, d, s) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val growthEntries: StateFlow<List<Growth>> = repo.allGrowth()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val growthSummary: StateFlow<GrowthSummary> = combine(repo.allGrowth(), profile, repo.weighedFeedings()) { g, p, f ->
        StatsCalculator.growth(g, p, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsCalculator.growth(emptyList(), Profile()))

    val lastFeeding: StateFlow<Feeding?> = repo.latestFeeding()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lastBreastFeeding: StateFlow<Feeding?> = repo.latestBreastFeeding()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lastDiaper: StateFlow<Diaper?> = repo.latestDiaper()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lastSleep: StateFlow<Sleep?> = repo.latestSleep()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun dayData(date: LocalDate): Flow<DayData> {
        val start = startOfDay(date)
        val end = startOfDay(date.plusDays(1))
        val base = combine(
            repo.feedings(start, end),
            repo.diapers(start, end),
            repo.sleeps(start, end),
            repo.growth(start, end),
            repo.sessions(start, end),
        ) { f, d, s, g, sessions -> DayData(date, f, d, s, g, sessions) }
        return base.flatMapLatest { day ->
            val ids = day.sessions.map { it.id }
            if (ids.isEmpty()) {
                flowOf(day)
            } else {
                combine(
                    repo.sessionFeedings(ids),
                    repo.sessionDiapers(ids),
                    repo.sessionSleeps(ids),
                ) { f, d, s -> day.copy(sessionFeedings = f, sessionDiapers = d, sessionSleeps = s) }
            }
        }
    }

    // ---- navigation state ----

    fun selectDay(date: LocalDate) {
        _selectedDay.value = if (date.isAfter(LocalDate.now())) LocalDate.now() else date
    }

    fun setStatsRange(days: Int) {
        _statsRange.value = days
    }

    // ---- breastfeeding timer ----

    private fun now() = System.currentTimeMillis()

    fun startFeeding(side: Side, weightBeforeGrams: Int?) =
        repo.prefs.setTimer(FeedingTimer(weightBeforeGrams = weightBeforeGrams).tap(side, now()))

    /** Switches to the other breast (or starts the other one if paused). */
    fun switchSide() {
        val t = timer.value
        val current = t.activeSide ?: t.lastSide ?: Side.RIGHT
        repo.prefs.setTimer(t.startSide(current.other(), now()))
    }

    fun pauseTimer() = repo.prefs.setTimer(timer.value.pause(now()))

    fun resumeTimer() {
        val t = timer.value
        repo.prefs.setTimer(t.startSide(t.lastSide ?: Side.LEFT, now()))
    }

    fun discardTimer() = repo.prefs.setTimer(FeedingTimer())

    // ---- sessions ----

    suspend fun loadSessionDraft(id: Long): SessionDraft? {
        val b = repo.sessionBundle(id) ?: return null
        val live = if (sleepSessionId.value == id) sleepStart.value else null
        return SessionDraft.from(b.session, b.feedings.firstOrNull(), b.diapers.firstOrNull(), b.sleeps.firstOrNull(), live)
    }

    /** Saves the form: the session plus its feed, diaper and sleep (adding, updating or removing each). */
    suspend fun saveSession(d: SessionDraft): Long {
        val id = repo.save(d.toSession())

        val feeding = d.toFeeding(id)
        if (feeding != null) repo.save(feeding)
        else if (d.feedingId != 0L) repo.delete(Feeding(id = d.feedingId, type = FeedingType.BREAST, startTime = 0, endTime = 0))

        val diaper = d.toDiaper(id)
        if (diaper != null) repo.save(diaper)
        else if (d.diaperId != 0L) repo.delete(Diaper(id = d.diaperId, time = 0, type = DiaperType.WET))

        val sleep = d.toSleep(id)
        if (sleep != null) repo.save(sleep)
        else if (d.sleepId != 0L) repo.delete(Sleep(id = d.sleepId, startTime = 0, endTime = 0))

        val liveForThis = sleepSessionId.value == id
        if (d.sleepMode == SleepMode.NOW) {
            repo.prefs.setSleepStart(d.sleepStart, id)
            sleepAlerts.sync()
        } else if (liveForThis) {
            repo.prefs.setSleepStart(null)
            sleepAlerts.sync()
        }
        if (d.fromTimer) repo.prefs.setTimer(FeedingTimer())
        return id
    }

    suspend fun deleteSession(id: Long): SessionBundle? {
        val b = repo.sessionBundle(id) ?: return null
        (b.feedings + b.diapers + b.sleeps).forEach { repo.delete(it) }
        repo.delete(b.session)
        if (sleepSessionId.value == id) repo.prefs.setSleepStart(sleepStart.value, null)
        return b
    }

    suspend fun restoreSession(b: SessionBundle) {
        repo.restore(b.session)
        (b.feedings + b.diapers + b.sleeps).forEach { repo.restore(it) }
    }

    // ---- sleep timer ----

    fun startSleep() {
        repo.prefs.setSleepStart(now())
        sleepAlerts.sync()
    }

    fun cancelSleep() {
        repo.prefs.setSleepStart(null)
        sleepAlerts.sync()
    }

    fun setSleepAlert(alert: SleepAlert) {
        repo.prefs.setSleepAlert(alert)
        sleepAlerts.sync()
    }

    /**
     * A recent session (started up to 3 hours before the running sleep) that this
     * sleep could be added to. Null if the sleep is already linked or there is none.
     */
    suspend fun sessionToAttachSleep(): Session? {
        if (sleepSessionId.value != null) return null
        val start = sleepStart.value ?: return null
        return repo.latestSessionWithoutSleep(start - 3 * 3_600_000L, start)
    }

    /** Ends the running sleep, linking it to [attachTo] (or the session it was started from). */
    suspend fun stopSleep(attachTo: Long? = null): Sleep? {
        val start = sleepStart.value ?: return null
        val sessionId = sleepSessionId.value ?: attachTo
        repo.prefs.setSleepStart(null)
        sleepAlerts.sync()
        val sleep = Sleep(startTime = start, endTime = now(), sessionId = sessionId)
        val id = repo.save(sleep)
        return sleep.copy(id = id)
    }

    // ---- entries ----

    fun save(item: Feeding) = viewModelScope.launch { repo.save(item) }
    fun save(item: Diaper) = viewModelScope.launch { repo.save(item) }
    fun save(item: Sleep) = viewModelScope.launch { repo.save(item) }
    fun save(item: Growth) = viewModelScope.launch { repo.save(item) }

    suspend fun delete(item: Any) = repo.delete(item)
    suspend fun restore(item: Any) = repo.restore(item)

    fun saveProfile(p: Profile) = repo.prefs.saveProfile(p)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BabyScheduleApp
                BabyViewModel(app.repository, app.sleepAlerts)
            }
        }
    }
}
