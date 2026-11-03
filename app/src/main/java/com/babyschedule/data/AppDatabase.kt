package com.babyschedule.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Feeding::class, Diaper::class, Sleep::class, Growth::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedingDao(): FeedingDao
    abstract fun diaperDao(): DiaperDao
    abstract fun sleepDao(): SleepDao
    abstract fun growthDao(): GrowthDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "baby_schedule.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: weights, side segments on feeds; colour, amount and session link on diapers. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feedings ADD COLUMN weightBeforeGrams INTEGER")
                db.execSQL("ALTER TABLE feedings ADD COLUMN weightAfterGrams INTEGER")
                db.execSQL("ALTER TABLE feedings ADD COLUMN segments TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE diapers ADD COLUMN color TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE diapers ADD COLUMN amount TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE diapers ADD COLUMN feedingId INTEGER")
            }
        }
    }
}
