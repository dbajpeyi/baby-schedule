package com.babyschedule.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Feeding::class, Diaper::class, Sleep::class, Growth::class, Session::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedingDao(): FeedingDao
    abstract fun diaperDao(): DiaperDao
    abstract fun sleepDao(): SleepDao
    abstract fun growthDao(): GrowthDao
    abstract fun sessionDao(): SessionDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "baby_schedule.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

        /**
         * v3: sessions. Every existing breastfeed becomes a session (same id), taking
         * its notes and any diaper that was logged with it.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startTime` INTEGER NOT NULL, `notes` TEXT NOT NULL)"
                )
                db.execSQL("ALTER TABLE feedings ADD COLUMN sessionId INTEGER")
                db.execSQL("ALTER TABLE diapers ADD COLUMN sessionId INTEGER")
                db.execSQL("ALTER TABLE sleeps ADD COLUMN sessionId INTEGER")
                db.execSQL(
                    "INSERT INTO sessions (id, startTime, notes) " +
                        "SELECT id, startTime, notes FROM feedings WHERE type = 'BREAST'"
                )
                db.execSQL("UPDATE feedings SET sessionId = id, notes = '' WHERE type = 'BREAST'")
                db.execSQL(
                    "UPDATE diapers SET sessionId = feedingId " +
                        "WHERE feedingId IN (SELECT id FROM feedings WHERE type = 'BREAST')"
                )
            }
        }
    }
}
