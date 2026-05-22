package com.example.movix

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.movix.data.Repository
import com.example.movix.data.TmdbItem
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class MainFragment : BrowseSupportFragment() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var backgroundManager: BackgroundManager
    private var defaultBackground: Drawable? = null
    private lateinit var metrics: DisplayMetrics
    private var backgroundTimer: Timer? = null
    private var backgroundUri: String? = null

    private lateinit var rowsAdapter: ArrayObjectAdapter

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        prepareBackgroundManager()
        setupUIElements()
        setupAdapter()
        setupEventListeners()
        loadRows()
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundTimer?.cancel()
    }

    private fun prepareBackgroundManager() {
        backgroundManager = BackgroundManager.getInstance(activity)
        backgroundManager.attach(requireActivity().window)
        defaultBackground = ContextCompat.getDrawable(requireActivity(), R.drawable.default_background)
        metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        requireActivity().windowManager.defaultDisplay.getMetrics(metrics)
    }

    private fun setupUIElements() {
        title = getString(R.string.browse_title)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireActivity(), R.color.fastlane_background)
        searchAffordanceColor = ContextCompat.getColor(requireActivity(), R.color.search_opaque)
    }

    private fun setupAdapter() {
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter
    }

    private fun loadRows() {
        viewLifecycleOwner.lifecycleScope.launch {
            val rows = Repository.rows()
            val cardPresenter = CardPresenter()
            rowsAdapter.clear()
            rows.forEachIndexed { index, (title, items) ->
                val rowAdapter = ArrayObjectAdapter(cardPresenter)
                items.forEach { rowAdapter.add(Movie.fromTmdb(it)) }
                val header = HeaderItem(index.toLong(), title)
                rowsAdapter.add(ListRow(header, rowAdapter))
            }
            if (rows.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Impossible de charger le catalogue. Vérifie la connexion.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupEventListeners() {
        setOnSearchClickedListener {
            startActivity(Intent(requireActivity(), SearchActivity::class.java))
        }
        onItemViewClickedListener = ItemViewClickedListener()
        onItemViewSelectedListener = ItemViewSelectedListener()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
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

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?, item: Any?,
            rowViewHolder: RowPresenter.ViewHolder, row: Row
        ) {
            if (item is Movie) {
                backgroundUri = item.backgroundImageUrl
                startBackgroundTimer()
            }
        }
    }

    private fun updateBackground(uri: String?) {
        if (uri.isNullOrBlank()) return
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        Glide.with(this)
            .load(uri)
            .centerCrop()
            .error(defaultBackground)
            .into(object : CustomTarget<Drawable>(width, height) {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    backgroundManager.drawable = resource
                }
                override fun onLoadCleared(placeholder: Drawable?) {}
            })
        backgroundTimer?.cancel()
    }

    private fun startBackgroundTimer() {
        backgroundTimer?.cancel()
        backgroundTimer = Timer()
        backgroundTimer?.schedule(object : TimerTask() {
            override fun run() {
                mainHandler.post { updateBackground(backgroundUri) }
            }
        }, BACKGROUND_UPDATE_DELAY.toLong())
    }

    companion object {
        private const val BACKGROUND_UPDATE_DELAY = 300
    }
}
