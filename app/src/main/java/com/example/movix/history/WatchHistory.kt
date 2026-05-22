package com.example.movix.history

import android.content.Context
import com.example.movix.Movie
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class WatchEntry(
    val tmdbId: Long,
    val isTv: Boolean,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val lastSeason: Int?,
    val lastEpisode: Int?,
    val updatedAt: Long
)

/**
 * Historique de visionnage persistant (SharedPreferences + JSON).
 * Stocke les 20 dernières entrées. Pour les séries, mémorise la dernière
 * saison/épisode lancé pour permettre une reprise rapide.
 */
object WatchHistory {

    private const val PREFS = "movix_history"
    private const val KEY_ENTRIES = "entries_json"
    private const val MAX_ENTRIES = 20

    private val moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }
    private val listType = Types.newParameterizedType(List::class.java, WatchEntry::class.java)
    private val adapter by lazy { moshi.adapter<List<WatchEntry>>(listType) }

    fun record(ctx: Context, movie: Movie, season: Int? = null, episode: Int? = null) {
        val entries = all(ctx).toMutableList()
        entries.removeAll { it.tmdbId == movie.tmdbId && it.isTv == movie.isTv }
        entries.add(0, WatchEntry(
            tmdbId = movie.tmdbId,
            isTv = movie.isTv,
            title = movie.title.orEmpty(),
            posterUrl = movie.cardImageUrl,
            backdropUrl = movie.backgroundImageUrl,
            lastSeason = season,
            lastEpisode = episode,
            updatedAt = System.currentTimeMillis()
        ))
        if (entries.size > MAX_ENTRIES) {
            entries.subList(MAX_ENTRIES, entries.size).clear()
        }
        save(ctx, entries)
    }

    fun all(ctx: Context): List<WatchEntry> {
        val raw = ctx.getSharedPreferences(PREFS, 0).getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching { adapter.fromJson(raw) ?: emptyList() }.getOrDefault(emptyList())
    }

    fun forMovie(ctx: Context, tmdbId: Long, isTv: Boolean): WatchEntry? =
        all(ctx).firstOrNull { it.tmdbId == tmdbId && it.isTv == isTv }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, 0).edit().remove(KEY_ENTRIES).apply()
    }

    private fun save(ctx: Context, entries: List<WatchEntry>) {
        val json = adapter.toJson(entries)
        ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY_ENTRIES, json).apply()
    }
}
