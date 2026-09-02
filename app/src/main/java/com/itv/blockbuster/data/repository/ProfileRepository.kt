package com.itv.blockbuster.data.repository

import com.itv.blockbuster.data.local.dao.ProfileDao
import com.itv.blockbuster.data.local.entity.ProfileEntity
import com.itv.blockbuster.domain.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao
) {

    fun getAll(): Flow<List<Profile>> =
        profileDao.getAll().map { list -> list.map { it.toDomain() } }

    suspend fun ensureDefaultProfile() {
        if (profileDao.getCount() == 0) {
            profileDao.insert(ProfileEntity(name = "Profile 1", colorIndex = 0))
        }
    }

    suspend fun get(id: Int): Profile? = profileDao.get(id)?.toDomain()

    suspend fun count(): Int = profileDao.getCount()

    suspend fun add(name: String, colorIndex: Int): Long =
        profileDao.insert(ProfileEntity(name = name, colorIndex = colorIndex))

    suspend fun rename(id: Int, name: String, colorIndex: Int) =
        profileDao.updateProfile(id, name, colorIndex)

    /** Returns false when attempting to delete the last remaining profile. */
    suspend fun delete(id: Int): Boolean {
        if (profileDao.getCount() <= 1) return false
        profileDao.delete(id)
        return true
    }

    suspend fun touch(id: Int) = profileDao.updateLastUsed(id, System.currentTimeMillis())

    private fun ProfileEntity.toDomain() = Profile(
        id = id,
        name = name,
        colorIndex = colorIndex,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt
    )
}