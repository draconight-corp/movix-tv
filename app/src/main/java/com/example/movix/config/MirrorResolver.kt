package com.example.movix.config

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Détecte le domaine officiel courant de Movix en scrapant movix.health
 * (page de redirection maintenue par les opérateurs, qui annonce l'adresse
 * active dans son <title> et son JSON-LD).
 */
object MirrorResolver {

    private const val TAG = "MirrorResolver"
    private const val REDIRECTOR_URL = "https://movix.health"

    // Domaines connus comme morts (listés sur la page de redirection elle-même).
    private val DEAD_DOMAINS = setOf(
        "movix.health",
        "movix.help", "movix.llc", "movix.rodeo", "movix.blog",
        "movix.club", "movix.website", "movix.site",
        "movix.tax"
    )

    // Hôtes de fallback si l'auto-détection échoue (à essayer dans l'ordre).
    val FALLBACK_HOSTS = listOf(
        "api.movix.fun",
        "api.movix.show",
        "api.movix.date",
        "api.movix.chat",
        "api.movix.golf",
        "api.movix.cloud"
    )

    private val TITLE_RX = """<title>[^<]*\|\s*(movix\.[a-z0-9]+)""".toRegex(RegexOption.IGNORE_CASE)
    private val LDJSON_URL_RX = """"url"\s*:\s*"https://(movix\.[a-z0-9]+)/?""""
        .toRegex(RegexOption.IGNORE_CASE)
    private val ANY_DOMAIN_RX = """movix\.[a-z0-9]+""".toRegex(RegexOption.IGNORE_CASE)

    private val resolverClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Récupère movix.health, en extrait le domaine actif, persiste le résultat.
     * Renvoie l'hôte API (préfixe api.) ou null si la détection échoue.
     */
    suspend fun resolve(ctx: Context): String? = withContext(Dispatchers.IO) {
        val html = runCatching {
            resolverClient.newCall(
                Request.Builder()
                    .url(REDIRECTOR_URL)
                    .header("User-Agent", "Mozilla/5.0 (Android TV)")
                    .header("Accept", "text/html")
                    .build()
            ).execute().use { it.body?.string() }
        }.onFailure { Log.w(TAG, "Resolver fetch failed: ${it.message}") }.getOrNull()
            ?: return@withContext null

        val frontHost = extractFrontHost(html) ?: return@withContext null
        if (frontHost in DEAD_DOMAINS) {
            Log.w(TAG, "Resolved $frontHost but it's in the dead list — ignoring")
            return@withContext null
        }

        val apiHost = "api.$frontHost"
        MovixConfig.setResolved(ctx, apiHost)
        Log.i(TAG, "Resolved current Movix host: $apiHost")
        apiHost
    }

    private fun extractFrontHost(html: String): String? {
        // 1. <title>...| movix.xyz</title> — convention de la page
        TITLE_RX.find(html)?.groupValues?.getOrNull(1)?.lowercase()?.let { return it }
        // 2. JSON-LD "url": "https://movix.xyz/"
        LDJSON_URL_RX.find(html)?.groupValues?.getOrNull(1)?.lowercase()
            ?.takeIf { it !in DEAD_DOMAINS }?.let { return it }
        // 3. Heuristique : domaine movix.XXX le plus mentionné, hors morts
        val counts = ANY_DOMAIN_RX.findAll(html)
            .map { it.value.lowercase() }
            .filter { it !in DEAD_DOMAINS }
            .groupingBy { it }
            .eachCount()
        return counts.maxByOrNull { it.value }?.key
    }
}
