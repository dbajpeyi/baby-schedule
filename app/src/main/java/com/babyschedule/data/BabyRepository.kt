package com.babyschedule.data

import kotlinx.coroutines.flow.Flow

class BabyRepository(db: AppDatabase, val prefs: AppPrefs) {
    private val feedings = db.feedingDao()
    private val diapers = db.diaperDao()
    private val sleeps = db.sleepDao()
    private val growth = db.growthDao()

    fun feedings(from: Long, to: Long): Flow<List<Feeding>> = feedings.between(from, to)
    fun diapers(from: Long, to: Long): Flow<List<Diaper>> = diapers.between(from, to)
    fun sleeps(from: Long, to: Long): Flow<List<Sleep>> = sleeps.overlapping(from, to)
    fun growth(from: Long, to: Long): Flow<List<Growth>> = growth.between(from, to)
    fun allGrowth(): Flow<List<Growth>> = growth.all()
    fun weighedFeedings(): Flow<List<Feeding>> = feedings.weighed()

    fun latestFeeding(): Flow<Feeding?> = feedings.latest()
    fun latestBreastFeeding(): Flow<Feeding?> = feedings.latestBreast()
    fun latestDiaper(): Flow<Diaper?> = diapers.latest()
    fun latestSleep(): Flow<Sleep?> = sleeps.latest()

    suspend fun save(item: Feeding): Long =
        if (item.id == 0L) feedings.insert(item) else item.id.also { feedings.update(item) }

    suspend fun save(item: Diaper): Long =
        if (item.id == 0L) diapers.insert(item) else item.id.also { diapers.update(item) }

    suspend fun save(item: Sleep): Long =
        if (item.id == 0L) sleeps.insert(item) else item.id.also { sleeps.update(item) }

    suspend fun save(item: Growth): Long =
        if (item.id == 0L) growth.insert(item) else item.id.also { growth.update(item) }

    /** Re-inserts a deleted item with its original id (used by "Undo"). */
    suspend fun restore(item: Any) {
        when (item) {
            is Feeding -> feedings.insert(item)
            is Diaper -> diapers.insert(item)
            is Sleep -> sleeps.insert(item)
            is Growth -> growth.insert(item)
        }
    }

    suspend fun delete(item: Any) {
        when (item) {
            is Feeding -> feedings.deleteById(item.id)
            is Diaper -> diapers.deleteById(item.id)
            is Sleep -> sleeps.deleteById(item.id)
            is Growth -> growth.deleteById(item.id)
        }
    }
}
