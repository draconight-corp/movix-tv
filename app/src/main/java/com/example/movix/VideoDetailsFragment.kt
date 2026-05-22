package com.example.movix

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
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

    private var detailsBackground: DetailsSupportFragmentBackgroundController? = null
    private lateinit var presenterSelector: ClassPresenterSelector
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private var movie: Movie? = null
    private var tmdbItem: TmdbItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            initialize()
        } catch (t: Throwable) {
            Log.e(TAG, "Detail fragment failed to initialize", t)
            MovixApp.writeCrashLog(requireContext().applicationContext, Thread.currentThread(), t)
            Toast.makeText(
                requireContext(),
                "Erreur d'ouverture : ${t.javaClass.simpleName} — ${t.message}",
                Toast.LENGTH_LONG
            ).show()
            requireActivity().finish()
        }
    }

    private fun initialize() {
        @Suppress("DEPRECATION")
        val selected = requireActivity().intent.getSerializableExtra(DetailsActivity.MOVIE) as? Movie
        if (selected == null) {
            Toast.makeText(requireContext(), "Film introuvable", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
            return
        }
        movie = selected

        detailsBackground = DetailsSupportFragmentBackgroundController(this)

        presenterSelector = ClassPresenterSelector()
        rowsAdapter = ArrayObjectAdapter(presenterSelector)

        setupDetailsOverviewRow(selected)
        setupDetailsOverviewRowPresenter()
        adapter = rowsAdapter

        initializeBackground(selected)
        fetchTmdbDetails(selected)
    }

    private fun initializeBackground(m: Movie) {
        val ctrl = detailsBackground ?: return
        ctrl.enableParallax()
        val url = m.backgroundImageUrl ?: return
        if (url.isBlank()) return
        if (!isAdded) return
        Glide.with(this)
            .asBitmap()
            .centerCrop()
            .error(R.drawable.default_background)
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    ctrl.coverBitmap = resource
                    if (::rowsAdapter.isInitialized) {
                        rowsAdapter.notifyArrayItemRangeChanged(0, rowsAdapter.size())
                    }
                }
                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private fun setupDetailsOverviewRow(m: Movie) {
        val row = DetailsOverviewRow(m)
        row.imageDrawable = ContextCompat.getDrawable(requireActivity(), R.drawable.default_background)

        val width = dpToPx(requireActivity(), DETAIL_THUMB_WIDTH)
        val height = dpToPx(requireActivity(), DETAIL_THUMB_HEIGHT)
        val cardImageUrl = m.cardImageUrl
        if (!cardImageUrl.isNullOrBlank() && isAdded) {
            Glide.with(this)
                .load(cardImageUrl)
                .centerCrop()
                .error(R.drawable.default_background)
                .into(object : CustomTarget<Drawable>(width, height) {
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                        row.imageDrawable = resource
                        if (::rowsAdapter.isInitialized) {
                            rowsAdapter.notifyArrayItemRangeChanged(0, rowsAdapter.size())
                        }
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }

        val actionAdapter = ArrayObjectAdapter()
        actionAdapter.add(Action(ACTION_WATCH, getString(R.string.watch_now)))
        row.actionsAdapter = actionAdapter

        rowsAdapter.add(row)
    }

    private fun setupDetailsOverviewRowPresenter() {
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter())
        detailsPresenter.backgroundColor =
            ContextCompat.getColor(requireActivity(), R.color.movix_dark)
        detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->
            if (action.id == ACTION_WATCH) onWatchClicked()
        }
        presenterSelector.addClassPresenter(DetailsOverviewRow::class.java, detailsPresenter)
    }

    private fun fetchTmdbDetails(m: Movie) {
        lifecycleScope.launch {
            val item = runCatching {
                withContext(Dispatchers.IO) {
                    Repository.details(TmdbItem(
                        id = m.tmdbId,
                        name = if (m.isTv) m.title else null,
                        title = if (!m.isTv) m.title else null,
                        mediaType = if (m.isTv) "tv" else "movie"
                    ))
                }
            }.getOrNull() ?: return@launch
            tmdbItem = item
            if (!item.overview.isNullOrBlank() && m.description.isNullOrBlank()) {
                m.description = item.overview
                if (::rowsAdapter.isInitialized) {
                    rowsAdapter.notifyArrayItemRangeChanged(0, rowsAdapter.size())
                }
            }
        }
    }

    private fun onWatchClicked() {
        val m = movie ?: return
        if (m.isTv) chooseSeasonAndEpisode(m) else resolveAndPlay(m, null, null)
    }

    private fun chooseSeasonAndEpisode(m: Movie) {
        lifecycleScope.launch {
            val item = tmdbItem ?: runCatching {
                withContext(Dispatchers.IO) {
                    Repository.details(TmdbItem(id = m.tmdbId, mediaType = "tv"))
                }
            }.getOrNull() ?: return@launch
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
                .setItems(labels) { _, idx -> chooseEpisode(m, seasons[idx].seasonNumber) }
                .show()
        }
    }

    private fun chooseEpisode(m: Movie, season: Int) {
        lifecycleScope.launch {
            val seasonDetail = runCatching {
                withContext(Dispatchers.IO) { Repository.seasonDetails(m.tmdbId, season) }
            }.getOrNull()
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
                    resolveAndPlay(m, season, episodes[idx].episodeNumber)
                }
                .show()
        }
    }

    private fun resolveAndPlay(m: Movie, season: Int?, episode: Int?) {
        Toast.makeText(requireContext(), R.string.loading, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val sources = runCatching {
                withContext(Dispatchers.IO) {
                    Repository.resolveSources(m.tmdbId, m.isTv, season, episode)
                }
            }.getOrNull()
            val links = collectLinks(sources, season, episode)
            if (links.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_sources, Toast.LENGTH_LONG).show()
                return@launch
            }
            if (links.size == 1) {
                launchPlayer(m, links[0])
                return@launch
            }
            val labels = links.map { it.displayName() }.toTypedArray()
            AlertDialog.Builder(requireContext(), R.style.MovixDialog)
                .setTitle(R.string.choose_source)
                .setItems(labels) { _, idx -> launchPlayer(m, links[idx]) }
                .show()
        }
    }

    private fun collectLinks(
        sources: MovixSourcesResponse?,
        season: Int?,
        episode: Int?
    ): List<MovixLink> {
        if (sources == null) return emptyList()
        val top = (sources.links ?: emptyList()) + (sources.players ?: emptyList())
        if (season == null || episode == null) {
            return top.filter { !it.bestUrl().isNullOrBlank() }
        }
        val seasonNode = sources.seasons?.firstOrNull { it.number == season }
        val episodeNode = seasonNode?.episodes?.firstOrNull { it.number == episode }
        val sub = episodeNode?.allLinks().orEmpty()
        return (sub + top).filter { !it.bestUrl().isNullOrBlank() }
    }

    private fun launchPlayer(m: Movie, link: MovixLink) {
        val url = link.bestUrl() ?: return
        val intent = Intent(requireActivity(), PlaybackActivity::class.java).apply {
            putExtra(PlaybackActivity.EXTRA_URL, url)
            putExtra(PlaybackActivity.EXTRA_TITLE, m.title)
            putExtra(PlaybackActivity.EXTRA_DESCRIPTION, m.description)
        }
        startActivity(intent)
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.applicationContext.resources.displayMetrics.density
        return Math.round(dp.toFloat() * density)
    }

    companion object {
        private const val TAG = "VideoDetailsFragment"
        private const val ACTION_WATCH = 1L
        private const val DETAIL_THUMB_WIDTH = 200
        private const val DETAIL_THUMB_HEIGHT = 300
    }
}
