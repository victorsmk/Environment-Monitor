package com.environmentmonitor.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    /** Newest first. */
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC, id DESC")
    fun observeAll(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<Session?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): Session?

    /** The session that is recording right now, if any. */
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<Session?>

    @Query("SELECT COUNT(*) FROM sessions WHERE endedAt IS NULL")
    suspend fun activeCount(): Int

    @Insert
    suspend fun insert(session: Session): Long

    @Query("UPDATE sessions SET name = :name, notes = :notes WHERE id = :id")
    suspend fun updateDetails(id: Long, name: String, notes: String): Int

    /** Ends a running session. Does nothing for a session that already ended. */
    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :id AND endedAt IS NULL")
    suspend fun end(id: Long, endedAt: Long): Int

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("UPDATE sessions SET averageScore = :score WHERE id = :id")
    suspend fun setAverageScore(id: Long, score: Int): Int
}
