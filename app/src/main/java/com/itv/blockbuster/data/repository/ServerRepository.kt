package com.itv.blockbuster.data.repository

import com.itv.blockbuster.data.local.dao.ServerDao
import com.itv.blockbuster.data.local.entity.ServerEntity
import com.itv.blockbuster.domain.model.Server
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository @Inject constructor(
    private val serverDao: ServerDao
) {

    fun getAllServers(): Flow<List<Server>> =
        serverDao.getAllServers().map { list -> list.map { it.toDomain() } }

    fun getActiveServer(): Flow<Server?> =
        serverDao.getActiveServer().map { it?.toDomain() }

    suspend fun addServer(server: Server): Long = serverDao.insertServer(server.toEntity())

    suspend fun updateServer(server: Server) = serverDao.updateServer(server.toEntity())

    suspend fun deleteServer(server: Server) = serverDao.deleteServer(server.toEntity())

    suspend fun activateServer(id: Int) {
        serverDao.deactivateAll()
        serverDao.activateServer(id)
    }

    private fun ServerEntity.toDomain() = Server(
        id, name, host, mac, username, password, useCredentials, isActive
    )

    private fun Server.toEntity() = ServerEntity(
        id, name, host, mac, username, password, useCredentials, isActive
    )
}