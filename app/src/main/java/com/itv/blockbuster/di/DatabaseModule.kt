package com.itv.blockbuster.di

import android.content.Context
import androidx.room.Room
import com.itv.blockbuster.data.local.AppDatabase
import com.itv.blockbuster.data.local.dao.FavoriteDao
import com.itv.blockbuster.data.local.dao.PlaybackProgressDao
import com.itv.blockbuster.data.local.dao.ProfileDao
import com.itv.blockbuster.data.local.dao.RecentDao
import com.itv.blockbuster.data.local.dao.ServerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "blockbuster.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()
    @Provides fun provideServerDao(db: AppDatabase): ServerDao = db.serverDao()
    @Provides fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun providePlaybackProgressDao(db: AppDatabase): PlaybackProgressDao = db.playbackProgressDao()
    @Provides fun provideRecentDao(db: AppDatabase): RecentDao = db.recentDao()
}