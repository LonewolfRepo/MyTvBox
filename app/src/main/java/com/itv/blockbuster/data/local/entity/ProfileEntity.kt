package com.itv.blockbuster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L
)