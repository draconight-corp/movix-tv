package com.example.movix.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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

    /**
     * Aggrège les sources de TOUS les providers Movix en parallèle.
     * Renvoie une liste unifiée de [MovixLink] avec champ `category` rempli
     * pour pouvoir grouper dans l'UI.
     */
    suspend fun aggregateAllSources(
        tmdbId: Long,
        isTv: Boolean,
        season: Int? = null,
        episode: Int? = null
    ): List<MovixLink> = coroutineScope {
        val movix1Deferred = async {
            runCatching {
                ApiClient.movix.sources(
                    type = if (isTv) "tv" else "movie",
                    tmdbId = tmdbId,
                    season = season,
                    episode = episode
                )
            }.getOrNull()
        }
        val cpasmalDeferred = async {
            runCatching {
                if (isTv && season != null && episode != null)
                    ApiClient.movix.cpasmalTv(tmdbId, season, episode)
                else if (!isTv)
                    ApiClient.movix.cpasmalMovie(tmdbId)
                else null
            }.getOrNull()
        }
        val wiflixDeferred = async {
            runCatching {
                if (isTv && season != null)
                    ApiClient.movix.wiflixTvSeason(tmdbId, season)
                else if (!isTv)
                    ApiClient.movix.wiflixMovie(tmdbId)
                else null
            }.getOrNull()
        }
        val fstreamDeferred = async {
            runCatching {
                if (!isTv) ApiClient.movix.fstreamMovie(tmdbId) else null
            }.getOrNull()
        }

        val all = mutableListOf<MovixLink>()
        all += parseMovix1(movix1Deferred.await(), season, episode)
        all += parseCpasmal(cpasmalDeferred.await())
        all += parseWiflix(wiflixDeferred.await())
        all += parseFstream(fstreamDeferred.await())
        all
    }

    private fun parseMovix1(
        resp: MovixSourcesResponse?,
        season: Int?,
        episode: Int?
    ): List<MovixLink> {
        if (resp == null) return emptyList()
        val ep = resp.currentEpisode
        val (rawLinks, iframe) = if (season != null && episode != null && ep != null)
            (ep.playerLinks.orEmpty()) to ep.iframeSrc
        else
            (resp.playerLinks.orEmpty()) to resp.iframeSrc

        val out = mutableListOf<MovixLink>()
        iframe?.takeIf { it.isNotBlank() }?.let {
            out += MovixLink(
                url = it,
                host = "Lecteur principal",
                language = "FR",
                quality = null,
                category = "Movix 1"
            )
        }
        rawLinks.forEach { l ->
            val u = l.bestUrl() ?: return@forEach
            out += MovixLink(
                url = u,
                host = hostOf(u),
                language = l.language ?: "FR",
                quality = l.quality,
                category = "Movix 1"
            )
        }
        return out
    }

    private fun parseCpasmal(resp: CpasmalResponse?): List<MovixLink> {
        if (resp?.links == null) return emptyList()
        val out = mutableListOf<MovixLink>()
        resp.links.vf?.forEach {
            val u = it.url ?: return@forEach
            out += MovixLink(
                url = u,
                host = it.server ?: hostOf(u),
                language = "VF",
                category = "Viper (Cpasmal) VF"
            )
        }
        resp.links.vostfr?.forEach {
            val u = it.url ?: return@forEach
            out += MovixLink(
                url = u,
                host = it.server ?: hostOf(u),
                language = "VOSTFR",
                category = "Viper (Cpasmal) VOSTFR"
            )
        }
        return out
    }

    private fun parseWiflix(resp: WiflixResponse?): List<MovixLink> {
        if (resp?.players == null) return emptyList()
        val out = mutableListOf<MovixLink>()
        resp.players.vf?.forEach {
            val u = it.url ?: return@forEach
            out += MovixLink(
                url = u,
                host = it.name ?: hostOf(u),
                language = it.type ?: "VF",
                category = "Wiflix (Lynx) VF"
            )
        }
        resp.players.vostfr?.forEach {
            val u = it.url ?: return@forEach
            out += MovixLink(
                url = u,
                host = it.name ?: hostOf(u),
                language = it.type ?: "VOSTFR",
                category = "Wiflix (Lynx) VOSTFR"
            )
        }
        return out
    }

    private fun parseFstream(resp: FstreamResponse?): List<MovixLink> {
        if (resp?.players == null) return emptyList()
        fun groupOf(items: List<FstreamLink>?, lang: String, category: String): List<MovixLink> =
            items.orEmpty().mapNotNull { l ->
                val u = l.url ?: return@mapNotNull null
                MovixLink(
                    url = u,
                    host = l.player ?: hostOf(u),
                    language = lang,
                    quality = l.quality,
                    category = category
                )
            }
        return groupOf(resp.players.vfq, "VFQ", "FStream VFQ") +
                groupOf(resp.players.vff, "VFF", "FStream VFF") +
                groupOf(resp.players.vostfr, "VOSTFR", "FStream VOSTFR") +
                groupOf(resp.players.default, "FR", "FStream Default")
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: "?"
}
