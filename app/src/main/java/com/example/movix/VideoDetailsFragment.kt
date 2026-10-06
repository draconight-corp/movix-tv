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
import com.example.movix.data.Repository
import com.example.movix.data.TmdbItem
import com.example.movix.history.WatchHistory
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
        val history = WatchHistory.forMovie(requireContext().applicationContext, m.tmdbId, m.isTv)
        if (m.isTv && history?.lastSeason != null && history.lastEpisode != null) {
            val tag = "S${history.lastSeason.toString().padStart(2, '0')}E${history.lastEpisode.toString().padStart(2, '0')}"
            actionAdapter.add(Action(ACTION_RESUME, "Reprendre $tag"))
            actionAdapter.add(Action(ACTION_WATCH, "Choisir un autre épisode"))
        } else {
            actionAdapter.add(Action(ACTION_WATCH, getString(R.string.watch_now)))
        }
        row.actionsAdapter = actionAdapter

        rowsAdapter.add(row)
    }

    private fun setupDetailsOverviewRowPresenter() {
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter())
        detailsPresenter.backgroundColor =
            ContextCompat.getColor(requireActivity(), R.color.movix_dark)
        detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->
            when (action.id) {
                ACTION_WATCH -> onWatchClicked()
                ACTION_RESUME -> onResumeClicked()
            }
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

    private fun onResumeClicked() {
        val m = movie ?: return
        val h = WatchHistory.forMovie(requireContext().applicationContext, m.tmdbId, m.isTv) ?: return
        val s = h.lastSeason; val e = h.lastEpisode
        if (s != null && e != null) resolveAndPlay(m, s, e) else onWatchClicked()
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
            val lastWatched = WatchHistory.forMovie(requireContext().applicationContext, m.tmdbId, true)
            val lastWatchedEp = lastWatched?.takeIf { it.lastSeason == season }?.lastEpisode
            var preselectedIdx = -1
            val labels = episodes.mapIndexed { i, ep ->
                val tag = "E${ep.episodeNumber.toString().padStart(2, '0')}"
                val name = ep.name ?: ""
                if (ep.episodeNumber == lastWatchedEp) {
                    preselectedIdx = i
                    "▶ $tag • $name   (dernier vu)"
                } else {
                    "$tag • $name"
                }
            }.toTypedArray()
            val title = if (lastWatchedEp != null) {
                getString(R.string.choose_episode) + "  •  dernier vu : E${lastWatchedEp.toString().padStart(2, '0')}"
            } else {
                getString(R.string.choose_episode)
            }
            val builder = AlertDialog.Builder(requireContext(), R.style.MovixDialog).setTitle(title)
            if (preselectedIdx >= 0) {
                builder.setSingleChoiceItems(labels, preselectedIdx) { dialog, idx ->
                    dialog.dismiss()
                    resolveAndPlay(m, season, episodes[idx].episodeNumber)
                }
            } else {
                builder.setItems(labels) { _, idx ->
                    resolveAndPlay(m, season, episodes[idx].episodeNumber)
                }
            }
            builder.show()
        }
    }

    private fun resolveAndPlay(m: Movie, season: Int?, episode: Int?) {
        Toast.makeText(requireContext(), R.string.loading, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val links = PlayerLauncher.resolveSources(m, season, episode)
            if (links.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_sources, Toast.LENGTH_LONG).show()
                return@launch
            }

            WatchHistory.record(requireContext().applicationContext, m, season, episode)
            if (links.size == 1) {
                launchPlayer(m, links, 0, season, episode)
                return@launch
            }

            val grouped = links.groupBy { it.category }
            val title = buildString {
                append("Sources (${links.size}) — ${grouped.size} catégorie(s)")
                if (isSpiderNoir(m)) append("\nℹ️ Version noir & blanc dispo dans Seekstreaming 4")
            }
            val flatLabels = links.map(PlayerLauncher::sourceLabel).toTypedArray()
            AlertDialog.Builder(requireContext(), R.style.MovixDialog)
                .setTitle(title)
                .setItems(flatLabels) { _, idx -> launchPlayer(m, links, idx, season, episode) }
                .show()
        }
    }


    private fun launchPlayer(
        m: Movie,
        links: List<MovixLink>,
        selectedIdx: Int,
        season: Int?,
        episode: Int?
    ) {
        if (selectedIdx !in links.indices) return
        val intent = PlayerLauncher.intentFor(requireContext(), m, links, selectedIdx, season, episode)
        startActivity(intent)
    }

    /**
     * Détecte la série Spider-Noir (Prime Video, 2026) — diffusée à la fois
     * en couleur et en noir & blanc. Sur Movix, la version N&B n'est dispo
     * que via la source Seekstreaming 4 ; on l'indique dans le sélecteur.
     * Match tolérant : "spider-noir", "spider noir", "spidernoir".
     */
    private fun isSpiderNoir(m: Movie): Boolean {
        val t = (m.title ?: "").lowercase()
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return t.contains("spider noir") || t.contains("spidernoir")
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.applicationContext.resources.displayMetrics.density
        return Math.round(dp.toFloat() * density)
    }

    companion object {
        private const val TAG = "VideoDetailsFragment"
        private const val ACTION_WATCH = 1L
        private const val ACTION_RESUME = 2L
        private const val DETAIL_THUMB_WIDTH = 200
        private const val DETAIL_THUMB_HEIGHT = 300
    }
}
