package com.itv.blockbuster.data.repository

import com.itv.blockbuster.data.local.dao.FavoriteDao
import com.itv.blockbuster.data.local.dao.RecentDao
import com.itv.blockbuster.data.local.entity.FavoriteEntity
import com.itv.blockbuster.data.local.entity.RecentLiveEntity
import com.itv.blockbuster.data.remote.StalkerApi
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.EpgDay
import com.itv.blockbuster.domain.model.EpgProgram
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalPage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveTvRepository @Inject constructor(
    private val portalService: StalkerPortalService,
    private val api: StalkerApi,
    private val session: StalkerSessionManager,
    private val favoriteDao: FavoriteDao,
    private val recentDao: RecentDao
) {

    private val epgCache = mutableMapOf<String, List<EpgProgram>>()

    // ── Network ──
    suspend fun getCategories(): Result<List<PortalCategory>> = portalService.fetchLiveCategories()

    suspend fun getAllChannels(): Result<PortalPage<PortalChannel>> = portalService.fetchAllLiveChannels()

    suspend fun createStreamLink(cmd: String): Result<String> = portalService.createStreamLink(cmd, "itv")

    suspend fun getShortEpg(channelId: String): Result<List<EpgProgram>> = portalService.fetchShortEpg(channelId)

    suspend fun getShortEpgCached(channelId: String): List<EpgProgram> {
        epgCache[channelId]?.let { return it }
        val epg = portalService.fetchShortEpg(channelId).getOrDefault(emptyList())
        if (epg.isNotEmpty()) epgCache[channelId] = epg
        return epg
    }

    suspend fun getEpgWeek(channelId: String): List<EpgDay> =
        portalService.fetchEpgWeek(channelId).getOrDefault(emptyList())

    suspend fun getEpgTable(channelId: String, date: String): List<EpgProgram> {
        return try {
            val loader = session.ajaxLoader.value
            val url = "$loader?action=get_epg_table&type=itv&ch_id=$channelId&date=$date&p=1&JsHttpRequest=1-xml"
            api.getEpgTable(url).js.data.orEmpty().map {
                EpgProgram(
                    name = it.name,
                    description = it.descr,
                    time = it.tTime,
                    duration = it.duration.toIntOrNull() ?: 0,
                    hasArchive = it.markArchive == 1,
                    cmd = it.cmd
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Favorites (profile + server scoped) ──
    fun getFavorites(profileId: Int, serverId: Int, type: String): Flow<List<FavoriteEntity>> =
        favoriteDao.getFavorites(profileId, serverId, type)

    suspend fun toggleFavorite(profileId: Int, serverId: Int, channel: PortalChannel) {
        favoriteDao.addFavorite(
            FavoriteEntity(
                profileId = profileId,
                serverId = serverId,
                itemId = channel.id,
                title = channel.name,
                type = "LIVE",
                logoUrl = channel.logoUrl,
                cmd = channel.cmd,
                categoryId = channel.genreId
            )
        )
    }

    suspend fun removeFavorite(profileId: Int, serverId: Int, itemId: String) =
        favoriteDao.removeFavorite(profileId, serverId, itemId)

    suspend fun clearAllFavorites(profileId: Int, serverId: Int, type: String) =
        favoriteDao.clearFavorites(profileId, serverId, type)

    // ── Recents (profile + server scoped) ──
    fun getRecent(profileId: Int, serverId: Int, limit: Int): Flow<List<RecentLiveEntity>> =
        recentDao.getRecentLive(profileId, serverId, limit)

    suspend fun addRecent(profileId: Int, serverId: Int, channel: PortalChannel) {
        recentDao.insertRecent(
            RecentLiveEntity(
                profileId = profileId,
                serverId = serverId,
                channelId = channel.id,
                channelName = channel.name,
                logoUrl = channel.logoUrl,
                number = channel.number,
                cmd = channel.cmd
            )
        )
    }

    suspend fun removeRecent(profileId: Int, serverId: Int, channelId: String) =
        recentDao.deleteRecent(profileId, serverId, channelId)

    suspend fun clearAllRecents(profileId: Int, serverId: Int) =
        recentDao.clearRecent(profileId, serverId)
}