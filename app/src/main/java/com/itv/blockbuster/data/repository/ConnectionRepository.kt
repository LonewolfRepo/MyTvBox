package com.itv.blockbuster.data.repository

import com.itv.blockbuster.domain.model.PortalConnectionResult
import com.itv.blockbuster.domain.model.PortalServerConfig
import com.itv.blockbuster.domain.model.Server
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(
    private val serverRepository: ServerRepository,
    private val portalService: StalkerPortalService
) {

    /**
     * Connects to whichever portal is marked active in Room.
     * Used on app startup and by "Retry" actions.
     */
    suspend fun connectToActiveServer(): Result<PortalConnectionResult> {
        val server = serverRepository.getActiveServer().firstOrNull()
            ?: return Result.failure(
                Exception("No portal configured. Add a portal to start watching.")
            )
        return connectToServer(server)
    }

    /**
     * Bridges the Room [Server] model into the network layer's
     * [PortalServerConfig] and runs the full handshake/profile flow.
     */
    suspend fun connectToServer(server: Server): Result<PortalConnectionResult> {
        return portalService.connect(
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