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
    @Json(name = "player_links") val playerLinks: List<TmdbProviderLink>? = null,
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
    @Json(name = "player_links") val playerLinks: List<TmdbProviderLink>? = null
)

/**
 * Lien de stream unifié, indépendant du provider d'origine.
 * Le `category` permet de regrouper dans l'UI ("Movix 1", "Viper VF",
 * "Wiflix VF", "FStream VFQ", etc.).
 */
data class MovixLink(
    val url: String,
    val host: String? = null,
    val language: String? = null,
    val quality: String? = null,
    val category: String = "Source"
) {
    fun bestUrl(): String = url
    fun displayName(): String = listOfNotNull(
        host?.takeIf { it.isNotBlank() },
        quality?.takeIf { it.isNotBlank() },
        language?.takeIf { it.isNotBlank() }
    ).joinToString(" • ").ifBlank { "Source" }
}

// Parseur Moshi générique pour les anciennes réponses {decoded_url, quality, language, clone_url}
@JsonClass(generateAdapter = true)
data class TmdbProviderLink(
    @Json(name = "decoded_url") val decodedUrl: String? = null,
    @Json(name = "clone_url") val cloneUrl: String? = null,
    val quality: String? = null,
    val language: String? = null
) {
    fun bestUrl(): String? = decodedUrl ?: cloneUrl
}

// Cpasmal / Viper : {links: {vf: [{server, url}], vostfr: [...]}}
@JsonClass(generateAdapter = true)
data class CpasmalResponse(
    val title: String? = null,
    val year: String? = null,
    val links: CpasmalGroups? = null
)
@JsonClass(generateAdapter = true)
data class CpasmalGroups(
    val vf: List<CpasmalLink>? = null,
    val vostfr: List<CpasmalLink>? = null
)
@JsonClass(generateAdapter = true)
data class CpasmalLink(
    val server: String? = null,
    val url: String? = null
)

// Wiflix / Lynx : {players: {vf: [{name, url, type}], vostfr: [...]}}
@JsonClass(generateAdapter = true)
data class WiflixResponse(
    val success: Boolean? = null,
    val title: String? = null,
    val players: WiflixGroups? = null
)
@JsonClass(generateAdapter = true)
data class WiflixGroups(
    val vf: List<WiflixLink>? = null,
    val vostfr: List<WiflixLink>? = null
)
@JsonClass(generateAdapter = true)
data class WiflixLink(
    val name: String? = null,
    val url: String? = null,
    val type: String? = null
)

// FStream : {players: {VFQ: [...], VFF: [...], VOSTFR: [...], Default: [...]}}
@JsonClass(generateAdapter = true)
data class FstreamResponse(
    val success: Boolean? = null,
    val players: FstreamGroups? = null
)
@JsonClass(generateAdapter = true)
data class FstreamGroups(
    @Json(name = "VFQ") val vfq: List<FstreamLink>? = null,
    @Json(name = "VFF") val vff: List<FstreamLink>? = null,
    @Json(name = "VOSTFR") val vostfr: List<FstreamLink>? = null,
    @Json(name = "Default") val default: List<FstreamLink>? = null
)
@JsonClass(generateAdapter = true)
data class FstreamLink(
    val url: String? = null,
    val type: String? = null,
    val quality: String? = null,
    val player: String? = null
)

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

// Custom links (Firebase) : sources additionnelles dont SeekStreaming.
// Films : {success, type:"movie", data:{id, links:[url, ...]}}
@JsonClass(generateAdapter = true)
data class MovixCustomLinksMovieResponse(
    val success: Boolean? = null,
    val type: String? = null,
    val data: MovixCustomLinksMovieData? = null
)
@JsonClass(generateAdapter = true)
data class MovixCustomLinksMovieData(
    val id: String? = null,
    val links: List<String>? = null
)

// Séries : {success, type:"tv", data:[{id, series_id, season_number, episode_number, links:[]}, ...]}
@JsonClass(generateAdapter = true)
data class MovixCustomLinksTvResponse(
    val success: Boolean? = null,
    val type: String? = null,
    val data: List<MovixCustomLinksTvEpisode>? = null
)
@JsonClass(generateAdapter = true)
data class MovixCustomLinksTvEpisode(
    val id: Long? = null,
    @Json(name = "series_id") val seriesId: String? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episode_number") val episodeNumber: Int? = null,
    val links: List<String>? = null
)

// ─── Anime (anime-sama, proxifié par api.movix.cloud/anime/search) ─────────
// GET /anime/search/{titre}?includeSeasons=true&includeEpisodes=true
// Réponse = tableau de résultats triés par pertinence. Chaque résultat a des
// saisons ("Saison 1", "Film"…), chaque saison des épisodes, chaque épisode
// des streaming_links groupés par langue (vf / vostfr) avec une liste de
// players (embeds Vidmoly / Sibnet / Sendvid…).
@JsonClass(generateAdapter = true)
data class AnimeSearchResult(
    val url: String? = null,
    val name: String? = null,
    val image: String? = null,
    @Json(name = "alternative_names") val alternativeNames: List<String>? = null,
    @Json(name = "alternative_names_string") val alternativeNamesString: String? = null,
    val seasons: List<AnimeSeasonData>? = null
)

@JsonClass(generateAdapter = true)
data class AnimeSeasonData(
    val name: String? = null,
    @Json(name = "episodeCount") val episodeCount: Int? = null,
    val episodes: List<AnimeEpisodeData>? = null
)

@JsonClass(generateAdapter = true)
data class AnimeEpisodeData(
    val name: String? = null,
    @Json(name = "serie_name") val serieName: String? = null,
    @Json(name = "season_name") val seasonName: String? = null,
    val index: Int? = null,
    @Json(name = "streaming_links") val streamingLinks: List<AnimeStreamingLink>? = null
)

@JsonClass(generateAdapter = true)
data class AnimeStreamingLink(
    val language: String? = null,
    val players: List<String>? = null
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
