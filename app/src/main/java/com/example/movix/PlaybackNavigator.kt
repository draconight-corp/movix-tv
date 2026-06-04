package com.example.movix

import android.content.Context
import com.example.movix.config.AppMode
import com.example.movix.config.AppModeStore
import com.example.movix.data.MovixLink
import com.example.movix.data.Repository

/**
 * Petit utilitaire partagé par les activités de lecture pour résoudre
 * l'épisode suivant d'une série (saison courante, épisode N+1 — sinon
 * saison N+1, épisode 1).
 */
object PlaybackNavigator {

    data class NextEpisode(
        val season: Int,
        val episode: Int,
        val links: List<MovixLink>
    )

    /**
     * Suspend — appelle depuis lifecycleScope sur Dispatchers.IO si possible.
     * Renvoie null s'il n'y a pas d'épisode suivant trouvé.
     */
    suspend fun resolveNext(
        ctx: Context,
        tmdbId: Long,
        currentSeason: Int,
        currentEpisode: Int,
        title: String? = null
    ): NextEpisode? {
        val isAnime = runCatching {
            AppModeStore.current(ctx.applicationContext) == AppMode.ANIME
        }.getOrDefault(false)

        // Résout les sources d'un (saison, épisode) : anime-sama d'abord en mode
        // Animé, sinon (ou en repli) les providers TMDB classiques.
        suspend fun sourcesFor(season: Int, episode: Int): List<MovixLink> {
            if (isAnime && !title.isNullOrBlank()) {
                val anime = runCatching {
                    Repository.resolveAnimeSources(title, season, episode)
                }.getOrDefault(emptyList())
                if (anime.isNotEmpty()) return anime
            }
            return runCatching {
                Repository.aggregateAllSources(tmdbId, isTv = true, season = season, episode = episode)
            }.getOrDefault(emptyList())
        }

        // Tentative : même saison, épisode suivant
        val sameSeason = sourcesFor(currentSeason, currentEpisode + 1)
        if (sameSeason.isNotEmpty()) {
            return NextEpisode(currentSeason, currentEpisode + 1, sameSeason)
        }
        // Fallback : saison suivante, épisode 1
        val nextSeason = sourcesFor(currentSeason + 1, 1)
        if (nextSeason.isNotEmpty()) {
            return NextEpisode(currentSeason + 1, 1, nextSeason)
        }
        return null
    }
}
