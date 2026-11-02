package com.babyschedule.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedingDao {
    @Query("SELECT * FROM feedings WHERE startTime >= :from AND startTime < :to ORDER BY startTime DESC")
    fun between(from: Long, to: Long): Flow<List<Feeding>>

    @Query("SELECT * FROM feedings ORDER BY startTime DESC LIMIT 1")
    fun latest(): Flow<Feeding?>

    @Query("SELECT * FROM feedings WHERE type = 'BREAST' ORDER BY startTime DESC LIMIT 1")
    fun latestBreast(): Flow<Feeding?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Feeding): Long

    @Update
    suspend fun update(item: Feeding)

    @Query("DELETE FROM feedings WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DiaperDao {
    @Query("SELECT * FROM diapers WHERE time >= :from AND time < :to ORDER BY time DESC")
    fun between(from: Long, to: Long): Flow<List<Diaper>>

    @Query("SELECT * FROM diapers ORDER BY time DESC LIMIT 1")
    fun latest(): Flow<Diaper?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Diaper): Long

    @Update
    suspend fun update(item: Diaper)

    @Query("DELETE FROM diapers WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SleepDao {
    /** Sleeps that overlap the [from, to) window, including ones crossing midnight. */
    @Query("SELECT * FROM sleeps WHERE endTime > :from AND startTime < :to ORDER BY startTime DESC")
    fun overlapping(from: Long, to: Long): Flow<List<Sleep>>

    @Query("SELECT * FROM sleeps ORDER BY endTime DESC LIMIT 1")
    fun latest(): Flow<Sleep?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Sleep): Long

    @Update
    suspend fun update(item: Sleep)

    @Query("DELETE FROM sleeps WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface GrowthDao {
    @Query("SELECT * FROM growth ORDER BY time ASC")
    fun all(): Flow<List<Growth>>

    @Query("SELECT * FROM growth WHERE time >= :from AND time < :to ORDER BY time DESC")
    fun between(from: Long, to: Long): Flow<List<Growth>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Growth): Long

    @Update
    suspend fun update(item: Growth)

    @Query("DELETE FROM growth WHERE id = :id")
    suspend fun deleteById(id: Long)
}
