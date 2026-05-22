package com.example.movix

import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
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
        showLastCrashIfAny()
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
        title = "${getString(R.string.browse_title)}  •  v${BuildConfig.VERSION_NAME}"
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
        lifecycleScope.launch {
            val rows = runCatching { Repository.rows() }.getOrDefault(emptyList())
            val cardPresenter = CardPresenter()
            rowsAdapter.clear()
            rows.forEachIndexed { index, (title, items) ->
                val rowAdapter = ArrayObjectAdapter(cardPresenter)
                items.forEach { rowAdapter.add(Movie.fromTmdb(it)) }
                val header = HeaderItem(index.toLong(), title)
                rowsAdapter.add(ListRow(header, rowAdapter))
            }

            // Row "À propos" en fin de liste — la version apparaît dans le drawer gauche
            val infoHeader = HeaderItem(
                rows.size.toLong(),
                "Movix TV  •  v${BuildConfig.VERSION_NAME}"
            )
            val infoAdapter = ArrayObjectAdapter(InfoItemPresenter())
            infoAdapter.add("Version installée : ${BuildConfig.VERSION_NAME}")
            infoAdapter.add("Vérifier les mises à jour")
            infoAdapter.add("Voir le dernier crash")
            rowsAdapter.add(ListRow(infoHeader, infoAdapter))

            if (rows.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Impossible de charger le catalogue. Vérifie la connexion.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showLastCrashIfAny() {
        val crash = MovixApp.consumeLastCrash(requireContext().applicationContext) ?: return
        val preview = crash.lines().take(20).joinToString("\n")
        AlertDialog.Builder(requireContext(), R.style.MovixDialog)
            .setTitle("Plantage précédent détecté")
            .setMessage(preview)
            .setPositiveButton("OK", null)
            .show()
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
            try {
                when (item) {
                    is Movie -> openDetails(item)
                    is String -> handleInfoAction(item)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Click failed", t)
                Toast.makeText(
                    requireContext(),
                    "Erreur: ${t.javaClass.simpleName} — ${t.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openDetails(movie: Movie) {
        val intent = Intent(requireActivity(), DetailsActivity::class.java)
        intent.putExtra(DetailsActivity.MOVIE, movie)
        startActivity(intent)
    }

    private fun handleInfoAction(item: String) {
        when {
            item.startsWith("Vérifier") -> {
                Toast.makeText(requireContext(), "Recherche d'une mise à jour…", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    com.example.movix.update.UpdateManager.checkAndPrompt(requireActivity())
                }
            }
            item.startsWith("Voir") -> {
                val crash = MovixApp.consumeLastCrash(requireContext().applicationContext)
                AlertDialog.Builder(requireContext(), R.style.MovixDialog)
                    .setTitle("Dernier crash")
                    .setMessage(crash?.lines()?.take(30)?.joinToString("\n") ?: "Aucun crash enregistré.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            else -> Toast.makeText(requireContext(), item, Toast.LENGTH_SHORT).show()
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
        if (!isAdded) return
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

    private inner class InfoItemPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val view = TextView(parent.context)
            view.layoutParams = ViewGroup.LayoutParams(INFO_W, INFO_H)
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.gravity = Gravity.CENTER
            view.setPadding(24, 24, 24, 24)
            view.setBackgroundColor(ContextCompat.getColor(parent.context, R.color.default_background))
            view.setTextColor(0xFFFFFFFF.toInt())
            return ViewHolder(view)
        }
        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            (viewHolder.view as TextView).text = item as String
        }
        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }

    companion object {
        private const val TAG = "MainFragment"
        private const val BACKGROUND_UPDATE_DELAY = 300
        private const val INFO_W = 380
        private const val INFO_H = 140
    }
}
