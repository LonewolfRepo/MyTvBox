package com.itv.blockbuster.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object Keys {
        val ACTIVE_PROFILE_ID = intPreferencesKey("active_profile_id")
        val REMEMBER_LAST_PROFILE = booleanPreferencesKey("remember_last_profile")
        val APP_ANIMATIONS = booleanPreferencesKey("app_animations")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
    }

    val activeProfileIdFlow: Flow<Int> = dataStore.data.map { it[Keys.ACTIVE_PROFILE_ID] ?: -1 }

    val rememberLastProfileFlow: Flow<Boolean> =
        dataStore.data.map { it[Keys.REMEMBER_LAST_PROFILE] ?: false }

    val appAnimationsFlow: Flow<Boolean> = dataStore.data.map { it[Keys.APP_ANIMATIONS] ?: true }

    val autoPlayNextFlow: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_PLAY_NEXT] ?: true }

    suspend fun setActiveProfileId(id: Int) {
        dataStore.edit { it[Keys.ACTIVE_PROFILE_ID] = id }
    }

    suspend fun setRememberLastProfile(enabled: Boolean) {
        dataStore.edit { it[Keys.REMEMBER_LAST_PROFILE] = enabled }
    }

    suspend fun setAppAnimations(enabled: Boolean) {
        dataStore.edit { it[Keys.APP_ANIMATIONS] = enabled }
    }

    suspend fun setAutoPlayNext(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_PLAY_NEXT] = enabled }
    }
}