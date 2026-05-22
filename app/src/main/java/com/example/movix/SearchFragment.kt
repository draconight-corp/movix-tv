package com.example.movix

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityOptionsCompat
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
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
            val results = withContext(Dispatchers.IO) { Repository.searchMovix(query) }
            displayResults(query, results)
        }
    }

    private fun displayResults(query: String, items: List<MovixSearchItem>) {
        rowsAdapter.clear()
        val movies = items.mapNotNull { toMovie(it) }
        if (movies.isEmpty()) return
        val cardPresenter = CardPresenter()
        val rowAdapter = ArrayObjectAdapter(cardPresenter)
        movies.forEach(rowAdapter::add)
        val header = HeaderItem(0, "Résultats pour \"$query\"")
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
                val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(),
                    (itemViewHolder.view as ImageCardView).mainImageView!!,
                    DetailsActivity.SHARED_ELEMENT_NAME
                ).toBundle()
                startActivity(intent, bundle)
            }
        }
    }
}
