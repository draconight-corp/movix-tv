package com.example.movix

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.movix.data.ApiClient
import com.example.movix.data.LiveTvCatalog
import com.example.movix.data.LiveTvChannel

/**
 * TV en direct — logique partagée TV / mobile (catalogues de
 * https://movix.luxe/live-tv).
 *
 * Seuls les catalogues gratuits et lisibles sont gardés :
 *  - vavoo_*     : chaînes par pays, flux HLS directs → lecteur natif (aucune pub)
 *  - northlive_* : chaînes françaises par thème, page lecteur web
 *  - streamed_all: matchs en direct, page lecteur web
 * Les pages web passent par WebPlaybackActivity (verrou anti-pop-up).
 */
object LiveTv {

    private const val TAG = "LiveTv"

    sealed class Opening {
        data class Ready(val intent: Intent) : Opening()
        data class Failed(val message: String) : Opening()
    }

    /** Catalogues supportés, triés (France d'abord). Liste vide si l'API est KO. */
    suspend fun catalogs(): List<LiveTvCatalog> =
        runCatching { ApiClient.movix.liveTvManifest().catalogs.orEmpty() }
            .onFailure { Log.w(TAG, "Manifest live TV KO", it) }
            .getOrDefault(emptyList())
            .filter(::isSupported)
            .sortedBy(::priority)

    suspend fun channels(catalogId: String): List<LiveTvChannel> =
        runCatching { ApiClient.movix.liveTvCatalog(catalogId).metas.orEmpty() }
            .onFailure { Log.w(TAG, "Catalogue $catalogId KO", it) }
            .getOrDefault(emptyList())
            .filter { it.id.isNotBlank() && !it.name.isNullOrBlank() }

    fun label(c: LiveTvCatalog): String = when {
        c.id == "vavoo_france" -> "France — toutes les chaînes"
        c.id == "vavoo_france-sport" -> "France — Sport"
        c.id.startsWith("northlive_") -> "France — ${c.name}"
        c.id == "streamed_all" -> "⚽ Matchs en direct"
        else -> c.name ?: c.id
    }

    /** Version courte pour les puces de l'UI mobile. */
    fun shortLabel(c: LiveTvCatalog): String = when {
        c.id == "vavoo_france" -> "🇫🇷 France"
        c.id == "vavoo_france-sport" -> "🇫🇷 Sport"
        c.id == "streamed_all" -> "⚽ Matchs"
        else -> c.name ?: c.id
    }

    /** Résout les flux d'une chaîne et construit l'intent du bon lecteur. */
    suspend fun open(ctx: Context, channel: LiveTvChannel): Opening {
        val streams = runCatching { ApiClient.movix.liveTvStreams(channel.id).streams.orEmpty() }
            .onFailure { Log.w(TAG, "Streams ${channel.id} KO", it) }
            .getOrDefault(emptyList())
            .filter { !it.url.isNullOrBlank() }
        val direct = streams.filter { it.isDirect }
        val chosen = direct.ifEmpty { streams }
        if (chosen.isEmpty()) {
            return Opening.Failed("Aucun flux disponible pour le moment (match pas encore commencé ?)")
        }
        val urls = ArrayList(chosen.map { it.url!! })
        val labels = ArrayList(chosen.mapIndexed { i, s -> s.title ?: "Source ${i + 1}" })
        val intent = if (direct.isNotEmpty()) {
            Intent(ctx, PlayerLauncher.directPlayerClass(ctx)).apply {
                putExtra(PlaybackActivity.EXTRA_URL, urls[0])
                putExtra(PlaybackActivity.EXTRA_TITLE, channel.name)
                putStringArrayListExtra(PlaybackActivity.EXTRA_URLS, urls)
                putStringArrayListExtra(PlaybackActivity.EXTRA_LABELS, labels)
                putExtra(PlaybackActivity.EXTRA_INDEX, 0)
            }
        } else {
            Intent(ctx, WebPlaybackActivity::class.java).apply {
                putExtra(WebPlaybackActivity.EXTRA_URL, urls[0])
                putExtra(WebPlaybackActivity.EXTRA_TITLE, channel.name)
                putStringArrayListExtra(WebPlaybackActivity.EXTRA_URLS, urls)
                putStringArrayListExtra(WebPlaybackActivity.EXTRA_LABELS, labels)
                putExtra(WebPlaybackActivity.EXTRA_INDEX, 0)
            }
        }
        return Opening.Ready(intent)
    }

    private fun isSupported(c: LiveTvCatalog): Boolean =
        c.id.startsWith("vavoo_") || c.id.startsWith("northlive_") || c.id == "streamed_all"

    /** France d'abord, puis les matchs, puis les autres pays. */
    private fun priority(c: LiveTvCatalog): Int = when {
        c.id == "vavoo_france" -> 0
        c.id == "vavoo_france-sport" -> 1
        c.id.startsWith("northlive_") -> 2
        c.id == "streamed_all" -> 3
        else -> 4
    }
}
