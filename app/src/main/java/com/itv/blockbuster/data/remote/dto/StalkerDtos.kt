package com.itv.blockbuster.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HandshakeResponse(val js: HandshakeData = HandshakeData()) {
    @Serializable
    data class HandshakeData(val token: String = "")
}

@Serializable
data class ProfileResponse(val js: ProfileData = ProfileData()) {
    @Serializable
    data class ProfileData(
        val status: String = "",
        val message: String? = null,
        @SerialName("msg") val msg: String? = null,
        @SerialName("parent_password") val parentPassword: String? = null,
        val created: String? = null
    )
}

@Serializable
data class CategoryResponse(val js: List<CategoryDto> = emptyList())

@Serializable
data class CategoryDto(
    val id: String = "",
    val title: String = "",
    val alias: String? = null,
    val censored: Int = 0
)

@Serializable
data class ChannelListResponse(val js: ChannelListData = ChannelListData()) {
    @Serializable
    data class ChannelListData(
        @SerialName("data") val data: List<ChannelDto>? = null,
        @SerialName("total_items") val totalItems: String = "0",
        @SerialName("max_page_items") val maxPageItems: String = "14"
    )
}

@Serializable
data class ChannelDto(
    val id: String = "",
    val name: String = "",
    val number: String? = null,
    val cmd: String = "",
    val logo: String? = null,
    val archive: String = "0",
    @SerialName("tv_genre_id") val tvGenreId: String? = null,
    @SerialName("use_http_tmp_link") val useHttpTmpLink: String = "0",
    val censored: String = "0",
    @SerialName("tv_archive_duration") val tvArchiveDuration: String = "0",
    @SerialName("cur_playing") val curPlaying: String = ""
)

@Serializable
data class CreateLinkResponse(val js: CreateLinkData = CreateLinkData()) {
    @Serializable
    data class CreateLinkData(val cmd: String = "")
}

@Serializable
data class VodListResponse(val js: VodListData = VodListData()) {
    @Serializable
    data class VodListData(
        @SerialName("data") val data: List<VodDto>? = null,
        @SerialName("total_items") val totalItems: String = "0",
        @SerialName("max_page_items") val maxPageItems: String = "14"
    )
}

@Serializable
data class VodDto(
    val id: String = "",
    val name: String = "",
    val cmd: String? = null,
    @SerialName("screenshot_uri") val screenshotUri: String? = null,
    val description: String? = null,
    val director: String? = null,
    val actors: String? = null,
    val year: String? = null,
    val time: String? = null,
    @SerialName("rating_imdb") val ratingImdb: String? = null,
    @SerialName("rating_mpaa") val ratingMpaa: String? = null,
    val age: String? = null,
    val added: String? = null,
    @SerialName("has_files") val hasFiles: Int = 0,
    val censored: String = "0",
    val protocol: String = "",
    @SerialName("category_id") val categoryId: String = "",
    val series: List<String>? = null,
    @SerialName("is_season") val isSeason: Boolean = false,
    @SerialName("is_episode") val isEpisode: Boolean = false,
    @SerialName("is_series") val isSeries: Int = 0,
    @SerialName("season_id") val seasonId: String? = null,
    @SerialName("episode_id") val episodeId: String? = null,
    @SerialName("movie_id") val movieId: String? = null,
    val season: String? = null,
    @SerialName("series_number") val seriesNumber: String? = null,
    @SerialName("genres_str") val genresStr: String? = null,
    @SerialName("season_number") val seasonNumber: String? = null,
    @SerialName("season_series") val seasonSeries: String? = null,
    @SerialName("count") val count: Int = 0,
    val country: String = "",
)

@Serializable
data class EpgTableResponse(val js: EpgTableData = EpgTableData()) {
    @Serializable
    data class EpgTableData(
        @SerialName("data") val data: List<EpgProgramDto>? = null,
        @SerialName("total_items") val totalItems: String = "0",
        @SerialName("cur_page") val curPage: Int = 1,
        @SerialName("selected_item") val selectedItem: Int = 0,
        @SerialName("max_page_items") val maxPageItems: Int = 50
    )
}

@Serializable
data class EpgProgramDto(
    val name: String = "",
    val descr: String = "",
    @SerialName("t_time") val tTime: String = "",
    val time: String = "",
    val duration: String = "0",
    @SerialName("mark_archive") val markArchive: Int = 0,
    val cmd: String? = null
)

@Serializable
data class EpgWeekResponse(val js: List<EpgDayDto> = emptyList())

@Serializable
data class EpgDayDto(
    @SerialName("f_human") val fHuman: String = "",
    @SerialName("f_mysql") val fMysql: String = "",
    val today: Int = 0
)
@Serializable
data class WatchdogResponse(val js: WatchdogData = WatchdogData()) {
    @Serializable
    data class WatchdogData(val status: String = "")
}