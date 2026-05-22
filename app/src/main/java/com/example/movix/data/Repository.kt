package com.example.movix.data

object Repository {

    suspend fun rows(): List<Pair<String, List<TmdbItem>>> {
        val popularMovies = runCatching { ApiClient.tmdb.popularMovies() }.getOrNull()
        val trendingWeek = runCatching { ApiClient.tmdb.trendingWeek() }.getOrNull()
        val nowPlaying = runCatching { ApiClient.tmdb.nowPlaying() }.getOrNull()
        val popularTv = runCatching { ApiClient.tmdb.popularTv() }.getOrNull()
        val topRated = runCatching { ApiClient.tmdb.topRatedMovies() }.getOrNull()

        return listOf(
            "Tendances de la semaine" to (trendingWeek?.results.orEmpty()),
            "Films populaires" to (popularMovies?.results.orEmpty()),
            "Au cinéma" to (nowPlaying?.results.orEmpty()),
            "Séries populaires" to (popularTv?.results.orEmpty()),
            "Mieux notés" to (topRated?.results.orEmpty()),
        ).filter { it.second.isNotEmpty() }
    }

    suspend fun details(item: TmdbItem): TmdbItem = runCatching {
        ApiClient.tmdb.details(
            type = if (item.isTv) "tv" else "movie",
            id = item.id
        )
    }.getOrDefault(item)

    suspend fun seasonDetails(tvId: Long, season: Int): TmdbSeasonDetail? = runCatching {
        ApiClient.tmdb.seasonDetails(tvId, season)
    }.getOrNull()

    suspend fun searchMovix(query: String): List<MovixSearchItem> = runCatching {
        ApiClient.movix.search(query).results
    }.getOrDefault(emptyList())

    suspend fun resolveSources(
        tmdbId: Long,
        isTv: Boolean,
        season: Int? = null,
        episode: Int? = null
    ): MovixSourcesResponse? = runCatching {
        ApiClient.movix.sources(
            type = if (isTv) "tv" else "movie",
            tmdbId = tmdbId,
            season = season,
            episode = episode
        )
    }.getOrNull()
}
