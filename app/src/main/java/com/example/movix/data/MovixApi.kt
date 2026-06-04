package com.example.movix.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MovixApi {
    @GET("api/search")
    suspend fun search(@Query("title") title: String): MovixSearchResponse

    // ─── Coflix / Movix 1 ───────────────────────────────────────────────────
    @GET("api/tmdb/{type}/{id}")
    suspend fun sources(
        @Path("type") type: String,
        @Path("id") tmdbId: Long,
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null
    ): MovixSourcesResponse

    // ─── Cpasmal / Viper ────────────────────────────────────────────────────
    @GET("api/cpasmal/movie/{id}")
    suspend fun cpasmalMovie(@Path("id") tmdbId: Long): CpasmalResponse

    @GET("api/cpasmal/tv/{id}/{season}/{episode}")
    suspend fun cpasmalTv(
        @Path("id") tmdbId: Long,
        @Path("season") season: Int,
        @Path("episode") episode: Int
    ): CpasmalResponse

    // ─── Wiflix / Lynx ──────────────────────────────────────────────────────
    @GET("api/wiflix/movie/{id}")
    suspend fun wiflixMovie(@Path("id") tmdbId: Long): WiflixResponse

    @GET("api/wiflix/tv/{id}/{season}")
    suspend fun wiflixTvSeason(
        @Path("id") tmdbId: Long,
        @Path("season") season: Int
    ): WiflixResponse

    // ─── FStream ────────────────────────────────────────────────────────────
    @GET("api/fstream/movie/{id}")
    suspend fun fstreamMovie(@Path("id") tmdbId: Long): FstreamResponse

    // ─── Anime (anime-sama, proxifié par Movix) — recherche par TITRE ───────
    // NB : pas de préfixe /api ici. Renvoie un tableau de résultats avec
    // saisons + épisodes + streaming_links.
    @GET("anime/search/{query}")
    suspend fun animeSearch(
        @Path("query") query: String,
        @Query("includeSeasons") includeSeasons: Boolean = true,
        @Query("includeEpisodes") includeEpisodes: Boolean = true
    ): List<AnimeSearchResult>

    // ─── Custom links (Firebase) — d'où viennent les sources SeekStreaming ──
    // Pas de path saison/épisode côté TV : l'endpoint renvoie tous les épisodes
    // d'un coup, on filtre côté client.
    @GET("api/links/movie/{id}")
    suspend fun customLinksMovie(@Path("id") tmdbId: Long): MovixCustomLinksMovieResponse

    @GET("api/links/tv/{id}")
    suspend fun customLinksTv(@Path("id") tmdbId: Long): MovixCustomLinksTvResponse
}
