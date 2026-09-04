package com.itv.blockbuster.data.local.dao

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgressInternal(progress: PlaybackProgressEntity)

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId AND videoId = :videoId LIMIT 1")
    suspend fun getProgressInternal(profileId: Int, serverId: Int, videoId: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId AND movieId = :movieId")
    suspend fun getProgressForMovie(profileId: Int, serverId: Int, movieId: String): List<PlaybackProgressEntity>

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId ORDER BY timestamp DESC")
    fun getRecentProgress(profileId: Int, serverId: Int): Flow<List<PlaybackProgressEntity>>

    @Query("DELETE FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId")
    suspend fun clearAll(profileId: Int, serverId: Int)

    @Query("DELETE FROM playback_progress WHERE profileId = :profileId AND serverId = :serverId AND videoId = :videoId")
    suspend fun deleteProgress(profileId: Int, serverId: Int, videoId: String)

    // ── Debug wrappers ──

    suspend fun saveProgress(progress: PlaybackProgressEntity) {
        Log.d("PlaybackProgressDao", "═══════════════════════════════════════════")
        Log.d("PlaybackProgressDao", "SAVE PROGRESS called:")
        Log.d("PlaybackProgressDao", "  profileId      = ${progress.profileId}")
        Log.d("PlaybackProgressDao", "  serverId       = ${progress.serverId}")
        Log.d("PlaybackProgressDao", "  movieId        = ${progress.movieId}")
        Log.d("PlaybackProgressDao", "  seasonId       = ${progress.seasonId}")
        Log.d("PlaybackProgressDao", "  seasonNumber   = ${progress.seasonNumber}")
        Log.d("PlaybackProgressDao", "  episodeId      = ${progress.episodeId}")
        Log.d("PlaybackProgressDao", "  episodeNumber  = ${progress.episodeNumber}")
        Log.d("PlaybackProgressDao", "  videoId        = ${progress.videoId}")
        Log.d("PlaybackProgressDao", "  positionMs     = ${progress.positionMs}")
        Log.d("PlaybackProgressDao", "  durationMs     = ${progress.durationMs}")
        Log.d("PlaybackProgressDao", "  timestamp      = ${progress.timestamp}")
        Log.d("PlaybackProgressDao", "═══════════════════════════════════════════")
        saveProgressInternal(progress)
    }

    suspend fun getProgress(profileId: Int, serverId: Int, videoId: String): PlaybackProgressEntity? {
        Log.d("PlaybackProgressDao", "───────────────────────────────────────────")
        Log.d("PlaybackProgressDao", "GET PROGRESS called:")
        Log.d("PlaybackProgressDao", "  profileId = $profileId")
        Log.d("PlaybackProgressDao", "  serverId  = $serverId")
        Log.d("PlaybackProgressDao", "  videoId   = $videoId")
        val result = getProgressInternal(profileId, serverId, videoId)
        Log.d("PlaybackProgressDao", "  RESULT    = ${if (result != null) "FOUND → positionMs=${result.positionMs}, videoId=${result.videoId}" else "NOT FOUND (null)"}")
        Log.d("PlaybackProgressDao", "───────────────────────────────────────────")
        return result
    }
}