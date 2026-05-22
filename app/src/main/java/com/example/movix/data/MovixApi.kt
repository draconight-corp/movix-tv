package com.example.movix.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MovixApi {
    @GET("api/search")
    suspend fun search(@Query("title") title: String): MovixSearchResponse

    /**
     * Récupère les sources de stream pour un film ou un épisode.
     * type = "movie" ou "tv". Pour un épisode, fournir season + episode.
     */
    @GET("api/tmdb/{type}/{id}")
    suspend fun sources(
        @Path("type") type: String,
        @Path("id") tmdbId: Long,
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null
    ): MovixSourcesResponse
}
