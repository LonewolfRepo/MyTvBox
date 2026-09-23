package com.itv.blockbuster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_progress",
    indices = [
        Index(value = ["profileId", "serverId", "videoId"], unique = true),
        // FIX (issue 4 - "Play/Resume button is slow to change, add an
        // index for video id"): the composite index above already covers
        // lookups by videoId (getProgress) efficiently, since its WHERE
        // clause matches this index's column order exactly. The ACTUAL
        // slow path was almost certainly getMovieProgressByMovieId/
        // getProgressForEpisode/getProgressForMovie below - all filter by
        // movieId (getMovieProgressByMovieId specifically is what
        // VodDetailViewModel calls to decide "Play" vs "Resume" for a
        // movie on load), and getProgressForEpisode also filters by
        // seasonId + episodeId on top of that for a series. Extended to
        // include seasonId/episodeId (rather than stopping at movieId) so
        // getProgressForEpisode's exact (movieId, seasonId, episodeId)
        // lookup is fully covered too, not just the movie-only queries -
        // SQLite can still use this same index as a (profileId, serverId,
        // movieId) prefix seek for the movie-only queries below that don't
        // filter on seasonId/episodeId.
        Index(value = ["profileId", "serverId", "movieId", "seasonId", "episodeId"])
    ]
)
data class PlaybackProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileId: Int,
    val serverId: Int,
    val movieId: String,
    val seasonId: String = "",
    val seasonNumber: String = "",
    val episodeId: String = "",
    val episodeNumber: String = "",
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long
)