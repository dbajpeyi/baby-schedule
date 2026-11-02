package com.babyschedule

import android.app.Application
import com.babyschedule.data.AppDatabase
import com.babyschedule.data.AppPrefs
import com.babyschedule.data.BabyRepository
import com.babyschedule.notify.SleepAlertScheduler

class BabyScheduleApp : Application() {
    val repository: BabyRepository by lazy {
        BabyRepository(AppDatabase.build(this), AppPrefs(this))
    }

    val sleepAlerts: SleepAlertScheduler by lazy { SleepAlertScheduler(this, repository.prefs) }

    override fun onCreate() {
        super.onCreate()
        sleepAlerts.createChannel()
    }
}
