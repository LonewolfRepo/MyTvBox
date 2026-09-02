package com.itv.blockbuster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recent_live",
    indices = [Index(value = ["profileId", "serverId", "channelId"], unique = true)]
)
data class RecentLiveEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileId: Int,
    val serverId: Int,
    val channelId: String,
    val channelName: String,
    val logoUrl: String? = null,
    val number: String? = null,
    val cmd: String = "",
    val timestamp: Long = System.currentTimeMillis()
)