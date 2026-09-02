package com.itv.blockbuster.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.itv.blockbuster.data.local.entity.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers ORDER BY id ASC")
    fun getAllServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE isActive = 1 LIMIT 1")
    fun getActiveServer(): Flow<ServerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity): Long

    @Update
    suspend fun updateServer(server: ServerEntity)

    @Query("UPDATE servers SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE servers SET isActive = 1 WHERE id = :id")
    suspend fun activateServer(id: Int)

    @Delete
    suspend fun deleteServer(server: ServerEntity)
}