package com.example.movix.config

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Détecte le domaine Movix actif, sans republier d'APK. Candidats, dans l'ordre :
 *   1. movix.online — page officielle de redirection, qui annonce l'adresse
 *      active à la fin de son <title> (« … | movix.dog »).
 *   2. `domain.json` à la racine du repo GitHub (branche main) — secours éditable
 *      à la main si movix.online tombe ou change de format.
 *   3. Le dernier hôte résolu, le défaut et les fallbacks connus.
 * Chaque candidat est vérifié par un vrai appel API avant d'être retenu.
 */
object MirrorResolver {

    private const val TAG = "MirrorResolver"
    private const val REDIRECTOR_URL = "https://movix.online"
    private const val DOMAIN_FILE_URL =
        "https://raw.githubusercontent.com/draconight-corp/movix-tv/main/domain.json"

    // Domaines à ne jamais retenir : redirecteurs et clones frauduleux
    // (movix.online liste movix.bet & co comme faux sites qui imitent Movix).
    private val BLOCKED_SITES = setOf("movix.online", "movix.health", "movix.bet")

    private val TITLE_RX = """<title>[^<]*\|\s*(movix\.[a-z0-9]+)\s*</title>"""
        .toRegex(RegexOption.IGNORE_CASE)

    // Hôtes de fallback si l'auto-détection échoue (à essayer dans l'ordre).
    val FALLBACK_HOSTS = listOf(
        "api.movix.dog",
        "api.movix.luxe",
        "api.movix.fun",
        "api.movix.show"
    )

    private val resolverClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Trouve l'hôte API actif, le persiste et le renvoie (null si aucun ne répond).
     */
    suspend fun resolve(ctx: Context): String? = withContext(Dispatchers.IO) {
        val candidates = buildList {
            fetchRedirectorHost()?.let { add(it) }
            fetchRemoteHost()?.let { add(it) }
            MovixConfig.lastResolved(ctx)?.let { add(it) }
            add(MovixConfig.DEFAULT_API_HOST)
            addAll(FALLBACK_HOSTS)
        }.distinct().filter { it.removePrefix("api.") !in BLOCKED_SITES }

        val alive = candidates.firstOrNull { isAlive(it) }
        if (alive == null) {
            Log.w(TAG, "No Movix host answered among $candidates")
            return@withContext null
        }
        MovixConfig.setResolved(ctx, alive)
        Log.i(TAG, "Resolved current Movix host: $alive")
        alive
    }

    /** Lit le domaine annoncé dans le <title> de movix.online. */
    private fun fetchRedirectorHost(): String? = runCatching {
        val html = resolverClient.newCall(
            Request.Builder()
                .url(REDIRECTOR_URL)
                .header("User-Agent", "Mozilla/5.0 (Android TV)")
                .header("Accept", "text/html")
                .build()
        ).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
        TITLE_RX.find(html)?.groupValues?.get(1)?.lowercase()?.let { "api.$it" }
    }.onFailure { Log.w(TAG, "Redirector fetch failed: ${it.message}") }.getOrNull()

    /** Lit domain.json : {"api": "api.movix.xxx"} ou {"site": "movix.xxx"}. */
    private fun fetchRemoteHost(): String? = runCatching {
        val body = resolverClient.newCall(
            Request.Builder().url(DOMAIN_FILE_URL).header("Cache-Control", "no-cache").build()
        ).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
        val json = JSONObject(body)
        val api = json.optString("api").trim().lowercase()
        val site = json.optString("site").trim().lowercase()
        when {
            api.isNotEmpty() -> api
            site.isNotEmpty() -> "api.$site"
            else -> null
        }
    }.onFailure { Log.w(TAG, "domain.json fetch failed: ${it.message}") }.getOrNull()

    /** Un hôte est vivant s'il répond 200 à une recherche avec l'Origin du site associé. */
    private fun isAlive(apiHost: String): Boolean = runCatching {
        val site = apiHost.removePrefix("api.")
        resolverClient.newCall(
            Request.Builder()
                .url("https://$apiHost/api/search?title=test")
                .header("User-Agent", "Mozilla/5.0 (Android TV)")
                .header("Origin", "https://$site")
                .header("Referer", "https://$site/")
                .build()
        ).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}
