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
}
