package com.example.movix.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object Repository {

    /**
     * Genres TMDB exposés en rangées dédiées sur l'écran principal.
     * IDs : https://developer.themoviedb.org/reference/genre-movie-list
     */
    private val MOVIE_GENRES: List<Pair<String, Int>> = listOf(
        "Action" to 28,
        "Comédie" to 35,
        "Drame" to 18,
        "Aventure" to 12,
        "Animation" to 16,
        "Science-fiction" to 878,
        "Horreur" to 27,
        "Thriller" to 53,
        "Romance" to 10749,
        "Famille" to 10751,
        "Fantastique" to 14,
        "Policier" to 80,
        "Documentaire" to 99,
        "Mystère" to 9648,
        "Guerre" to 10752,
        "Western" to 37,
        "Histoire" to 36
        // "Musique" (10402) retiré : rangée souvent vide et faisait doublon avec les films.
    )

    private val TV_GENRES: List<Pair<String, Int>> = listOf(
        "Séries Drame" to 18,
        "Séries Comédie" to 35,
        "Séries Action & Aventure" to 10759,
        "Séries Science-fiction & Fantastique" to 10765,
        "Séries Crime" to 80,
        "Séries Animation" to 16
    )

    /**
     * Genres TMDB combinés avec un filtre animation+JP pour le mode Animé.
     * On utilise les genres TV "Action & Aventure" et "SF & Fantastique" qui
     * sont mieux peuplés que les équivalents film côté animés japonais.
     */
    private val ANIME_TV_GENRES: List<Pair<String, String>> = listOf(
        "Animés Action & Aventure" to "16,10759",
        "Animés Science-fiction & Fantastique" to "16,10765",
        "Animés Comédie" to "16,35",
        "Animés Drame" to "16,18",
        "Animés Mystère" to "16,9648"
    )

    suspend fun rows(mode: com.example.movix.config.AppMode): List<Pair<String, List<TmdbItem>>> =
        when (mode) {
            com.example.movix.config.AppMode.ANIME -> animeRows()
            com.example.movix.config.AppMode.FILMS_SERIES -> classicRows()
        }

    /**
     * Catalogue Animé : séries et films d'animation japonais, par popularité,
     * notoriété et genre. On agrège plusieurs pages pour avoir un catalogue
     * fourni, puis on dédoublonne par id.
     */
    private suspend fun animeRows(): List<Pair<String, List<TmdbItem>>> = coroutineScope {
        val popularAnimeTvD = async {
            fetchPages(2) { ApiClient.tmdb.discoverAnimeTv(page = it) }
        }
        val topAnimeTvD = async {
            runCatching {
                ApiClient.tmdb.discoverAnimeTv(sortBy = "vote_average.desc").results
                    .filter { (it.voteAverage ?: 0.0) >= 7.0 }
            }.getOrDefault(emptyList())
        }
        val popularAnimeMoviesD = async {
            fetchPages(2) { ApiClient.tmdb.discoverAnimeMovies(page = it) }
        }
        val topAnimeMoviesD = async {
            runCatching {
                ApiClient.tmdb.discoverAnimeMovies(sortBy = "vote_average.desc").results
                    .filter { (it.voteAverage ?: 0.0) >= 7.0 }
            }.getOrDefault(emptyList())
        }
        val recentAnimeD = async {
            runCatching {
                ApiClient.tmdb.discoverAnimeTv(sortBy = "first_air_date.desc").results
                    .filter { !it.firstAirDate.isNullOrBlank() }
            }.getOrDefault(emptyList())
        }
        val genreDeferreds = ANIME_TV_GENRES.map { (label, genres) ->
            label to async {
                runCatching {
                    ApiClient.tmdb.discoverAnimeTv(genres = genres).results
                }.getOrDefault(emptyList())
            }
        }

        val baseRows = listOf(
            "Animés populaires"        to popularAnimeTvD.await(),
            "Films d'animation populaires" to popularAnimeMoviesD.await(),
            "Animés mieux notés"       to topAnimeTvD.await(),
            "Films d'animation cultes" to topAnimeMoviesD.await(),
            "Nouveautés animés"        to recentAnimeD.await()
        )
        val genreRows = genreDeferreds.map { (label, def) -> label to def.await() }
        (baseRows + genreRows).filter { it.second.isNotEmpty() }
    }

    private suspend fun classicRows(): List<Pair<String, List<TmdbItem>>> = coroutineScope {
        // ── Rangées "classiques" (multi-pages pour avoir plus de films) ──
        val popularMoviesD = async { fetchPages(3) { ApiClient.tmdb.popularMoviesPage(it) } }
        val popularTvD     = async { fetchPages(2) { ApiClient.tmdb.popularTvPage(it) } }
        val trendingWeekD  = async { runCatching { ApiClient.tmdb.trendingWeek() }.getOrNull()?.results.orEmpty() }
        val nowPlayingD    = async { runCatching { ApiClient.tmdb.nowPlaying() }.getOrNull()?.results.orEmpty() }
        val topRatedD      = async { runCatching { ApiClient.tmdb.topRatedMovies() }.getOrNull()?.results.orEmpty() }

        // ── Rangées par catégorie (en parallèle, 1 page par genre) ──
        val movieGenreDeferreds = MOVIE_GENRES.map { (label, id) ->
            label to async {
                runCatching {
                    ApiClient.tmdb.discoverMovies(genres = id.toString()).results
                }.getOrDefault(emptyList())
            }
        }
        val tvGenreDeferreds = TV_GENRES.map { (label, id) ->
            label to async {
                runCatching {
                    ApiClient.tmdb.discoverTv(genres = id.toString()).results
                }.getOrDefault(emptyList())
            }
        }

        val baseRows = listOf(
            "Tendances de la semaine" to trendingWeekD.await(),
            "Films populaires"        to popularMoviesD.await(),
            "Au cinéma"               to nowPlayingD.await(),
            "Séries populaires"       to popularTvD.await(),
            "Mieux notés"             to topRatedD.await()
        )
        val movieGenreRows = movieGenreDeferreds.map { (label, def) -> label to def.await() }
        val tvGenreRows = tvGenreDeferreds.map { (label, def) -> label to def.await() }

        (baseRows + movieGenreRows + tvGenreRows).filter { it.second.isNotEmpty() }
    }

    /**
     * Récupère N pages d'un endpoint TMDB en parallèle puis dédoublonne par id.
     * Tolère les échecs partiels.
     */
    private suspend fun fetchPages(
        pages: Int,
        loader: suspend (page: Int) -> TmdbListResponse
    ): List<TmdbItem> = coroutineScope {
        val deferreds = (1..pages).map { p ->
            async { runCatching { loader(p) }.getOrNull()?.results.orEmpty() }
        }
        val seen = mutableSetOf<Long>()
        deferreds.flatMap { it.await() }.filter { seen.add(it.id) }
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

    /**
     * Recherche d'animés : utilise search/multi de TMDB puis garde uniquement
     * les résultats dont la langue d'origine est ja ET le genre 16 (Animation).
     * Mappé vers MovixSearchItem pour réutiliser le pipeline existant.
     */
    suspend fun searchAnimeTmdb(query: String): List<MovixSearchItem> = runCatching {
        val resp = ApiClient.tmdb.searchMulti(query = query)
        resp.results
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            .filter { it.isAnime }
            .map { it.toMovixSearchItem() }
    }.getOrDefault(emptyList())

    private fun TmdbItem.toMovixSearchItem(): MovixSearchItem = MovixSearchItem(
        id = id,
        tmdbId = id,
        title = title,
        name = name,
        type = if (mediaType == "tv" || (name != null && title == null)) "tv" else "movie",
        modelType = mediaType,
        posterPath = posterPath,
        poster = ApiConfig.posterUrl(posterPath),
        overview = overview,
        releaseDate = releaseDate,
        firstAirDate = firstAirDate,
        voteAverage = voteAverage
    )

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
        val customMovieDeferred = async {
            runCatching {
                if (!isTv) ApiClient.movix.customLinksMovie(tmdbId) else null
            }.getOrNull()
        }
        val customTvDeferred = async {
            runCatching {
                if (isTv) ApiClient.movix.customLinksTv(tmdbId) else null
            }.getOrNull()
        }

        val all = mutableListOf<MovixLink>()
        all += parseMovix1(movix1Deferred.await(), season, episode)
        all += parseCpasmal(cpasmalDeferred.await())
        all += parseWiflix(wiflixDeferred.await())
        all += parseFstream(fstreamDeferred.await())
        all += parseCustomMovieLinks(customMovieDeferred.await())
        all += parseCustomTvLinks(customTvDeferred.await(), season, episode)
        all.sortedWith(preferredOrder)
    }

    /**
     * Détecte si une URL matche un host SeekStreaming connu (les patterns
     * viennent de hosterRegistry.ts côté MovixOpenSource).
     */
    private fun isSeekStreaming(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("embedseek") || u.contains("embed4me") || u.contains("seekstreaming")
    }

    /**
     * Numérote les liens custom-links pour matcher l'affichage du site Movix :
     * les URLs SeekStreaming sont labellisées "SeekStreaming 1..N", les autres
     * conservent leur host comme nom.
     */
    private fun numberCustomLinks(urls: List<String>): List<MovixLink> {
        var seekIdx = 0
        return urls.mapNotNull { raw ->
            val url = raw.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (isSeekStreaming(url)) {
                seekIdx += 1
                MovixLink(
                    url = url,
                    host = "SeekStreaming $seekIdx",
                    language = "FR",
                    category = "SeekStreaming"
                )
            } else {
                MovixLink(
                    url = url,
                    host = hostOf(url),
                    language = "FR",
                    category = "Custom links"
                )
            }
        }
    }

    private fun parseCustomMovieLinks(resp: MovixCustomLinksMovieResponse?): List<MovixLink> {
        val urls = resp?.data?.links.orEmpty()
        return numberCustomLinks(urls)
    }

    private fun parseCustomTvLinks(
        resp: MovixCustomLinksTvResponse?,
        season: Int?,
        episode: Int?
    ): List<MovixLink> {
        if (resp?.data == null || season == null || episode == null) return emptyList()
        val ep = resp.data.firstOrNull {
            it.seasonNumber == season && it.episodeNumber == episode
        } ?: return emptyList()
        return numberCustomLinks(ep.links.orEmpty())
    }

    /**
     * Ordonne les sources pour privilégier d'abord Viper (Cpasmal), puis les
     * hôtes voe / uqload, puis le reste. Stable à l'intérieur de chaque bucket.
     */
    private val preferredOrder: Comparator<MovixLink> = Comparator { a, b ->
        priorityOf(a).compareTo(priorityOf(b))
    }

    private fun priorityOf(link: MovixLink): Int {
        val cat = link.category.lowercase()
        val host = (link.host ?: "").lowercase()
        val url = link.url.lowercase()
        // 0 = Viper (Cpasmal) — la source la plus fiable historiquement
        if (cat.contains("viper") || cat.contains("cpasmal")) return 0
        // 1 = hôtes VOE / UQLOAD quelle que soit la catégorie d'origine
        if ("voe" in host || "voe.sx" in url || "voe-" in url) return 1
        if ("uqload" in host || "uqload" in url) return 1
        // 2 = autres hôtes populaires (vidmoly, doodstream, filemoon)
        if ("vidmoly" in host || "doodstream" in host || "filemoon" in host) return 2
        // 3 = reste
        return 3
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
