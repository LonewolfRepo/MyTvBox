package com.itv.blockbuster.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.itv.blockbuster.data.local.dao.FavoriteDao
import com.itv.blockbuster.data.local.dao.PlaybackProgressDao
import com.itv.blockbuster.data.local.dao.ProfileDao
import com.itv.blockbuster.data.local.dao.RecentDao
import com.itv.blockbuster.data.local.dao.ServerDao
import com.itv.blockbuster.data.local.entity.FavoriteEntity
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.data.local.entity.ProfileEntity
import com.itv.blockbuster.data.local.entity.RecentEntity
import com.itv.blockbuster.data.local.entity.ServerEntity

@Database(
    entities = [
        ProfileEntity::class,
        ServerEntity::class,
        FavoriteEntity::class,
        PlaybackProgressEntity::class,
        RecentEntity::class // Replaces RecentLiveEntity
    ],
    version = 7, // BUMPED
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun serverDao(): ServerDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun recentDao(): RecentDao // Replaces recentLiveDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "blockbuster.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}