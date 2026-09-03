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
    val description: String = "",
    val director: String = "",
    val actors: String = "",
    val year: String = "",
    val duration: String = "",
    val ratingImdb: String = "",
    val ratingMpaa: String = "",
    val age: String = "",
    val addedDate: String = "",
    val genres: String = "",
    val country: String = "",          // ADDED
    val timestamp: Long = System.currentTimeMillis()
)