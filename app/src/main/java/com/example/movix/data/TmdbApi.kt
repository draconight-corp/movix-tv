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
}
