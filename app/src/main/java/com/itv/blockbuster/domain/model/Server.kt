package com.itv.blockbuster.domain.model

data class Server(
    val id: Int = 0,
    val name: String,
    val host: String,
    val mac: String,
    val username: String = "",
    val password: String = "",
    val useCredentials: Boolean = false,
    val isActive: Boolean = false
)