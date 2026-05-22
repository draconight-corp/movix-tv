package com.example.movix

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper
import androidx.leanback.widget.OnActionClickedListener
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.movix.data.MovixLink
import com.example.movix.data.MovixSourcesResponse
import com.example.movix.data.Repository
import com.example.movix.data.TmdbItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoDetailsFragment : DetailsSupportFragment() {

    private lateinit var detailsBackground: DetailsSupportFragmentBackgroundController
    private lateinit var presenterSelector: ClassPresenterSelector
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var movie: Movie
    private var tmdbItem: TmdbItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        detailsBackground = DetailsSupportFragmentBackgroundController(this)

        val selected = requireActivity().intent.getSerializableExtra(DetailsActivity.MOVIE) as? Movie
        if (selected == null) {
            startActivity(Intent(requireActivity(), MainActivity::class.java))
            requireActivity().finish()
            return
        }
        movie = selected

        presenterSelector = ClassPresenterSelector()
        rowsAdapter = ArrayObjectAdapter(presenterSelector)

        setupDetailsOverviewRow()
        setupDetailsOverviewRowPresenter()
        adapter = rowsAdapter

        initializeBackground()
        fetchTmdbDetails()
    }

    private fun initializeBackground() {
        detailsBackground.enableParallax()
        if (!movie.backgroundImageUrl.isNullOrBlank()) {
            Glide.with(this)
                .asBitmap()
                .centerCrop()
                .error(R.drawable.default_background)
                .load(movie.backgroundImageUrl)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        detailsBackground.coverBitmap = resource
                        rowsAdapter.notifyArrayItemRangeChanged(0, rowsAdapter.size())
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }
    }

    private fun setupDetailsOverviewRow() {
        val row = DetailsOverviewRow(movie)
        row.imageDrawable = ContextCompat.getDrawable(requireActivity(), R.drawable.default_background)

        val width = dpToPx(requireActivity(), DETAIL_THUMB_WIDTH)
        val height = dpToPx(requireActivity(), DETAIL_THUMB_HEIGHT)
        Glide.with(this)
            .load(movie.cardImageUrl)
            .centerCrop()
            .error(R.drawable.default_background)
            .into(object : CustomTarget<Drawable>(width, height) {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    row.imageDrawable = resource
                    rowsAdapter.notifyArrayItemRangeChanged(0, rowsAdapter.size())
                }
                override fun onLoadCleared(placeholder: Drawable?) {}
            })

        val actionAdapter = ArrayObjectAdapter()
        actionAdapter.add(Action(ACTION_WATCH, getString(R.string.watch_now)))
        row.actionsAdapter = actionAdapter

        rowsAdapter.add(row)
    }

    private fun setupDetailsOverviewRowPresenter() {
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter())
        detailsPresenter.backgroundColor =
            ContextCompat.getColor(requireActivity(), R.color.movix_dark)

        val sharedElementHelper = FullWidthDetailsOverviewSharedElementHelper()
        sharedElementHelper.setSharedElementEnterTransition(
            activity, DetailsActivity.SHARED_ELEMENT_NAME
        )
        detailsPresenter.setListener(sharedElementHelper)
        detailsPresenter.isParticipatingEntranceTransition = true

        detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->
            if (action.id == ACTION_WATCH) onWatchClicked()
        }
        presenterSelector.addClassPresenter(DetailsOverviewRow::class.java, detailsPresenter)
    }

    private fun fetchTmdbDetails() {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) {
                Repository.details(TmdbItem(
                    id = movie.tmdbId,
                    name = if (movie.isTv) movie.title else null,
                    title = if (!movie.isTv) movie.title else null,
                    mediaType = if (movie.isTv) "tv" else "movie"
                ))
            }
            tmdbItem = item
            // Met à jour la description si plus riche
            if (!item.overview.isNullOrBlank() && movie.description.isNullOrBlank()) {
                movie.description = item.overview
                rowsAdapter.notifyArrayItemRangeChanged(0, rowsAdapter.size())
            }
        }
    }

    private fun onWatchClicked() {
        if (movie.isTv) {
            chooseSeasonAndEpisode()
        } else {
            resolveAndPlay(season = null, episode = null)
        }
    }

    private fun chooseSeasonAndEpisode() {
        lifecycleScope.launch {
            val item = tmdbItem ?: withContext(Dispatchers.IO) {
                Repository.details(TmdbItem(id = movie.tmdbId, mediaType = "tv"))
            }
            val seasons = item.seasons?.filter { it.seasonNumber > 0 }.orEmpty()
            if (seasons.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_sources, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = seasons.map {
                it.name ?: getString(R.string.season_n, it.seasonNumber)
            }.toTypedArray()
            AlertDialog.Builder(requireContext(), R.style.MovixDialog)
                .setTitle(R.string.choose_season)
                .setItems(labels) { _, idx ->
                    chooseEpisode(seasons[idx].seasonNumber)
                }
                .show()
        }
    }

    private fun chooseEpisode(season: Int) {
        lifecycleScope.launch {
            val seasonDetail = withContext(Dispatchers.IO) {
                Repository.seasonDetails(movie.tmdbId, season)
            }
            val episodes = seasonDetail?.episodes.orEmpty()
            if (episodes.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_sources, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = episodes.map {
                "E${it.episodeNumber.toString().padStart(2, '0')} • ${it.name ?: ""}"
            }.toTypedArray()
            AlertDialog.Builder(requireContext(), R.style.MovixDialog)
                .setTitle(R.string.choose_episode)
                .setItems(labels) { _, idx ->
                    resolveAndPlay(season = season, episode = episodes[idx].episodeNumber)
                }
                .show()
        }
    }

    private fun resolveAndPlay(season: Int?, episode: Int?) {
        Toast.makeText(requireContext(), R.string.loading, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val sources = withContext(Dispatchers.IO) {
                Repository.resolveSources(movie.tmdbId, movie.isTv, season, episode)
            }
            val links = collectLinks(sources, season, episode)
            if (links.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_sources, Toast.LENGTH_LONG).show()
                return@launch
            }
            if (links.size == 1) {
                launchPlayer(links[0])
                return@launch
            }
            val labels = links.map { it.displayName() }.toTypedArray()
            AlertDialog.Builder(requireContext(), R.style.MovixDialog)
                .setTitle(R.string.choose_source)
                .setItems(labels) { _, idx -> launchPlayer(links[idx]) }
                .show()
        }
    }

    private fun collectLinks(
        sources: MovixSourcesResponse?,
        season: Int?,
        episode: Int?
    ): List<MovixLink> {
        if (sources == null) return emptyList()
        // Cas film : links + players à la racine.
        val top = (sources.links ?: emptyList()) + (sources.players ?: emptyList())
        if (season == null || episode == null) {
            return top.filter { !it.bestUrl().isNullOrBlank() }
        }
        // Cas série : chercher la saison/épisode demandée
        val seasonNode = sources.seasons?.firstOrNull { it.number == season }
        val episodeNode = seasonNode?.episodes?.firstOrNull { it.number == episode }
        val sub = episodeNode?.allLinks().orEmpty()
        return (sub + top).filter { !it.bestUrl().isNullOrBlank() }
    }

    private fun launchPlayer(link: MovixLink) {
        val url = link.bestUrl() ?: return
        val intent = Intent(requireActivity(), PlaybackActivity::class.java).apply {
            putExtra(PlaybackActivity.EXTRA_URL, url)
            putExtra(PlaybackActivity.EXTRA_TITLE, movie.title)
            putExtra(PlaybackActivity.EXTRA_DESCRIPTION, movie.description)
        }
        startActivity(intent)
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.applicationContext.resources.displayMetrics.density
        return Math.round(dp.toFloat() * density)
    }

    companion object {
        private const val ACTION_WATCH = 1L
        private const val DETAIL_THUMB_WIDTH = 200
        private const val DETAIL_THUMB_HEIGHT = 300
    }
}
