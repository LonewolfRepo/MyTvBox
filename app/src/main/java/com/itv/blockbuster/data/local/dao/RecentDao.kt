package com.itv.blockbuster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itv.blockbuster.data.local.entity.RecentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recent: RecentEntity)

    @Query("SELECT * FROM recents WHERE profileId = :profileId AND serverId = :serverId ORDER BY timestamp DESC")
    fun getAll(profileId: Int, serverId: Int): Flow<List<RecentEntity>>

    @Query("DELETE FROM recents WHERE profileId = :profileId AND serverId = :serverId AND itemId = :itemId AND type = :type")
    suspend fun delete(profileId: Int, serverId: Int, itemId: String, type: String)

    @Query("DELETE FROM recents WHERE profileId = :profileId AND serverId = :serverId")
    suspend fun clearAll(profileId: Int, serverId: Int)
}