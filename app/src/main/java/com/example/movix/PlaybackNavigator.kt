package com.example.movix

import android.content.Context
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
        @Suppress("UNUSED_PARAMETER") ctx: Context,
        tmdbId: Long,
        currentSeason: Int,
        currentEpisode: Int
    ): NextEpisode? {
        // Tentative : même saison, épisode suivant
        val sameSeason = runCatching {
            Repository.aggregateAllSources(tmdbId, isTv = true, season = currentSeason, episode = currentEpisode + 1)
        }.getOrDefault(emptyList())
        if (sameSeason.isNotEmpty()) {
            return NextEpisode(currentSeason, currentEpisode + 1, sameSeason)
        }
        // Fallback : saison suivante, épisode 1
        val nextSeason = runCatching {
            Repository.aggregateAllSources(tmdbId, isTv = true, season = currentSeason + 1, episode = 1)
        }.getOrDefault(emptyList())
        if (nextSeason.isNotEmpty()) {
            return NextEpisode(currentSeason + 1, 1, nextSeason)
        }
        return null
    }
}
