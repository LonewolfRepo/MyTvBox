package com.itv.blockbuster.data.repository

import com.itv.blockbuster.data.local.dao.FavoriteDao
import com.itv.blockbuster.data.local.dao.PlaybackProgressDao
import com.itv.blockbuster.data.local.dao.RecentDao
import com.itv.blockbuster.data.local.entity.FavoriteEntity
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.data.local.entity.RecentEntity
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalPage
import com.itv.blockbuster.domain.model.PortalVodItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VodRepository @Inject constructor(
    private val portalService: StalkerPortalService,
    private val sessionManager: StalkerSessionManager,
    private val favoriteDao: FavoriteDao,
    private val progressDao: PlaybackProgressDao,
    private val recentDao: RecentDao
) {
    suspend fun getCategories(): Result<List<PortalCategory>> = portalService.fetchVodCategories()
    suspend fun getList(contentType: String, categoryId: String, page: Int, pageSize: Int = 20): Result<PortalPage<PortalVodItem>> {
        val result = portalService.fetchVodList(categoryId, page, pageSize)
        return result.map { vodPage ->
            val filteredItems = if (contentType == "series") vodPage.items.filter { it.isSeries } else vodPage.items.filter { !it.isSeries }
            PortalPage(filteredItems, filteredItems.size)
        }
    }
    suspend fun search(contentType: String, query: String, categoryId: String, page: Int): Result<PortalPage<PortalVodItem>> {
        val result = portalService.fetchVodSearch(query, categoryId, page)
        return result.map { vodPage ->
            val filteredItems = if (contentType == "series") vodPage.items.filter { it.isSeries } else vodPage.items.filter { !it.isSeries }
            PortalPage(filteredItems, filteredItems.size)
        }
    }
    suspend fun createStreamLink(cmd: String, type: String = "vod", series: String = ""): Result<String> = portalService.createStreamLink(cmd, type, series)
    suspend fun getMovieFileId(movieId: String): Result<String> = portalService.getMovieFileId(movieId)
    suspend fun getSeasons(movieId: String): Result<List<PortalVodItem>> = portalService.getSeasons(movieId)
    suspend fun getEpisodes(movieId: String, seasonId: String): Result<List<PortalVodItem>> = portalService.getEpisodes(movieId, seasonId)
    suspend fun getEpisodeFileId(movieId: String, seasonId: String, episodeId: String): Result<String> = portalService.getEpisodeFileId(movieId, seasonId, episodeId)

    fun getFavorites(profileId: Int, serverId: Int, type: String): Flow<List<FavoriteEntity>> = favoriteDao.getFavorites(profileId, serverId, type)
    fun isFavorite(profileId: Int, serverId: Int, itemId: String): Flow<Boolean> = favoriteDao.isFavorite(profileId, serverId, itemId)

    suspend fun toggleFavorite(profileId: Int, serverId: Int, item: PortalVodItem, type: String) {
        val storageType = when {
            type.equals("series", ignoreCase = true) -> "SERIES"
            type.equals("vod", ignoreCase = true) || type.equals("movie", ignoreCase = true) -> "VOD"
            else -> type.uppercase()
        }
        val exists = favoriteDao.isFavorite(profileId, serverId, item.id).first()
        if (exists) favoriteDao.removeFavorite(profileId, serverId, item.id)
        else {
            favoriteDao.addFavorite(FavoriteEntity(
                profileId = profileId, serverId = serverId, itemId = item.id, title = item.name, type = storageType,
                logoUrl = item.logoUrl, cmd = item.cmd, categoryId = item.categoryId, description = item.description,
                director = item.director, actors = item.actors, year = item.year, duration = item.duration,
                ratingImdb = item.ratingImdb, ratingMpaa = item.ratingMpaa, age = item.age, addedDate = item.addedDate,
                genres = item.genres, country = item.country, timestamp = System.currentTimeMillis()
            ))
        }
    }
    suspend fun removeFavorite(profileId: Int, serverId: Int, itemId: String) = favoriteDao.removeFavorite(profileId, serverId, itemId)

    // ── PROGRESS LOOKUP PATHS ──
    suspend fun getProgress(profileId: Int, serverId: Int, videoId: String): PlaybackProgressEntity? = progressDao.getProgress(profileId, serverId, videoId)

    suspend fun getMovieProgressByMovieId(profileId: Int, serverId: Int, movieId: String): PlaybackProgressEntity? =
        progressDao.getMovieProgressByMovieId(profileId, serverId, movieId)

    suspend fun getProgressForEpisode(profileId: Int, serverId: Int, movieId: String, seasonId: String, episodeId: String): PlaybackProgressEntity? =
        progressDao.getProgressForEpisode(profileId, serverId, movieId, seasonId, episodeId)

    suspend fun getProgressForMovie(profileId: Int, serverId: Int, movieId: String): List<PlaybackProgressEntity> =
        progressDao.getProgressForMovie(profileId, serverId, movieId)

    fun getRecentProgress(profileId: Int, serverId: Int): Flow<List<PlaybackProgressEntity>> = progressDao.getRecentProgress(profileId, serverId)

    suspend fun saveProgress(
        profileId: Int, serverId: Int, movieId: String, seasonId: String, seasonNumber: String,
        episodeId: String, episodeNumber: String, videoId: String, positionMs: Long, durationMs: Long
    ) {
        progressDao.saveProgress(PlaybackProgressEntity(
            profileId = profileId, serverId = serverId, movieId = movieId, seasonId = seasonId,
            seasonNumber = seasonNumber, episodeId = episodeId, episodeNumber = episodeNumber,
            videoId = videoId, positionMs = positionMs, durationMs = durationMs, timestamp = System.currentTimeMillis()
        ))
    }

    suspend fun addRecent(profileId: Int, serverId: Int, item: PortalVodItem, type: String) {
        val storageType = if (type.equals("series", ignoreCase = true)) "SERIES" else "VOD"
        recentDao.upsert(RecentEntity(
            profileId = profileId, serverId = serverId, itemId = item.id, title = item.name, type = storageType,
            logoUrl = item.logoUrl, cmd = item.cmd, categoryId = item.categoryId, description = item.description,
            director = item.director, actors = item.actors, year = item.year, duration = item.duration,
            ratingImdb = item.ratingImdb, ratingMpaa = item.ratingMpaa, age = item.age, addedDate = item.addedDate,
            genres = item.genres, country = item.country, timestamp = System.currentTimeMillis()
        ))
    }
}