package com.itv.blockbuster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itv.blockbuster.data.local.entity.RecentLiveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDao {

    @Query("SELECT * FROM recent_live WHERE profileId = :profileId AND serverId = :serverId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLive(profileId: Int, serverId: Int, limit: Int): Flow<List<RecentLiveEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recent: RecentLiveEntity)

    @Query("DELETE FROM recent_live WHERE profileId = :profileId AND serverId = :serverId AND channelId = :channelId")
    suspend fun deleteRecent(profileId: Int, serverId: Int, channelId: String)

    @Query("DELETE FROM recent_live WHERE profileId = :profileId AND serverId = :serverId")
    suspend fun clearRecent(profileId: Int, serverId: Int)
}