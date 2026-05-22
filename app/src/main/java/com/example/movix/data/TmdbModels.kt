package com.example.movix.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TmdbListResponse(
    val page: Int = 1,
    val results: List<TmdbItem> = emptyList(),
    @Json(name = "total_pages") val totalPages: Int = 0,
    @Json(name = "total_results") val totalResults: Int = 0
)

@JsonClass(generateAdapter = true)
data class TmdbItem(
    val id: Long,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "media_type") val mediaType: String? = null,
    @Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    val seasons: List<TmdbSeason>? = null,
    val genres: List<TmdbGenre>? = null,
    val runtime: Int? = null
) {
    val displayTitle: String get() = title ?: name ?: "Sans titre"
    val displayDate: String? get() = releaseDate ?: firstAirDate
    val isTv: Boolean get() = mediaType == "tv" || name != null && title == null
}

@JsonClass(generateAdapter = true)
data class TmdbSeason(
    val id: Long,
    @Json(name = "season_number") val seasonNumber: Int,
    val name: String? = null,
    @Json(name = "episode_count") val episodeCount: Int? = null,
    @Json(name = "poster_path") val posterPath: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbGenre(
    val id: Long,
    val name: String
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonDetail(
    val id: Long,
    @Json(name = "season_number") val seasonNumber: Int,
    val name: String? = null,
    val episodes: List<TmdbEpisode> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbEpisode(
    val id: Long,
    @Json(name = "episode_number") val episodeNumber: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "still_path") val stillPath: String? = null,
    @Json(name = "air_date") val airDate: String? = null
)
