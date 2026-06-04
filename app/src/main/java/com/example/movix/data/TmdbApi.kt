package com.example.movix.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("movie/popular")
    suspend fun popularMovies(
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE,
        @Query("page") page: Int = 1
    ): TmdbListResponse

    @GET("movie/top_rated")
    suspend fun topRatedMovies(
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE,
        @Query("page") page: Int = 1
    ): TmdbListResponse

    @GET("movie/now_playing")
    suspend fun nowPlaying(
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE,
        @Query("page") page: Int = 1,
        @Query("region") region: String = "FR"
    ): TmdbListResponse

    @GET("tv/popular")
    suspend fun popularTv(
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE,
        @Query("page") page: Int = 1
    ): TmdbListResponse

    @GET("trending/all/week")
    suspend fun trendingWeek(
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE
    ): TmdbListResponse

    @GET("trending/all/day")
    suspend fun trendingToday(
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE
    ): TmdbListResponse

    @GET("{type}/{id}")
    suspend fun details(
        @Path("type") type: String,
        @Path("id") id: Long,
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE
    ): TmdbItem

    @GET("tv/{id}/season/{season}")
    suspend fun seasonDetails(
        @Path("id") id: Long,
        @Path("season") season: Int,
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE
    ): TmdbSeasonDetail

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE
    ): TmdbListResponse

    // ── Catalogue avancé : pagination + filtres par genre/année ──────────────

    @GET("movie/popular")
    suspend fun popularMoviesPage(
        @Query("page") page: Int,
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE
    ): TmdbListResponse

    @GET("tv/popular")
    suspend fun popularTvPage(
        @Query("page") page: Int,
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE
    ): TmdbListResponse

    /**
     * Découverte de films, filtrée par genre TMDB. Renvoie une page de résultats
     * triée par popularité décroissante (ce que la plupart des UIs veulent).
     */
    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("with_genres") genres: String,
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("vote_count.gte") minVotes: Int = 50,
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE
    ): TmdbListResponse

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("with_genres") genres: String,
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") minVotes: Int = 50,
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE
    ): TmdbListResponse

    // ── Anime : discover filtré par genre animation + pays d'origine ──────────
    // Pour les animés japonais, on combine genre 16 (Animation) + origin_country=JP.
    // Pas de minVotes ici (beaucoup d'animés cultes ont peu de votes côté TMDB FR).

    @GET("discover/tv")
    suspend fun discoverAnimeTv(
        @Query("with_genres") genres: String = "16",
        @Query("with_origin_country") originCountry: String = "JP",
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE
    ): TmdbListResponse

    // ── Dessins animés occidentaux (Rick et Morty, BoJack, Family Guy…) ───────
    // Genre 16 (Animation) + origine US/GB/CA. Ces titres ne sont PAS japonais
    // et étaient donc exclus du mode Animé filtré sur origin_country=JP.
    @GET("discover/tv")
    suspend fun discoverWesternAnimationTv(
        @Query("with_genres") genres: String = "16",
        @Query("with_origin_country") originCountry: String = "US|GB|CA",
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") minVotes: Int = 50,
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE
    ): TmdbListResponse

    // ── Suggestions "au hasard" pour le défilement infini (mode Films) ────────
    // discover/movie sans filtre de genre, trié par popularité, paginé.
    @GET("discover/movie")
    suspend fun discoverAllMovies(
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") minVotes: Int = 150,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE
    ): TmdbListResponse

    @GET("discover/movie")
    suspend fun discoverAnimeMovies(
        @Query("with_genres") genres: String = "16",
        @Query("with_original_language") originalLanguage: String = "ja",
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("api_key") apiKey: String = ApiConfig.TMDB_API_KEY,
        @Query("language") language: String = ApiConfig.TMDB_LANGUAGE
    ): TmdbListResponse
}
