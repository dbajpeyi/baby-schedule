package com.babyschedule

import android.app.Application
import com.babyschedule.data.AppDatabase
import com.babyschedule.data.AppPrefs
import com.babyschedule.data.BabyRepository

class BabyScheduleApp : Application() {
    val repository: BabyRepository by lazy {
        BabyRepository(AppDatabase.build(this), AppPrefs(this))
    }
}
