package com.itv.blockbuster.domain.model

import androidx.compose.runtime.Immutable

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

// FIX: every field here is a val (String/Boolean/Int), except `series`,
// which is a plain List<String>. Since List is an interface the Compose
// compiler can't prove is never mutated after construction, that ONE field
// was enough to mark this WHOLE class unstable - and since PortalVodItem is
// the model behind every poster/episode card in every carousel and list in
// the app, that meant Compose couldn't skip recomposition for ANY of them
// based on "did this item's data actually change" - every scroll tick,
// focus change, or state update forced every item composable touching this
// type to recompose, regardless of whether its own data changed. @Immutable
// tells the compiler to trust that this class behaves as immutable (true in
// practice - series is never mutated after construction anywhere in the
// codebase) rather than needing to prove it structurally.
@Immutable
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
    val isSeries: Boolean,
    val fileCount: Int = 0,
    val country: String = "",
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