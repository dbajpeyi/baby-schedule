package com.babyschedule

import android.app.Application
import com.babyschedule.data.AppDatabase
import com.babyschedule.data.AppPrefs
import com.babyschedule.data.BabyRepository
import com.babyschedule.notify.SleepAlertScheduler
import com.babyschedule.widget.BabyWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BabyScheduleApp : Application() {
    val repository: BabyRepository by lazy {
        BabyRepository(AppDatabase.build(this), AppPrefs(this))
    }

    val sleepAlerts: SleepAlertScheduler by lazy { SleepAlertScheduler(this, repository.prefs) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        sleepAlerts.createChannel()
        // Keep the home-screen widget in step with the data while the app is running.
        appScope.launch {
            val prefs = repository.prefs
            combine(
                repository.latestFeeding(),
                repository.latestSleep(),
                prefs.sleepStart,
                prefs.timer,
                prefs.profile,
            ) { _, _, _, _, _ -> }.collect { BabyWidget.updateAll(this@BabyScheduleApp) }
        }
    }
}
