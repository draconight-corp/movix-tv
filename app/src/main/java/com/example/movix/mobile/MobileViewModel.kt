package com.example.movix.mobile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.movix.LiveTv
import com.example.movix.Movie
import com.example.movix.config.AppMode
import com.example.movix.config.AppModeStore
import com.example.movix.data.LiveTvCatalog
import com.example.movix.data.LiveTvChannel
import com.example.movix.data.Repository
import com.example.movix.history.WatchEntry
import com.example.movix.history.WatchHistory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * État de l'UI mobile, conservé quand on change d'onglet ou qu'on tourne
 * l'écran (catalogue, recherche, TV en direct, historique).
 */
class MobileViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()

    // ── Accueil ──────────────────────────────────────────────────────────
    var mode by mutableStateOf(AppModeStore.current(app))
        private set
    /** null = chargement en cours. */
    var homeRows by mutableStateOf<List<Pair<String, List<Movie>>>?>(null)
        private set
    private var homeJob: Job? = null

    fun loadHome(force: Boolean = false) {
        if (!force && homeRows != null) return
        homeJob?.cancel()
        homeRows = null
        val m = mode
        homeJob = viewModelScope.launch {
            val rows = runCatching { Repository.rows(m) }.getOrDefault(emptyList())
            // distinctBy : les clés des listes Compose doivent être uniques
            homeRows = rows.map { (title, items) -> title to items.map(Movie::fromTmdb).distinctBy(::itemKey) }
        }
    }

    fun switchMode(m: AppMode) {
        if (m == mode) return
        AppModeStore.set(ctx, m)
        mode = m
        loadHome(force = true)
    }

    // ── Recherche ────────────────────────────────────────────────────────
    var query by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<Movie>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        query = q
        searchJob?.cancel()
        if (q.trim().length < 2) {
            searchResults = emptyList()
            searching = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(400) // anti-rebond : on attend que la frappe se calme
            searching = true
            searchResults = Repository.searchAll(q.trim()).mapNotNull(Movie::fromSearch).distinctBy(::itemKey)
            searching = false
        }
    }

    // ── TV en direct ─────────────────────────────────────────────────────
    /** null = chargement en cours. */
    var liveCatalogs by mutableStateOf<List<LiveTvCatalog>?>(null)
        private set
    var selectedCatalog by mutableStateOf<String?>(null)
        private set
    val channels = mutableStateMapOf<String, List<LiveTvChannel>>()

    fun loadLive(force: Boolean = false) {
        if (!force && !liveCatalogs.isNullOrEmpty()) return
        liveCatalogs = null
        viewModelScope.launch {
            val cats = LiveTv.catalogs()
            liveCatalogs = cats
            cats.firstOrNull()?.let { selectCatalog(it.id) }
        }
    }

    fun selectCatalog(id: String) {
        selectedCatalog = id
        if (channels.containsKey(id)) return
        viewModelScope.launch { channels[id] = LiveTv.channels(id) }
    }

    // ── Historique ───────────────────────────────────────────────────────
    var history by mutableStateOf<List<WatchEntry>>(emptyList())
        private set

    fun refreshHistory() {
        history = WatchHistory.all(ctx)
    }

    fun clearHistory() {
        WatchHistory.clear(ctx)
        refreshHistory()
    }
}

private fun itemKey(m: Movie) = "${m.isTv}-${m.tmdbId}"
