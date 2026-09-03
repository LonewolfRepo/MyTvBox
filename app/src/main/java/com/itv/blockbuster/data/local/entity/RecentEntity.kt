package com.itv.blockbuster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recents",
    indices = [Index(value = ["profileId", "serverId", "itemId", "type"], unique = true)]
)
data class RecentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileId: Int,
    val serverId: Int,
    val itemId: String,
    val type: String,
    val title: String,
    val logoUrl: String = "",
    val cmd: String = "",
    val categoryId: String = "",
    val contentType: String = "",
    val duration: String = "",
    val description: String = "",
    val director: String = "",
    val actors: String = "",
    val year: String = "",
    val ratingImdb: String = "",
    val ratingMpaa: String = "",
    val age: String = "",
    val addedDate: String = "",
    val genres: String = "",
    val country: String = "",
    val timestamp: Long
)