package com.itv.blockbuster.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    // ── Device-global keys ──
    private val SAVE_LOGIN = booleanPreferencesKey("save_login")

    val saveLoginFlow: Flow<Boolean> = dataStore.data.map { it[SAVE_LOGIN] ?: false }

    suspend fun setSaveLogin(enabled: Boolean) {
        dataStore.edit { it[SAVE_LOGIN] = enabled }
    }

    // ── Profile + Server scoped key factories ──
    private fun bk(p: Int, s: Int, n: String) = booleanPreferencesKey("p${p}_s${s}_$n")
    private fun ik(p: Int, s: Int, n: String) = intPreferencesKey("p${p}_s${s}_$n")
    private fun sk(p: Int, s: Int, n: String) = stringPreferencesKey("p${p}_s${s}_$n")

    suspend fun getBool(p: Int, s: Int, n: String, def: Boolean): Boolean =
        dataStore.data.first()[bk(p, s, n)] ?: def

    suspend fun setBool(p: Int, s: Int, n: String, v: Boolean) {
        dataStore.edit { it[bk(p, s, n)] = v }
    }

    suspend fun getInt(p: Int, s: Int, n: String, def: Int): Int =
        dataStore.data.first()[ik(p, s, n)] ?: def

    suspend fun setInt(p: Int, s: Int, n: String, v: Int) {
        dataStore.edit { it[ik(p, s, n)] = v }
    }

    suspend fun getString(p: Int, s: Int, n: String, def: String): String =
        dataStore.data.first()[sk(p, s, n)] ?: def

    suspend fun setString(p: Int, s: Int, n: String, v: String) {
        dataStore.edit { it[sk(p, s, n)] = v }
    }
}