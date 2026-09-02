package com.itv.blockbuster.domain.model

data class Profile(
    val id: Int = 0,
    val name: String,
    val colorIndex: Int = 0,
    val createdAt: Long = 0L,
    val lastUsedAt: Long = 0L
)