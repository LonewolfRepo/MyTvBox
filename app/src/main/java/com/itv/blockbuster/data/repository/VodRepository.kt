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
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VodRepository @Inject constructor(
    private val portalService: StalkerPortalService,
    private val sessionManager: StalkerSessionManager,
    private val favoriteDao: FavoriteDao,
    private val progressDao: PlaybackProgressDao
) {

    suspend fun getCategories(): Result<List<PortalCategory>> {
        return portalService.fetchVodCategories()
    }

    suspend fun getList(contentType: String, categoryId: String, page: Int, pageSize: Int = 20): Result<PortalPage<PortalVodItem>> {
        val result = portalService.fetchVodList(categoryId, page, pageSize)
        return result.map { vodPage ->
            val filteredItems = when (contentType) {
                "series" -> vodPage.items.filter { item -> item.isSeries }
                else -> vodPage.items.filter { item -> !item.isSeries }
            }
            PortalPage(filteredItems, filteredItems.size)
        }
    }

    suspend fun search(contentType: String, query: String, categoryId: String, page: Int): Result<PortalPage<PortalVodItem>> {
        val result = portalService.fetchVodSearch(query, categoryId, page)
        return result.map { vodPage ->
            val filteredItems = when (contentType) {
                "series" -> vodPage.items.filter { item -> item.isSeries }
                else -> vodPage.items.filter { item -> !item.isSeries }
            }
            PortalPage(filteredItems, filteredItems.size)
        }
    }

    suspend fun createStreamLink(cmd: String, type: String = "vod", series: String = ""): Result<String> {
        return portalService.createStreamLink(cmd, type, series)
    }

    suspend fun getMovieFileId(movieId: String): Result<String> {
        return portalService.getMovieFileId(movieId)
    }

    suspend fun getSeasons(movieId: String): Result<List<PortalVodItem>> {
        return portalService.getSeasons(movieId)
    }

    suspend fun getEpisodes(movieId: String, seasonId: String): Result<List<PortalVodItem>> {
        return portalService.getEpisodes(movieId, seasonId)
    }

    suspend fun getEpisodeFileId(movieId: String, seasonId: String, episodeId: String): Result<String> {
        return portalService.getEpisodeFileId(movieId, seasonId, episodeId)
    }

    fun getFavorites(profileId: Int, serverId: Int, type: String): Flow<List<FavoriteEntity>> =
        favoriteDao.getFavorites(profileId, serverId, type)

    fun isFavorite(profileId: Int, serverId: Int, itemId: String): Flow<Boolean> =
        favoriteDao.isFavorite(profileId, serverId, itemId)

    suspend fun toggleFavorite(profileId: Int, serverId: Int, item: PortalVodItem, type: String) {
        // Normalize so writers and readers agree: VOD / SERIES / LIVE
        val storageType = when {
            type.equals("series", ignoreCase = true) -> "SERIES"
            type.equals("vod", ignoreCase = true) || type.equals("movie", ignoreCase = true) -> "VOD"
            else -> type.uppercase()
        }
        val exists = favoriteDao.isFavorite(profileId, serverId, item.id).first()
        if (exists) {
            favoriteDao.removeFavorite(profileId, serverId, item.id)
        } else {
            favoriteDao.addFavorite(
                FavoriteEntity(
                    profileId = profileId,
                    serverId = serverId,
                    itemId = item.id,
                    title = item.name,
                    type = storageType,
                    logoUrl = item.logoUrl,
                    cmd = item.cmd,
                    categoryId = item.categoryId,
                    // Store full metadata
                    description = item.description,
                    director = item.director,
                    actors = item.actors,
                    year = item.year,
                    duration = item.duration,
                    ratingImdb = item.ratingImdb,
                    ratingMpaa = item.ratingMpaa,
                    age = item.age,
                    addedDate = item.addedDate,
                    genres = item.genres,
                    country = item.country,
                    timestamp = System.currentTimeMillis()

                )
            )
        }
    }



    suspend fun removeFavorite(profileId: Int, serverId: Int, itemId: String) =
        favoriteDao.removeFavorite(profileId, serverId, itemId)

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