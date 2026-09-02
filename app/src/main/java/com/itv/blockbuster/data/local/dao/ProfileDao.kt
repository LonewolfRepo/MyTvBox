package com.itv.blockbuster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.itv.blockbuster.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun getAll(): Flow<List<ProfileEntity>>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getCount(): Int

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Int): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Query("UPDATE profiles SET name = :name, colorIndex = :colorIndex WHERE id = :id")
    suspend fun updateProfile(id: Int, name: String, colorIndex: Int)

    @Query("UPDATE profiles SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: Int, timestamp: Long)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Int)
}