package com.itv.blockbuster.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.repository.ProfileRepository
import com.itv.blockbuster.domain.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val prefs: UserPreferencesRepository
) : ViewModel() {

    sealed class StartupState {
        object Loading : StartupState()
        data class Resolved(val showPicker: Boolean) : StartupState()
    }

    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.ensureDefaultProfile()

            val remember = prefs.rememberLastProfileFlow.first()
            val activeId = prefs.activeProfileIdFlow.first()
            val activeExists = activeId > 0 && profileRepository.get(activeId) != null

            _state.value = StartupState.Resolved(
                showPicker = !(remember && activeExists)
            )
        }
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val prefs: UserPreferencesRepository
) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = profileRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfileId: StateFlow<Int> = prefs.activeProfileIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    fun setEditMode(enabled: Boolean) {
        _editMode.value = enabled
    }

    fun selectProfile(profile: Profile) {
        viewModelScope.launch {
            prefs.setActiveProfileId(profile.id)
            profileRepository.touch(profile.id)
        }
    }

    fun addProfile(name: String, colorIndex: Int) {
        viewModelScope.launch {
            profileRepository.add(name.ifBlank { "Profile ${profiles.value.size + 1}" }, colorIndex)
        }
    }

    fun renameProfile(id: Int, name: String, colorIndex: Int) {
        viewModelScope.launch {
            profileRepository.rename(id, name.ifBlank { "Profile" }, colorIndex)
        }
    }

    /** Returns false if deletion was blocked (last profile). */
    fun deleteProfile(id: Int, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val deleted = profileRepository.delete(id)
            if (deleted && activeProfileId.value == id) {
                val fallback = profiles.value.firstOrNull { it.id != id }
                fallback?.let { prefs.setActiveProfileId(it.id) }
            }
            onResult(deleted)
        }
    }

    fun canDelete(): Boolean = profiles.value.size > 1
}