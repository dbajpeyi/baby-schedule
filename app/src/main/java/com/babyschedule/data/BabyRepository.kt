package com.babyschedule.data

import kotlinx.coroutines.flow.Flow

/** A session with everything logged in it. */
data class SessionBundle(
    val session: Session,
    val feedings: List<Feeding>,
    val diapers: List<Diaper>,
    val sleeps: List<Sleep>,
)

class BabyRepository(db: AppDatabase, val prefs: AppPrefs) {
    private val sessions = db.sessionDao()
    private val feedings = db.feedingDao()
    private val diapers = db.diaperDao()
    private val sleeps = db.sleepDao()
    private val growth = db.growthDao()

    fun feedings(from: Long, to: Long): Flow<List<Feeding>> = feedings.between(from, to)
    fun diapers(from: Long, to: Long): Flow<List<Diaper>> = diapers.between(from, to)
    fun sleeps(from: Long, to: Long): Flow<List<Sleep>> = sleeps.overlapping(from, to)
    fun growth(from: Long, to: Long): Flow<List<Growth>> = growth.between(from, to)
    fun allGrowth(): Flow<List<Growth>> = growth.all()
    fun sessions(from: Long, to: Long): Flow<List<Session>> = sessions.between(from, to)
    fun sessionFeedings(ids: List<Long>): Flow<List<Feeding>> = feedings.forSessions(ids)
    fun sessionDiapers(ids: List<Long>): Flow<List<Diaper>> = diapers.forSessions(ids)
    fun sessionSleeps(ids: List<Long>): Flow<List<Sleep>> = sleeps.forSessions(ids)

    suspend fun sessionBundle(id: Long): SessionBundle? {
        val session = sessions.get(id) ?: return null
        return SessionBundle(session, feedings.forSession(id), diapers.forSession(id), sleeps.forSession(id))
    }

    suspend fun latestSessionWithoutSleep(from: Long, to: Long): Session? = sessions.latestWithoutSleep(from, to)

    suspend fun save(item: Session): Long =
        if (item.id == 0L) sessions.insert(item) else item.id.also { sessions.update(item) }
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
            is Session -> sessions.insert(item)
        }
    }

    suspend fun delete(item: Any) {
        when (item) {
            is Feeding -> feedings.deleteById(item.id)
            is Diaper -> diapers.deleteById(item.id)
            is Sleep -> sleeps.deleteById(item.id)
            is Growth -> growth.deleteById(item.id)
            is Session -> sessions.deleteById(item.id)
        }
    }
}
