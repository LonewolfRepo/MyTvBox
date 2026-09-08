package com.itv.blockbuster.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.SettingsRepository
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.repository.ProfileRepository
import com.itv.blockbuster.data.repository.ServerRepository
import com.itv.blockbuster.domain.model.Profile
import com.itv.blockbuster.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val prefs: UserPreferencesRepository,
    private val settings: SettingsRepository,
    private val serverRepository: ServerRepository
) : ViewModel() {
    sealed class StartupState {
        object Loading : StartupState()
        data class Resolved(val showPicker: Boolean, val landingRoute: String) : StartupState()
    }

    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = _state.asStateFlow()
    private var profileIdAtStartup: Int = -1

    init {
        viewModelScope.launch {
            profileRepository.ensureDefaultProfile()
            val count = profileRepository.count()
            if (count <= 1) {
                // Only 1 profile exists. Never show the picker.
                // Ensure it is set as the active profile so the app has a valid context.
                val profiles = profileRepository.getAll().first()
                val singleProfile = profiles.firstOrNull()
                if (singleProfile != null) {
                    val currentActive = prefs.activeProfileIdFlow.first()
                    if (currentActive != singleProfile.id) {
                        prefs.setActiveProfileId(singleProfile.id)
                        profileRepository.touch(singleProfile.id)
                    }
                }
                profileIdAtStartup = singleProfile?.id ?: -1
                _state.value = StartupState.Resolved(showPicker = false, landingRoute = resolveLandingRoute())
            } else {
                // More than 1 profile exists.
                val remember = prefs.rememberLastProfileFlow.first()
                val activeId = prefs.activeProfileIdFlow.first()
                val activeExists = activeId > 0 && profileRepository.get(activeId) != null
                // Show picker only if "Remember last profile" is OFF,
                // or if the remembered active profile no longer exists.
                val showPicker = !(remember && activeExists)
                profileIdAtStartup = activeId
                _state.value = StartupState.Resolved(
                    showPicker = showPicker,
                    // When the picker will be shown, the real landing route is resolved
                    // AFTER selection (awaitLandingRoute); HOME is only a placeholder here.
                    landingRoute = if (showPicker) Routes.HOME else resolveLandingRoute()
                )
            }
        }
    }

    /** Resolves the configured landing route for the currently active profile + portal. */
    suspend fun resolveLandingRoute(): String {
        val profileId = prefs.activeProfileIdFlow.first()
        val serverId = serverRepository.getActiveServer().first()?.id ?: 0
        val setting = settings.getString(profileId, serverId, "default_home", "HOME")
        return mapLandingSetting(setting)
    }

    /**
     * Called after the profile picker completes. Waits (briefly) for the picker's
     * setActiveProfileId DataStore write to commit, then resolves the landing route
     * of the SELECTED profile.
     */
    suspend fun awaitLandingRoute(timeoutMs: Long = 500): String {
        val startId = profileIdAtStartup
        withTimeoutOrNull(timeoutMs) { prefs.activeProfileIdFlow.first { it > 0 && it != startId } }
        return resolveLandingRoute()
    }

    // NEW: Safely waits for a specific profile ID to become active, then resolves its route
    suspend fun awaitProfileSwitch(targetProfileId: Int): String {
        prefs.activeProfileIdFlow.first { it == targetProfileId }
        return resolveLandingRoute()
    }

    private fun mapLandingSetting(setting: String): String = when (setting) {
        "LIVE_TV" -> Routes.LIVE_TV
        "MOVIES" -> Routes.MOVIES
        "TV_SHOWS" -> Routes.TV_SHOWS
        "TV_GUIDE" -> Routes.TV_GUIDE
        "FAVORITES" -> Routes.MY_LIST
        "RECENTS" -> Routes.RECENT
        else -> Routes.HOME
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val prefs: UserPreferencesRepository
) : ViewModel() {
    val profiles: StateFlow<List<Profile>> = profileRepository.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeProfileId: StateFlow<Int> = prefs.activeProfileIdFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)
    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()
    fun setEditMode(enabled: Boolean) { _editMode.value = enabled }
    fun selectProfile(profile: Profile) { viewModelScope.launch { prefs.setActiveProfileId(profile.id); profileRepository.touch(profile.id) } }
    fun addProfile(name: String, colorIndex: Int) { viewModelScope.launch { profileRepository.add(name.ifBlank { "Profile ${profiles.value.size + 1}" }, colorIndex) } }
    fun renameProfile(id: Int, name: String, colorIndex: Int) { viewModelScope.launch { profileRepository.rename(id, name.ifBlank { "Profile" }, colorIndex) } }
    fun deleteProfile(id: Int, onResult: (Boolean) -> Unit = {}) { viewModelScope.launch { val deleted = profileRepository.delete(id); if (deleted && activeProfileId.value == id) { val fallback = profiles.value.firstOrNull { it.id != id }; fallback?.let { prefs.setActiveProfileId(it.id) } }; onResult(deleted) } }
    fun canDelete(): Boolean = profiles.value.size > 1
}
