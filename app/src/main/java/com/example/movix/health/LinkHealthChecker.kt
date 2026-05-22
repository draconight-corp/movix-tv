package com.example.movix.health

import android.util.Log
import com.example.movix.data.MovixLink
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Vérifie l'état de vie des liens embed en parallèle (HEAD request).
 *
 * - Timeout par requête : 4 s
 * - Budget total : 6 s (au-delà, on considère les sources non vérifiées
 *   comme vivantes pour ne pas bloquer le user)
 * - Concurrence : 12 requêtes simultanées
 * - Cache mémoire : 5 min par URL
 *
 * Heuristique :
 *   - 2xx, 3xx, 403, 401 → vivant (les CDN renvoient souvent 403 sur HEAD
 *     pour anti-bot, mais la page existe)
 *   - 404, 410, 5xx → mort
 *   - DNS / TLS / timeout → mort
 */
object LinkHealthChecker {

    private const val TAG = "LinkHealthChecker"
    private const val PER_REQUEST_MS = 4000L
    private const val OVERALL_BUDGET_MS = 6000L
    private const val MAX_CONCURRENT = 12
    private const val CACHE_TTL_MS = 5L * 60 * 1000

    private val cache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(PER_REQUEST_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PER_REQUEST_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(PER_REQUEST_MS, TimeUnit.MILLISECONDS)
            .callTimeout(PER_REQUEST_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(false)
            .build()
    }

    data class Result(val alives: List<MovixLink>, val deads: List<MovixLink>)

    suspend fun classify(links: List<MovixLink>): Result {
        if (links.isEmpty()) return Result(emptyList(), emptyList())

        val checked = withTimeoutOrNull(OVERALL_BUDGET_MS) {
            coroutineScope {
                val sem = Semaphore(MAX_CONCURRENT)
                links.map { link ->
                    async {
                        sem.withPermit { link to checkOne(link.url) }
                    }
                }.awaitAll()
            }
        }

        if (checked == null) {
            // Budget global dépassé → fallback en alive pour ne pas frustrer l'user
            Log.w(TAG, "Health check budget exceeded; returning all as alive")
            return Result(links, emptyList())
        }

        val alives = ArrayList<MovixLink>(checked.size)
        val deads = ArrayList<MovixLink>()
        checked.forEach { (link, alive) ->
            if (alive) alives.add(link) else deads.add(link)
        }
        return Result(alives, deads)
    }

    private fun checkOne(url: String): Boolean {
        val now = System.currentTimeMillis()
        cache[url]?.let { (alive, at) ->
            if (now - at < CACHE_TTL_MS) return alive
        }

        val alive = try {
            doHead(url) ?: doRangedGet(url) ?: false
        } catch (t: Throwable) {
            Log.d(TAG, "Check failed for $url: ${t.message}")
            false
        }
        cache[url] = alive to now
        return alive
    }

    /** Renvoie true/false si on a une réponse exploitable, null si HEAD pas supporté. */
    private fun doHead(url: String): Boolean? {
        val req = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            .header("Accept", "*/*")
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                val c = resp.code
                when {
                    c in 200..399 -> true
                    c == 401 || c == 403 -> true  // anti-bot CDN, la page existe
                    c == 405 || c == 501 -> null  // HEAD pas supporté
                    else -> false
                }
            }
        }.getOrNull()
    }

    /** Fallback GET avec Range pour ne télécharger qu'un octet. */
    private fun doRangedGet(url: String): Boolean? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            .header("Range", "bytes=0-0")
            .header("Accept", "*/*")
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                val c = resp.code
                when {
                    c in 200..399 -> true
                    c == 401 || c == 403 -> true
                    else -> false
                }
            }
        }.getOrNull()
    }

    fun clearCache() = cache.clear()
}
