package com.itv.blockbuster.data.repository

import com.itv.blockbuster.data.local.dao.FavoriteDao
import com.itv.blockbuster.data.local.dao.PlaybackProgressDao
import com.itv.blockbuster.data.local.entity.FavoriteEntity
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalPage
import com.itv.blockbuster.domain.model.PortalVodItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VodRepository @Inject constructor(
    private val portalService: StalkerPortalService,
    private val sessionManager: StalkerSessionManager,
    private val favoriteDao: FavoriteDao,
    private val progressDao: PlaybackProgressDao
) {

    // Always fetch "vod" categories - filtering happens client-side
    suspend fun getCategories(): Result<List<PortalCategory>> {
        return portalService.fetchVodCategories()
    }

    // FIX: Fetch all items with type=vod, then filter by isSeries attribute
    suspend fun getList(contentType: String, categoryId: String, page: Int, pageSize: Int = 20): Result<PortalPage<PortalVodItem>> {
        val result = portalService.fetchVodList(categoryId, page, pageSize)
        return result.map { page ->
            val filteredItems = when (contentType) {
                "series" -> page.items.filter { it.isSeries } // TV Shows only
                else -> page.items.filter { !it.isSeries }     // Movies only (is_series = false/0/absent)
            }
            PortalPage(filteredItems, filteredItems.size)
        }
    }

    // FIX: Search also filters by isSeries
    suspend fun search(contentType: String, query: String, categoryId: String, page: Int): Result<PortalPage<PortalVodItem>> {
        val result = portalService.fetchVodSearch(query, categoryId, page)
        return result.map { page ->
            val filteredItems = when (contentType) {
                "series" -> page.items.filter { it.isSeries }
                else -> page.items.filter { !it.isSeries }
            }
            PortalPage(filteredItems, filteredItems.size)
        }
    }

    suspend fun createStreamLink(cmd: String, type: String = "vod", series: String = ""): Result<String> {
        return portalService.createStreamLink(cmd, type, series)
    }

    // ── Favorites ──
    fun getFavorites(profileId: Int, serverId: Int, type: String): Flow<List<FavoriteEntity>> =
        favoriteDao.getFavorites(profileId, serverId, type)

    fun isFavorite(profileId: Int, serverId: Int, itemId: String): Flow<Boolean> =
        favoriteDao.isFavorite(profileId, serverId, itemId)

    suspend fun toggleFavorite(profileId: Int, serverId: Int, item: PortalVodItem, type: String) {
        favoriteDao.addFavorite(
            FavoriteEntity(
                profileId = profileId,
                serverId = serverId,
                itemId = item.id,
                title = item.name,
                type = type,
                logoUrl = item.logoUrl,
                cmd = item.cmd,
                categoryId = item.categoryId
            )
        )
    }

    suspend fun removeFavorite(profileId: Int, serverId: Int, itemId: String) =
        favoriteDao.removeFavorite(profileId, serverId, itemId)

    // ── Progress ──
    suspend fun getProgress(profileId: Int, serverId: Int, videoId: String): PlaybackProgressEntity? =
        progressDao.getProgress(profileId, serverId, videoId)

    suspend fun getProgressForMovie(profileId: Int, serverId: Int, movieId: String): List<PlaybackProgressEntity> =
        progressDao.getProgressForMovie(profileId, serverId, movieId)

    suspend fun saveProgress(
        profileId: Int, serverId: Int, movieId: String, seasonId: String, seasonNumber: String,
        episodeId: String, episodeNumber: String, videoId: String, positionMs: Long, durationMs: Long
    ) {
        progressDao.saveProgress(
            PlaybackProgressEntity(
                profileId = profileId,
                serverId = serverId,
                movieId = movieId,
                seasonId = seasonId,
                seasonNumber = seasonNumber,
                episodeId = episodeId,
                episodeNumber = episodeNumber,
                videoId = videoId,
                positionMs = positionMs,
                durationMs = durationMs,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}