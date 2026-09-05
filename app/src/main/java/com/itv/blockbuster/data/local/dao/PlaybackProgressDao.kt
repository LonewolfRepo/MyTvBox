package com.itv.blockbuster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgressEntity)

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId AND videoId = :videoId LIMIT 1")
    suspend fun getProgress(profileId: Int, serverId: Int, videoId: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId AND movieId = :movieId AND episodeId = '' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMovieProgressByMovieId(profileId: Int, serverId: Int, movieId: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId AND movieId = :movieId AND seasonId = :seasonId AND episodeId = :episodeId LIMIT 1")
    suspend fun getProgressForEpisode(profileId: Int, serverId: Int, movieId: String, seasonId: String, episodeId: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId AND movieId = :movieId")
    suspend fun getProgressForMovie(profileId: Int, serverId: Int, movieId: String): List<PlaybackProgressEntity>

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId ORDER BY timestamp DESC")
    fun getRecentProgress(profileId: Int, serverId: Int): Flow<List<PlaybackProgressEntity>>

    @Query("DELETE FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId")
    suspend fun clearAll(profileId: Int, serverId: Int)

    @Query("DELETE FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId AND videoId = :videoId")
    suspend fun deleteProgress(profileId: Int, serverId: Int, videoId: String)
}