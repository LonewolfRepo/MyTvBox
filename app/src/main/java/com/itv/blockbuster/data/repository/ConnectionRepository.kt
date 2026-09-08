package com.itv.blockbuster.data.repository

import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.PortalConnectionResult
import com.itv.blockbuster.domain.model.PortalServerConfig
import com.itv.blockbuster.domain.model.Server
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(
    private val serverRepository: ServerRepository,
    private val portalService: StalkerPortalService,
    private val sessionManager: StalkerSessionManager
) {
    private val connectMutex = Mutex()

    suspend fun connectToActiveServer(): Result<PortalConnectionResult> {
        val server = serverRepository.getActiveServer().firstOrNull()
            ?: return Result.failure(
                Exception("No portal configured. Add a portal to start watching.")
            )
        return connectToServer(server)
    }

    suspend fun connectToServer(server: Server): Result<PortalConnectionResult> = connectMutex.withLock {
        // Short-circuit if we are already connected to this exact server
        val alreadyConnected = sessionManager.ajaxLoader.value.isNotEmpty() &&
                sessionManager.activePortal.value?.serverId == server.id
        if (alreadyConnected) {
            return@withLock Result.success(
                PortalConnectionResult(
                    portalPath = sessionManager.portalDir.value,
                    token = sessionManager.bearerToken.value ?: "",
                    status = "OK",
                    message = "Session already active"
                )
            )
        }

        return@withLock portalService.connect(
            PortalServerConfig(
                id = server.id,
                name = server.name,
                host = server.host,
                mac = server.mac,
                username = server.username,
                password = server.password,
                useCredentials = server.useCredentials
            )
        )
    }
}