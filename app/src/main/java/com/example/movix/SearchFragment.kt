package com.example.movix

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.example.movix.data.ApiConfig
import com.example.movix.data.MovixSearchItem
import com.example.movix.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)
        setOnItemViewClickedListener(ClickListener())
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String?): Boolean {
        scheduleSearch(newQuery)
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        scheduleSearch(query, immediate = true)
        return true
    }

    private fun scheduleSearch(query: String?, immediate: Boolean = false) {
        searchJob?.cancel()
        if (query.isNullOrBlank() || query.length < 2) {
            rowsAdapter.clear()
            return
        }
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            if (!immediate) delay(350)
            showStatus("Recherche en cours pour \"$query\"…")
            // Recherche unifiée : films, séries, animés et dessins animés en une
            // seule passe — plus besoin de switcher de catégorie.
            val results = withContext(Dispatchers.IO) {
                Repository.searchAll(query)
            }
            displayResults(query, results)
        }
    }

    private fun showStatus(message: String) {
        rowsAdapter.clear()
        val header = HeaderItem(STATUS_HEADER_ID, message)
        val statusAdapter = ArrayObjectAdapter(StatusPresenter())
        statusAdapter.add(message)
        rowsAdapter.add(ListRow(header, statusAdapter))
    }

    private fun displayResults(query: String, items: List<MovixSearchItem>) {
        rowsAdapter.clear()
        val movies = items.mapNotNull { toMovie(it) }
        if (movies.isEmpty()) {
            showStatus("Aucun résultat pour \"$query\". Vérifie l'orthographe ou essaie un mot clé différent.")
            return
        }
        val cardPresenter = CardPresenter()
        val rowAdapter = ArrayObjectAdapter(cardPresenter)
        movies.forEach(rowAdapter::add)
        val header = HeaderItem(0, "Résultats pour \"$query\" — ${movies.size}")
        rowsAdapter.add(ListRow(header, rowAdapter))
    }

    private fun toMovie(item: MovixSearchItem): Movie? {
        val tmdb = item.resolvedTmdbId() ?: return null
        return Movie(
            id = tmdb,
            tmdbId = tmdb,
            isTv = item.isTv(),
            title = item.displayTitle(),
            description = item.overview,
            backgroundImageUrl = ApiConfig.backdropUrl(item.posterPath),
            cardImageUrl = item.posterUrl(),
            year = (item.releaseDate ?: item.firstAirDate)?.take(4),
            rating = item.voteAverage
        )
    }

    private inner class ClickListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            if (item is Movie) {
                val intent = Intent(requireActivity(), DetailsActivity::class.java)
                intent.putExtra(DetailsActivity.MOVIE, item)
                startActivity(intent)
            }
        }
    }

    /**
     * Présenter minimaliste pour afficher un message d'état (recherche en cours,
     * pas de résultat) dans une rangée. Évite le sentiment de "bug" quand
     * l'utilisateur tape mais que rien ne semble se passer.
     */
    private inner class StatusPresenter : Presenter() {
        override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
            val view = android.widget.TextView(parent.context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(STATUS_W, STATUS_H)
                setPadding(32, 32, 32, 32)
                gravity = android.view.Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0x33000000)
                isFocusable = true
                isFocusableInTouchMode = true
                textSize = 16f
            }
            return ViewHolder(view)
        }
        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            (viewHolder.view as android.widget.TextView).text = item as? String ?: ""
        }
        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }

    companion object {
        private const val STATUS_HEADER_ID = 999L
        private const val STATUS_W = 900
        private const val STATUS_H = 200
    }
}
