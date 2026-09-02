package com.itv.blockbuster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val host: String,
    val mac: String,
    val username: String = "",
    val password: String = "",
    val useCredentials: Boolean = false,
    val isActive: Boolean = false
)