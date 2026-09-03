package com.itv.blockbuster.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.itv.blockbuster.data.local.dao.FavoriteDao
import com.itv.blockbuster.data.local.dao.PlaybackProgressDao
import com.itv.blockbuster.data.local.dao.ProfileDao
import com.itv.blockbuster.data.local.dao.RecentDao
import com.itv.blockbuster.data.local.dao.ServerDao
import com.itv.blockbuster.data.local.entity.FavoriteEntity
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.data.local.entity.ProfileEntity
import com.itv.blockbuster.data.local.entity.RecentLiveEntity
import com.itv.blockbuster.data.local.entity.ServerEntity

@Database(
    entities = [
        ProfileEntity::class,
        ServerEntity::class,
        FavoriteEntity::class,
        PlaybackProgressEntity::class,
        RecentLiveEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun serverDao(): ServerDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun recentDao(): RecentDao
}