package com.itv.blockbuster.domain.model

data class PortalServerConfig(
    val id: Int = 0,
    val name: String,
    val host: String,
    val mac: String,
    val username: String = "",
    val password: String = "",
    val useCredentials: Boolean = false,
    val timezoneId: String? = null
)

data class PortalConnectionResult(
    val portalPath: String,
    val token: String,
    val status: String,
    val message: String? = null
)

data class PortalCategory(
    val id: String,
    val title: String,
    val alias: String = title,
    val isCensored: Boolean = false
)

data class PortalChannel(
    val id: String,
    val name: String,
    val number: String = "",
    val cmd: String = "",
    val logoUrl: String = "",
    val genreId: String = "",
    val nowPlaying: String = "",
    val hasArchive: Boolean = false,
    val archiveDuration: Int = 0,
    val isCensored: Boolean = false
)

data class PortalVodItem(
    val id: String,
    val name: String,
    val cmd: String = "",
    val logoUrl: String = "",
    val description: String = "",
    val director: String = "",
    val actors: String = "",
    val year: String = "",
    val duration: String = "",
    val ratingImdb: String = "",
    val ratingMpaa: String = "",
    val age: String = "",
    val addedDate: String = "",
    val hasFiles: Int = 0,
    val isCensored: Boolean = false,
    val protocol: String = "",
    val categoryId: String = "",
    val contentType: String = "vod",
    val series: List<String> = emptyList(),
    val isSeason: Boolean = false,
    val isEpisode: Boolean = false,
    val isFile: Boolean = false,
    val seasonId: String = "",
    val episodeId: String = "",
    val movieId: String = "",
    val seasonNumber: String = "",
    val episodeNumber: String = "",
    val genres: String = "",
    val seasonSeries: String = "",
    val isSeries: Boolean
)

data class PortalPage<T>(
    val items: List<T>,
    val totalItems: Int
)

data class EpgProgram(
    val name: String,
    val description: String = "",
    val time: String = "",
    val duration: Int = 0,
    val hasArchive: Boolean = false,
    val cmd: String? = null
)

data class EpgDay(
    val humanLabel: String,
    val mysqlDate: String,
    val isToday: Boolean
)