package com.itv.blockbuster.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.repository.ConnectionRepository
import com.itv.blockbuster.data.repository.ServerRepository
import com.itv.blockbuster.domain.model.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerUiState(
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    val servers: StateFlow<List<Server>> = serverRepository.getAllServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeServer: StateFlow<Server?> = serverRepository.getActiveServer()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    fun addServer(server: Server) {
        viewModelScope.launch {
            val newId = serverRepository.addServer(server)
            connectToServer(server.copy(id = newId.toInt()))
        }
    }

    fun updateServer(server: Server) {
        viewModelScope.launch {
            serverRepository.updateServer(server)
            if (activeServer.value?.id == server.id) {
                connectToServer(server)
            } else {
                _uiState.value = _uiState.value.copy(successMessage = "Portal updated successfully!")
            }
        }
    }

    fun deleteServer(server: Server) {
        viewModelScope.launch {
            serverRepository.deleteServer(server)
            _uiState.value = _uiState.value.copy(successMessage = "Portal deleted.")
        }
    }

    fun activateServer(server: Server) {
        connectToServer(server)
    }

    private fun connectToServer(server: Server) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnecting = true, errorMessage = null, successMessage = null)
            serverRepository.activateServer(server.id)
            val result = connectionRepository.connectToServer(server)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    successMessage = result.getOrNull()?.message ?: "Connected successfully!"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    errorMessage = "Connection failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}