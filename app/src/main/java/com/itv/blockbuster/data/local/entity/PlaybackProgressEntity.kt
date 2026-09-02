package com.itv.blockbuster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_progress",
    indices = [Index(value = ["profileId", "serverId", "videoId"], unique = true)]
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