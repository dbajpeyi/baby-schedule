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
import com.babyschedule.data.Sleep
import com.babyschedule.data.SleepAlert
import com.babyschedule.notify.SleepAlertScheduler
import com.babyschedule.domain.DayStats
import com.babyschedule.domain.GrowthSummary
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything logged on one calendar day. */
data class DayData(
    val date: LocalDate,
    val feedings: List<Feeding> = emptyList(),
    val diapers: List<Diaper> = emptyList(),
    val sleeps: List<Sleep> = emptyList(),
    val growth: List<Growth> = emptyList(),
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
    val sleepAlert: StateFlow<SleepAlert> = repo.prefs.sleepAlert

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

    val growthSummary: StateFlow<GrowthSummary> = combine(repo.allGrowth(), profile) { g, p ->
        StatsCalculator.growth(g, p)
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
        return combine(
            repo.feedings(start, end),
            repo.diapers(start, end),
            repo.sleeps(start, end),
            repo.growth(start, end),
        ) { f, d, s, g -> DayData(date, f, d, s, g) }
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

    fun startFeeding(side: Side) = repo.prefs.setTimer(FeedingTimer().tap(side, now()))

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

    data class SavedSession(val feedingId: Long, val diaperId: Long?, val previous: FeedingTimer)

    /** Saves the current session (and an optional diaper change). */
    suspend fun saveSession(diaper: DiaperType?, notes: String): SavedSession? {
        val previous = timer.value
        val feeding = previous.toFeeding(now())?.copy(notes = notes.trim()) ?: return null
        repo.prefs.setTimer(FeedingTimer())
        val feedingId = repo.save(feeding)
        val diaperId = diaper?.let { repo.save(Diaper(time = feeding.endTime, type = it)) }
        return SavedSession(feedingId, diaperId, previous)
    }

    suspend fun undoSession(saved: SavedSession) {
        repo.delete(Feeding(id = saved.feedingId, type = com.babyschedule.data.FeedingType.BREAST, startTime = 0, endTime = 0))
        saved.diaperId?.let { repo.delete(Diaper(id = it, time = 0, type = DiaperType.WET)) }
        repo.prefs.setTimer(saved.previous)
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

    suspend fun stopSleep(): Sleep? {
        val start = sleepStart.value ?: return null
        repo.prefs.setSleepStart(null)
        sleepAlerts.sync()
        val sleep = Sleep(startTime = start, endTime = now())
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
