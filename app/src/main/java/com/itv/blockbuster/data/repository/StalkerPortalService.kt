package com.itv.blockbuster.data.repository

import com.itv.blockbuster.data.remote.StalkerApi
import com.itv.blockbuster.data.remote.dto.CategoryDto
import com.itv.blockbuster.data.remote.dto.ChannelDto
import com.itv.blockbuster.data.remote.dto.EpgProgramDto
import com.itv.blockbuster.data.remote.dto.VodDto
import com.itv.blockbuster.data.session.ActivePortal
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.EpgDay
import com.itv.blockbuster.domain.model.EpgProgram
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalConnectionResult
import com.itv.blockbuster.domain.model.PortalPage
import com.itv.blockbuster.domain.model.PortalServerConfig
import com.itv.blockbuster.domain.model.PortalVodItem
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StalkerPortalService @Inject constructor(
    private val api: StalkerApi,
    private val session: StalkerSessionManager
) {

    private val portalPaths = listOf(
        "/stalker_portal/",
        "/c/",
        "/mag/",
        "/portal/",
        "/"
    )

    // =====================================================================
    // CONNECTION / AUTHENTICATION
    // =====================================================================

    suspend fun connect(config: PortalServerConfig): Result<PortalConnectionResult> = safe {
        require(config.host.isNotBlank()) { "Portal host cannot be empty" }
        require(config.mac.isNotBlank()) { "MAC address cannot be empty" }

        session.setActivePortal(
            ActivePortal(
                serverId = config.id,
                name = config.name,
                host = config.host,
                mac = config.mac,
                username = config.username,
                password = config.password,
                useCredentials = config.useCredentials,
                timezoneId = config.timezoneId ?: TimeZone.getDefault().id
            )
        )
        session.clearSession()

        val cleanHost = config.host.trimEnd('/')
        var detectedPath: String? = null
        var handshakeToken: String? = null
        var lastError = "Unknown error"

        for (path in portalPaths) {
            try {
                val handshakeUrl =
                    "$cleanHost${path}server/load.php?action=handshake&type=stb&JsHttpRequest=1-xml"
                val response = api.handshake(handshakeUrl)
                val token = response.js.token.trim()
                if (token.isNotEmpty()) {
                    detectedPath = path
                    handshakeToken = token
                    break
                } else {
                    lastError = "Empty handshake token"
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (httpError: HttpException) {
                if (httpError.code() == 404) continue
                detectedPath = path
                lastError = "Server rejected handshake: HTTP ${httpError.code()}"
                break
            } catch (serializationError: SerializationException) {
                continue
            } catch (error: Exception) {
                lastError = error.localizedMessage ?: "Network error"
                break
            }
        }

        if (detectedPath == null || handshakeToken == null) {
            throw IOException("Portal not found or handshake failed. $lastError")
        }

        session.setPortalDir(detectedPath)
        session.setBearerToken(handshakeToken)

        val serialNumber = generateSerial(config.mac)
        val profileUrl = "$cleanHost${detectedPath}server/load.php" +
                "?action=get_profile&type=stb&hd=1" +
                "&ver=ImageDescription:%200.2.18-r14-pub-250" +
                "&sn=$serialNumber&stb_type=MAG254&client_type=STB" +
                "&device_id=&deviceid2=&JsHttpRequest=1-xml"

        val profileResponse = try {
            api.getProfile(profileUrl)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            throw IOException("Profile fetch failed: ${error.localizedMessage}")
        }

        val status = profileResponse.js.status
        val isSuccess = status.isEmpty() ||
                status.equals("OK", ignoreCase = true) ||
                status == "0"

        if (!isSuccess) {
            val errorMessage = profileResponse.js.msg
                ?: profileResponse.js.message
                ?: "Portal rejected profile request"
            throw IOException(errorMessage)
        }

        session.setAjaxLoader("$cleanHost${detectedPath}server/load.php")

        PortalConnectionResult(
            portalPath = detectedPath,
            token = handshakeToken,
            status = status,
            message = profileResponse.js.message
        )
    }

    // =====================================================================
    // LIVE TV
    // =====================================================================

    suspend fun fetchLiveCategories(): Result<List<PortalCategory>> = safe {
        api.getLiveCategories(buildLoadUrl("action=get_genres&type=itv"))
            .js.map { it.toDomain() }
    }

    suspend fun fetchAllLiveChannels(): Result<PortalPage<PortalChannel>> = safe {
        val response = api.getLiveChannels(buildLoadUrl("action=get_all_channels&type=itv"))
        val items = response.js.data.orEmpty().map { it.toDomain() }
        PortalPage(items, response.js.totalItems.toIntOrNull() ?: items.size)
    }

    suspend fun fetchChannelsByCategory(
        categoryId: String,
        page: Int = 1
    ): Result<PortalPage<PortalChannel>> = safe {
        val params = if (categoryId == "*" || categoryId.equals("all", true)) {
            "action=get_all_channels&type=itv"
        } else {
            "action=get_ordered_list&type=itv&genre=$categoryId&sortby=number&p=$page"
        }
        val response = api.getLiveChannels(buildLoadUrl(params))
        val items = response.js.data.orEmpty().map { it.toDomain() }
        PortalPage(items, response.js.totalItems.toIntOrNull() ?: items.size)
    }

    suspend fun fetchShortEpg(channelId: String): Result<List<EpgProgram>> = safe {
        val url = buildLoadUrl("action=get_short_epg&type=itv&ch_id=$channelId")
        api.getShortEpg(url).js.data.orEmpty().map { it.toDomain() }
    }

    suspend fun fetchEpgWeek(channelId: String): Result<List<EpgDay>> = safe {
        val url = buildLoadUrl("action=get_week&type=itv&ch_id=$channelId")
        api.getEpgWeek(url).js.map {
            EpgDay(it.fHuman, it.fMysql, it.today == 1)
        }
    }

    // =====================================================================
    // VOD / MOVIES / TV SHOWS
    // =====================================================================

    suspend fun fetchVodCategories(): Result<List<PortalCategory>> = safe {
        api.getVodCategories(buildLoadUrl("action=get_categories&type=vod"))
            .js.map { it.toDomain() }
    }

    suspend fun fetchSeriesCategories(): Result<List<PortalCategory>> = safe {
        api.getSeriesCategories(buildLoadUrl("action=get_categories&type=series"))
            .js.map { it.toDomain() }
    }

    suspend fun fetchVodList(
        categoryId: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<PortalPage<PortalVodItem>> = safe {
        fetchOrderedList("vod", categoryId, page, pageSize, null)
    }

    suspend fun fetchVodSearch(query: String, categoryId: String, page: Int): Result<PortalPage<PortalVodItem>> {
        return safe {
            fetchOrderedList("vod", categoryId, page, 20, query)
        }
    }

    suspend fun fetchSeriesList(
        categoryId: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<PortalPage<PortalVodItem>> = safe {
        fetchOrderedList("vod", categoryId, page, pageSize, null)
    }

    suspend fun searchVod(
        query: String,
        categoryId: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<PortalPage<PortalVodItem>> = safe {
        fetchOrderedList("vod", categoryId, page, pageSize, query)
    }

    // FIX: Added missing searchSeries method
    suspend fun searchSeries(
        query: String,
        categoryId: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<PortalPage<PortalVodItem>> = safe {
        fetchOrderedList("series", categoryId, page, pageSize, query)
    }

    suspend fun createStreamLink(
        cmd: String,
        type: String = "itv",
        series: String = ""
    ): Result<String> = safe {
        require(cmd.isNotBlank()) { "Stream cmd cannot be empty" }
        val encodedCmd = URLEncoder.encode(cmd, "UTF-8")
        val seriesParam = if (series.isNotEmpty()) "&series=$series" else "&series="
        val url = buildLoadUrl(
            "action=create_link&type=$type&cmd=$encodedCmd$seriesParam" +
                    "&forced_storage=undefined&disable_ad=0&download=0"
        )
        val fullCmd = api.createLink(url).js.cmd
        Regex("https?://[^\\s\"']+").find(fullCmd)?.value ?: fullCmd.trim()
    }

    // =====================================================================
    // PRIVATE HELPERS
    // =====================================================================

    private suspend fun fetchOrderedList(
        type: String,
        categoryId: String,
        page: Int,
        pageSize: Int,
        search: String?
    ): PortalPage<PortalVodItem> {
        val offset = (page - 1) * pageSize
        val category = categoryId.ifBlank { "*" }
        val searchParam = if (search.isNullOrBlank()) "" else
            "&search=${URLEncoder.encode(search.trim(), "UTF-8")}"
        // FIX: Removed page_offset. Server dictates max_page_items and handles offset automatically via 'p'
        // The Stalker API uses "genre" parameter for VOD category filtering
        val params = "action=get_ordered_list&type=$type&sortby=added" +
                "&genre=$category&p=$page&video=all$searchParam"
        val response = api.getVodList(buildLoadUrl(params))
        val items = response.js.data.orEmpty().map { it.toDomain(type) }
        return PortalPage(items, response.js.totalItems.toIntOrNull() ?: items.size)
    }

    private fun buildLoadUrl(params: String): String {
        val loader = session.ajaxLoader.value
        check(loader.isNotEmpty()) { "Stalker session is not connected. Call connect() first." }
        return "$loader?$params&JsHttpRequest=1-xml"
    }

    private fun absoluteUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""

        // FIX: Suppress invalid "false" or "null" strings returned by some portals
        if (path.equals("false", ignoreCase = true) || path.equals("null", ignoreCase = true)) return ""

        if (path.startsWith("http://", true) || path.startsWith("https://", true)) return path
        val host = session.activePortal.value?.host?.trimEnd('/') ?: return path
        val portalDir = session.portalDir.value.trimEnd('/')

        // If the path already contains the portal directory, don't append it again
        val cleanPath = if (path.startsWith(portalDir)) {
            path
        } else {
            if (path.startsWith("/")) path else "/$path"
        }
        var result = "$host$cleanPath"
        val protocolEnd = result.indexOf("://")
        if (protocolEnd != -1) {
            val protocol = result.substring(0, protocolEnd + 3)
            val rest = result.substring(protocolEnd + 3).replace("//", "/")
            result = protocol + rest
        } else {
            result = result.replace("//", "/")
        }
        return result
    }

    private fun CategoryDto.toDomain() = PortalCategory(
        id = id,
        title = title.uppercase(),
        alias = alias ?: title,
        isCensored = censored == 1
    )

    private fun ChannelDto.toDomain() = PortalChannel(
        id = id,
        name = name,
        number = number ?: "",
        cmd = cmd,
        logoUrl = absoluteUrl(logo),
        genreId = tvGenreId ?: "",
        nowPlaying = curPlaying,
        hasArchive = archive != "0",
        archiveDuration = tvArchiveDuration.toIntOrNull() ?: 0,
        isCensored = censored == "1"
    )

    private fun EpgProgramDto.toDomain() = EpgProgram(
        name = name,
        description = descr,
        time = tTime,
        duration = duration.toIntOrNull() ?: 0,
        hasArchive = markArchive == 1,
        cmd = cmd
    )

    private fun VodDto.toDomain(contentType: String) = PortalVodItem(
        id = id,
        name = name,
        cmd = cmd ?: "",
        logoUrl = absoluteUrl(screenshotUri),
        description = description ?: "",
        director = director ?: "",
        actors = actors ?: "",
        year = year ?: "",
        duration = time ?: "",
        ratingImdb = ratingImdb ?: "",
        ratingMpaa = ratingMpaa ?: "",
        age = age ?: "",
        addedDate = added ?: "",
        hasFiles = hasFiles,
        isCensored = censored == "1",
        protocol = protocol,
        categoryId = categoryId,
        contentType = contentType,
        series = series ?: emptyList(),
        isSeason = isSeason,
        isEpisode = isEpisode,
        isSeries = isSeries == 1,
        isFile = hasFiles == 1,
        seasonId = seasonId ?: "",
        episodeId = episodeId ?: "",
        movieId = movieId ?: id,
        seasonNumber = if (isSeason) seasonNumber ?: "" else "",
        episodeNumber = if (isEpisode) seriesNumber ?: "" else "",
        genres = genresStr ?: "",
        seasonSeries = seasonSeries ?: "",
        fileCount = count,
        country = country,
    )

    private fun generateSerial(mac: String): String {
        val cleanMac = mac.replace(":", "").uppercase(Locale.US)
        if (cleanMac.length < 12) return "102014J000000"
        val macBytes = cleanMac.chunked(2)
        if (macBytes.size < 6) return "102014J000000"
        val i = macBytes[3].toIntOrNull(16) ?: 0
        val i2 = macBytes[4].toIntOrNull(16) ?: 0
        val i3 = macBytes[5].toIntOrNull(16) ?: 0
        val months = arrayOf(
            "102014", "112014", "122014",
            "012015", "022015", "032015",
            "042015", "052015", "062015",
            "072015", "082015", "092015",
            "102015", "112015", "122015"
        )
        val letters = arrayOf("J", "K", "L", "M", "N")
        val part1 = months[i % months.size]
        val part2 = letters[i2 % letters.size]
        val part3 = String.format(Locale.US, "%06d", i3 or (i2 shl 8)).take(6)
        return part1 + part2 + part3
    }

    private suspend fun <T> safe(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getMovieFileId(movieId: String): Result<String> {
        return safe {
            val url = buildLoadUrl("action=get_ordered_list&type=vod&movie_id=$movieId&p=1")
            val response = api.getVodList(url)
            val firstItem = response.js.data?.firstOrNull()
                ?: throw IOException("No video file found for movie $movieId")
            firstItem.id
        }
    }

    suspend fun getSeasons(movieId: String): Result<List<PortalVodItem>> {
        return safe {
            val allSeasons = mutableListOf<PortalVodItem>()
            var currentPage = 1
            var totalItems = Int.MAX_VALUE
            while (allSeasons.size < totalItems) {
                val url = buildLoadUrl("action=get_ordered_list&type=vod&movie_id=$movieId&p=$currentPage")
                val response = api.getVodList(url)
                totalItems = response.js.totalItems.toIntOrNull() ?: 0
                val items = response.js.data.orEmpty().map { it.toDomain("series") }
                allSeasons.addAll(items)
                if (items.isEmpty() || allSeasons.size >= totalItems) break
                currentPage++
            }
            allSeasons
        }
    }

    suspend fun getEpisodes(movieId: String, seasonId: String): Result<List<PortalVodItem>> {
        return safe {
            val allEpisodes = mutableListOf<PortalVodItem>()
            var currentPage = 1
            var totalItems = Int.MAX_VALUE
            while (allEpisodes.size < totalItems) {
                val url = buildLoadUrl("action=get_ordered_list&type=vod&movie_id=$movieId&season_id=$seasonId&p=$currentPage")
                val response = api.getVodList(url)
                totalItems = response.js.totalItems.toIntOrNull() ?: 0
                val items = response.js.data.orEmpty().map { it.toDomain("series") }
                allEpisodes.addAll(items)
                if (items.isEmpty() || allEpisodes.size >= totalItems) break
                currentPage++
            }
            allEpisodes
        }
    }

    suspend fun getEpisodeFileId(movieId: String, seasonId: String, episodeId: String): Result<String> {
        return safe {
            val url = buildLoadUrl("action=get_ordered_list&type=vod&movie_id=$movieId&season_id=$seasonId&episode_id=$episodeId&p=1")
            val response = api.getVodList(url)
            val firstItem = response.js.data?.firstOrNull()
                ?: throw IOException("No video file found for episode $episodeId")
            firstItem.id
        }
    }
}