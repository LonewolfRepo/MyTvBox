package com.itv.blockbuster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId AND videoId = :videoId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getProgress(profileId: Int, serverId: Int, videoId: String): PlaybackProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgressEntity)

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId AND movieId = :movieId")
    suspend fun getProgressForMovie(profileId: Int, serverId: Int, movieId: String): List<PlaybackProgressEntity>

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId ORDER BY timestamp DESC")
    fun getRecentProgress(profileId: Int, serverId: Int): Flow<List<PlaybackProgressEntity>>

    @Query("DELETE FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId")
    suspend fun clearAll(profileId: Int, serverId: Int)
}