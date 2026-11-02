package com.babyschedule.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Feeding::class, Diaper::class, Sleep::class, Growth::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedingDao(): FeedingDao
    abstract fun diaperDao(): DiaperDao
    abstract fun sleepDao(): SleepDao
    abstract fun growthDao(): GrowthDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "baby_schedule.db").build()
    }
}
