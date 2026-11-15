package com.environmentmonitor.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    /** Presets first (in the order they were seeded), then custom profiles A to Z. */
    @Query(
        """
        SELECT * FROM profiles
        ORDER BY isPreset DESC,
                 CASE WHEN isPreset = 1 THEN id END,
                 name COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): Profile?

    @Query("SELECT name FROM profiles")
    suspend fun allNames(): List<String>

    @Query("SELECT COUNT(*) FROM profiles WHERE isPreset = 1")
    suspend fun presetCount(): Int

    @Insert
    suspend fun insert(profile: Profile): Long

    @Insert
    suspend fun insertAll(profiles: List<Profile>)

    @Update
    suspend fun update(profile: Profile)

    /** Deletes a custom profile. Presets are never deleted: returns the number of rows removed. */
    @Query("DELETE FROM profiles WHERE id = :id AND isPreset = 0")
    suspend fun deleteCustom(id: Long): Int
}
