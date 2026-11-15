package com.environmentmonitor.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {

    @Insert
    suspend fun insert(reading: Reading): Long

    /** Oldest first, ready to draw left to right. */
    @Query("SELECT * FROM readings WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    fun observeForSession(sessionId: Long): Flow<List<Reading>>

    @Query("SELECT COUNT(*) FROM readings WHERE sessionId = :sessionId")
    fun observeCount(sessionId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM readings WHERE sessionId = :sessionId")
    suspend fun count(sessionId: Long): Int
}
