package com.example.movix.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Schéma réel de https://api.movix.tax/api/tmdb/{type}/{id}[?season=N&episode=N]
 *
 * Films :
 * { tmdb_details, iframe_src, player_links: [...] }
 *
 * Séries (sans season/episode) :
 * { tmdb_details, seasons: [{season_number, name, data_id, post_id, episodes: []}] }
 *
 * Séries (avec season+episode) :
 * { tmdb_details, seasons: [...], current_episode: { season_number, episode_number, title, iframe_src, player_links } }
 */
@JsonClass(generateAdapter = true)
data class MovixSourcesResponse(
    @Json(name = "tmdb_details") val tmdbDetails: MovixTmdbDetails? = null,
    @Json(name = "iframe_src") val iframeSrc: String? = null,
    @Json(name = "player_links") val playerLinks: List<MovixLink>? = null,
    val seasons: List<MovixSeasonInfo>? = null,
    @Json(name = "current_episode") val currentEpisode: MovixCurrentEpisode? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class MovixTmdbDetails(
    val id: Long? = null,
    val title: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    val overview: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null
)

@JsonClass(generateAdapter = true)
data class MovixCurrentEpisode(
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episode_number") val episodeNumber: Int? = null,
    val title: String? = null,
    @Json(name = "iframe_src") val iframeSrc: String? = null,
    @Json(name = "player_links") val playerLinks: List<MovixLink>? = null
)

@JsonClass(generateAdapter = true)
data class MovixLink(
    @Json(name = "decoded_url") val decodedUrl: String? = null,
    @Json(name = "clone_url") val cloneUrl: String? = null,
    val quality: String? = null,
    val language: String? = null
) {
    fun bestUrl(): String? = decodedUrl ?: cloneUrl
    fun displayName(): String = listOfNotNull(quality, language)
        .joinToString(" • ")
        .ifBlank { "Source" }
}

@JsonClass(generateAdapter = true)
data class MovixSeasonInfo(
    @Json(name = "season_number") val seasonNumber: Int? = null,
    val name: String? = null,
    @Json(name = "data_id") val dataId: String? = null,
    @Json(name = "post_id") val postId: String? = null,
    val episodes: List<MovixEpisodeStub>? = null
) {
    val number: Int get() = seasonNumber ?: 0
}

@JsonClass(generateAdapter = true)
data class MovixEpisodeStub(
    @Json(name = "episode_number") val episodeNumber: Int? = null,
    val title: String? = null
)

@JsonClass(generateAdapter = true)
data class MovixSearchResponse(
    val results: List<MovixSearchItem> = emptyList(),
    val query: String? = null
)

@JsonClass(generateAdapter = true)
data class MovixSearchItem(
    val id: Long? = null,
    @Json(name = "tmdb_id") val tmdbId: Long? = null,
    val title: String? = null,
    val name: String? = null,
    val type: String? = null,
    @Json(name = "model_type") val modelType: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    val poster: String? = null,
    val overview: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null
) {
    fun displayTitle(): String = title ?: name ?: "Sans titre"
    fun resolvedTmdbId(): Long? = tmdbId ?: id
    fun isTv(): Boolean {
        val t = (type ?: modelType ?: "").lowercase()
        return t.contains("tv") || t.contains("series") || t.contains("show")
    }
    fun posterUrl(): String? = poster ?: posterPath?.let {
        if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w500$it"
    }
}
