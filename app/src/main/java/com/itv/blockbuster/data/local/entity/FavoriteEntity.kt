package com.itv.blockbuster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileId: Int,
    val serverId: Int,
    val itemId: String,
    val title: String,
    val type: String, // "LIVE", "VOD", "SERIES"
    val logoUrl: String = "",
    val cmd: String = "",
    val categoryId: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val year: String = "",
    val duration: String = "",
    val ratingImdb: String = "",
    val ratingMpaa: String = "",
    val genres: String = ""
)