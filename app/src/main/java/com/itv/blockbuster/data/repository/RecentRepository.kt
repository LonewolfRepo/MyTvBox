package com.itv.blockbuster.data.repository

import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.local.dao.RecentDao
import com.itv.blockbuster.data.local.entity.RecentEntity
import com.itv.blockbuster.data.player.PlaybackManager
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalVodItem
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentRepository @Inject constructor(
    private val recentDao: RecentDao,
    private val prefs: UserPreferencesRepository,
    private val sessionManager: StalkerSessionManager
) {
    suspend fun saveVodRecent(item: PortalVodItem, type: String) {
        val p = prefs.activeProfileIdFlow.first()
        val s = sessionManager.activePortal.value?.serverId ?: 0
        recentDao.upsert(
            RecentEntity(
                profileId = p, serverId = s, itemId = item.id, type = type,
                title = item.name, logoUrl = item.logoUrl,
                contentType = if (type == "SERIES") "series" else "vod",
                description = item.description, director = item.director, actors = item.actors,
                year = item.year, ratingImdb = item.ratingImdb, ratingMpaa = item.ratingMpaa,
                age = item.age, addedDate = item.addedDate, genres = item.genres, country = item.country,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveLiveRecent(channel: PortalChannel) {
        val p = prefs.activeProfileIdFlow.first()
        val s = sessionManager.activePortal.value?.serverId ?: 0
        recentDao.upsert(
            RecentEntity(
                profileId = p, serverId = s, itemId = channel.id, type = "LIVE",
                title = channel.name, logoUrl = channel.logoUrl, cmd = channel.cmd,
                contentType = "live", timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveFromPlayback(manager: PlaybackManager) {
        val p = prefs.activeProfileIdFlow.first()
        val s = sessionManager.activePortal.value?.serverId ?: 0
        if (manager.currentItemId.isEmpty()) return

        recentDao.upsert(
            RecentEntity(
                profileId = p, serverId = s,
                itemId = manager.currentItemId,
                type = manager.currentItemType,
                title = manager.currentTitle,
                logoUrl = manager.currentLogoUrl,
                cmd = manager.currentChannelCmd,
                contentType = manager.currentContentType,
                description = manager.currentDescription,
                director = manager.currentDirector,
                actors = manager.currentActors,
                year = manager.currentYear,
                ratingImdb = manager.currentRatingImdb,
                ratingMpaa = manager.currentRatingMpaa,
                age = manager.currentAge,
                addedDate = manager.currentAddedDate,
                genres = manager.currentGenres,
                country = manager.currentCountry,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun getRecents(profileId: Int, serverId: Int) = recentDao.getAll(profileId, serverId)

    suspend fun deleteRecent(profileId: Int, serverId: Int, itemId: String, type: String) {
        recentDao.delete(profileId, serverId, itemId, type)
    }

    suspend fun clearAll(profileId: Int, serverId: Int) {
        recentDao.clearAll(profileId, serverId)
    }
}