package com.example.movix.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Réponse du proxy Movix /api/tmdb/{type}/{id}.
 * Le schéma exact varie selon les sources (coflix, frenchstream, etc.) donc
 * on garde un mapping souple : on extrait juste les éléments lisibles.
 */
@JsonClass(generateAdapter = true)
data class MovixSourcesResponse(
    val available: Boolean? = null,
    val message: String? = null,
    val title: String? = null,
    val overview: String? = null,
    @Json(name = "tmdb_id") val tmdbId: Long? = null,
    val type: String? = null,
    val links: List<MovixLink>? = null,
    val players: List<MovixLink>? = null,
    val seasons: List<MovixSeasonInfo>? = null
)

@JsonClass(generateAdapter = true)
data class MovixLink(
    val url: String? = null,
    val link: String? = null,
    val embed: String? = null,
    val host: String? = null,
    val hoster: String? = null,
    val provider: String? = null,
    val source: String? = null,
    val quality: String? = null,
    val language: String? = null,
    val lang: String? = null,
    val type: String? = null
) {
    fun bestUrl(): String? = url ?: link ?: embed
    fun displayName(): String = listOfNotNull(
        provider ?: source ?: host ?: hoster,
        quality,
        language ?: lang
    ).joinToString(" • ").ifBlank { "Source" }
}

@JsonClass(generateAdapter = true)
data class MovixSeasonInfo(
    @Json(name = "season_number") val seasonNumber: Int? = null,
    val season: Int? = null,
    val episodes: List<MovixEpisodeInfo>? = null
) {
    val number: Int get() = seasonNumber ?: season ?: 0
}

@JsonClass(generateAdapter = true)
data class MovixEpisodeInfo(
    @Json(name = "episode_number") val episodeNumber: Int? = null,
    val episode: Int? = null,
    val title: String? = null,
    val links: List<MovixLink>? = null,
    val players: List<MovixLink>? = null
) {
    val number: Int get() = episodeNumber ?: episode ?: 0
    fun allLinks(): List<MovixLink> = (links ?: emptyList()) + (players ?: emptyList())
}

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
