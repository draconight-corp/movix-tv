package com.example.movix

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import com.example.movix.data.MovixLink
import com.example.movix.data.Repository
import com.example.movix.mobile.MobilePlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Logique de lecture partagée par l'UI TV (Leanback) et l'UI mobile :
 * résolution des sources et choix du bon lecteur.
 *
 *  - flux direct (.m3u8, .mp4…) → PlaybackActivity sur TV, MobilePlayerActivity sur téléphone
 *  - page embed → WebPlaybackActivity (verrou anti-pop-up) sur les deux
 */
object PlayerLauncher {

    /** Vrai sur Android TV / Fire TV (télécommande), faux sur téléphone/tablette. */
    fun isTv(ctx: Context): Boolean {
        val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    /**
     * Recherche unifiée des sources : anime-sama (par titre) ET providers TMDB
     * en parallèle. anime-sama renvoie une liste vide pour les titres
     * non-animés, donc c'est sans effet pour films/séries.
     */
    suspend fun resolveSources(m: Movie, season: Int?, episode: Int?): List<MovixLink> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val anime = async {
                    val title = m.title
                    if (title.isNullOrBlank()) emptyList()
                    else runCatching { Repository.resolveAnimeSources(title, season, episode) }
                        .getOrDefault(emptyList())
                }
                val tmdb = async {
                    runCatching { Repository.aggregateAllSources(m.tmdbId, m.isTv, season, episode) }
                        .getOrDefault(emptyList())
                }
                anime.await() + tmdb.await()
            }
        }

    fun isDirectStream(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains(".mpd") ||
                lower.endsWith(".mp4") || lower.endsWith(".webm") ||
                lower.endsWith(".mkv")
    }

    fun sourceLabel(link: MovixLink): String = "[${link.category}] ${link.displayName()}"

    /** Intent de lecture d'un film / épisode à partir de la source [selectedIdx]. */
    fun intentFor(
        ctx: Context,
        m: Movie,
        links: List<MovixLink>,
        selectedIdx: Int,
        season: Int?,
        episode: Int?
    ): Intent {
        val url = links[selectedIdx].bestUrl()
        val urls = ArrayList(links.map { it.bestUrl() })
        val labels = ArrayList(links.map(::sourceLabel))
        return if (isDirectStream(url)) {
            Intent(ctx, directPlayerClass(ctx)).apply {
                putExtra(PlaybackActivity.EXTRA_URL, url)
                putExtra(PlaybackActivity.EXTRA_TITLE, m.title)
                putExtra(PlaybackActivity.EXTRA_DESCRIPTION, m.description)
                putStringArrayListExtra(PlaybackActivity.EXTRA_URLS, urls)
                putStringArrayListExtra(PlaybackActivity.EXTRA_LABELS, labels)
                putExtra(PlaybackActivity.EXTRA_INDEX, selectedIdx)
                putExtra(PlaybackActivity.EXTRA_TMDB_ID, m.tmdbId)
                putExtra(PlaybackActivity.EXTRA_IS_TV, m.isTv)
                if (season != null) putExtra(PlaybackActivity.EXTRA_SEASON, season)
                if (episode != null) putExtra(PlaybackActivity.EXTRA_EPISODE, episode)
                putExtra(PlaybackActivity.EXTRA_POSTER, m.cardImageUrl)
                putExtra(PlaybackActivity.EXTRA_BACKDROP, m.backgroundImageUrl)
            }
        } else {
            Intent(ctx, WebPlaybackActivity::class.java).apply {
                putExtra(WebPlaybackActivity.EXTRA_URL, url)
                putExtra(WebPlaybackActivity.EXTRA_TITLE, m.title)
                putStringArrayListExtra(WebPlaybackActivity.EXTRA_URLS, urls)
                putStringArrayListExtra(WebPlaybackActivity.EXTRA_LABELS, labels)
                putExtra(WebPlaybackActivity.EXTRA_INDEX, selectedIdx)
                putExtra(WebPlaybackActivity.EXTRA_TMDB_ID, m.tmdbId)
                putExtra(WebPlaybackActivity.EXTRA_IS_TV, m.isTv)
                if (season != null) putExtra(WebPlaybackActivity.EXTRA_SEASON, season)
                if (episode != null) putExtra(WebPlaybackActivity.EXTRA_EPISODE, episode)
                putExtra(WebPlaybackActivity.EXTRA_POSTER, m.cardImageUrl)
                putExtra(WebPlaybackActivity.EXTRA_BACKDROP, m.backgroundImageUrl)
            }
        }
    }

    /** Lecteur natif adapté à l'appareil (Leanback sur TV, tactile sur téléphone). */
    fun directPlayerClass(ctx: Context): Class<*> =
        if (isTv(ctx)) PlaybackActivity::class.java else MobilePlayerActivity::class.java
}
