package com.itv.blockbuster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itv.blockbuster.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND serverId = :serverId AND type = :type ORDER BY timestamp DESC")
    fun getFavorites(profileId: Int, serverId: Int, type: String): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE profileId = :profileId AND serverId = :serverId AND itemId = :itemId)")
    fun isFavorite(profileId: Int, serverId: Int, itemId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND serverId = :serverId AND itemId = :itemId")
    suspend fun removeFavorite(profileId: Int, serverId: Int, itemId: String)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND serverId = :serverId AND type = :type")
    suspend fun clearFavorites(profileId: Int, serverId: Int, type: String)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND serverId = :serverId")
    suspend fun clearAll(profileId: Int, serverId: Int)
}