package com.example.movix

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.movix.data.ApiClient
import com.example.movix.data.LiveTvCatalog
import com.example.movix.data.LiveTvChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Section "TV en direct" — reprend les catalogues de https://movix.luxe/live-tv.
 *
 * Seuls les catalogues gratuits et lisibles sont gardés :
 *  - vavoo_*     : chaînes par pays, flux HLS directs → lecteur natif (aucune pub)
 *  - northlive_* : chaînes françaises par thème, page lecteur web
 *  - streamed_all: matchs en direct, page lecteur web
 * Les pages web passent par WebPlaybackActivity (verrou anti-pop-up).
 */
class LiveTvFragment : BrowseSupportFragment() {

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private var opening = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        title = "TV en direct"
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireActivity(), R.color.fastlane_background)
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is LiveTvChannel) openChannel(item)
        }
        loadCatalogs()
    }

    private fun loadCatalogs() {
        lifecycleScope.launch {
            val catalogs = runCatching { ApiClient.movix.liveTvManifest().catalogs.orEmpty() }
                .onFailure { Log.w(TAG, "Manifest live TV KO", it) }
                .getOrDefault(emptyList())
                .filter(::isSupported)
                .sortedBy(::priority)
            if (catalogs.isEmpty()) {
                toast("Impossible de charger la TV en direct. Vérifie la connexion.")
                return@launch
            }
            val presenter = ChannelPresenter()
            // Rangées créées tout de suite dans l'ordre, remplies au fil des réponses
            val rows = catalogs.mapIndexed { i, c ->
                val rowAdapter = ArrayObjectAdapter(presenter)
                val row = ListRow(HeaderItem(i.toLong(), label(c)), rowAdapter)
                rowsAdapter.add(row)
                c to row
            }
            rows.map { (catalog, row) ->
                async {
                    val channels = runCatching {
                        ApiClient.movix.liveTvCatalog(catalog.id).metas.orEmpty()
                    }.onFailure { Log.w(TAG, "Catalogue ${catalog.id} KO", it) }
                        .getOrDefault(emptyList())
                        .filter { it.id.isNotBlank() && !it.name.isNullOrBlank() }
                    if (channels.isEmpty()) rowsAdapter.remove(row)
                    else (row.adapter as ArrayObjectAdapter).addAll(0, channels)
                }
            }.awaitAll()
        }
    }

    private fun isSupported(c: LiveTvCatalog): Boolean =
        c.id.startsWith("vavoo_") || c.id.startsWith("northlive_") || c.id == "streamed_all"

    /** France d'abord, puis les matchs, puis les autres pays. */
    private fun priority(c: LiveTvCatalog): Int = when {
        c.id == "vavoo_france" -> 0
        c.id == "vavoo_france-sport" -> 1
        c.id.startsWith("northlive_") -> 2
        c.id == "streamed_all" -> 3
        else -> 4
    }

    private fun label(c: LiveTvCatalog): String = when {
        c.id == "vavoo_france" -> "France — toutes les chaînes"
        c.id == "vavoo_france-sport" -> "France — Sport"
        c.id.startsWith("northlive_") -> "France — ${c.name}"
        c.id == "streamed_all" -> "⚽ Matchs en direct"
        else -> c.name ?: c.id
    }

    private fun openChannel(channel: LiveTvChannel) {
        if (opening) return
        opening = true
        toast("Chargement de ${channel.name}…")
        lifecycleScope.launch {
            try {
                val streams = runCatching { ApiClient.movix.liveTvStreams(channel.id).streams.orEmpty() }
                    .onFailure { Log.w(TAG, "Streams ${channel.id} KO", it) }
                    .getOrDefault(emptyList())
                    .filter { !it.url.isNullOrBlank() }
                val direct = streams.filter { it.isDirect }
                val chosen = direct.ifEmpty { streams }
                if (chosen.isEmpty()) {
                    toast("Aucun flux disponible pour le moment (match pas encore commencé ?)")
                    return@launch
                }
                val urls = ArrayList(chosen.map { it.url!! })
                val labels = ArrayList(chosen.mapIndexed { i, s -> s.title ?: "Source ${i + 1}" })
                val intent = if (direct.isNotEmpty()) {
                    Intent(requireActivity(), PlaybackActivity::class.java).apply {
                        putExtra(PlaybackActivity.EXTRA_URL, urls[0])
                        putExtra(PlaybackActivity.EXTRA_TITLE, channel.name)
                        putStringArrayListExtra(PlaybackActivity.EXTRA_URLS, urls)
                        putStringArrayListExtra(PlaybackActivity.EXTRA_LABELS, labels)
                        putExtra(PlaybackActivity.EXTRA_INDEX, 0)
                    }
                } else {
                    Intent(requireActivity(), WebPlaybackActivity::class.java).apply {
                        putExtra(WebPlaybackActivity.EXTRA_URL, urls[0])
                        putExtra(WebPlaybackActivity.EXTRA_TITLE, channel.name)
                        putStringArrayListExtra(WebPlaybackActivity.EXTRA_URLS, urls)
                        putStringArrayListExtra(WebPlaybackActivity.EXTRA_LABELS, labels)
                        putExtra(WebPlaybackActivity.EXTRA_INDEX, 0)
                    }
                }
                startActivity(intent)
            } finally {
                opening = false
            }
        }
    }

    private fun toast(msg: String) {
        if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    /** Carte paysage (logo de chaîne / affiche du match). */
    private class ChannelPresenter : Presenter() {
        private var defaultImage: Drawable? = null
        private val timeFormat = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)

        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            defaultImage = ContextCompat.getDrawable(parent.context, R.drawable.movie)
            val bg = ContextCompat.getColor(parent.context, R.color.default_background)
            val selected = ContextCompat.getColor(parent.context, R.color.selected_background)
            val card = object : ImageCardView(parent.context) {
                override fun setSelected(isSelected: Boolean) {
                    val c = if (isSelected) selected else bg
                    setBackgroundColor(c)
                    setInfoAreaBackgroundColor(c)
                    super.setSelected(isSelected)
                }
            }
            card.isFocusable = true
            card.isFocusableInTouchMode = true
            card.setBackgroundColor(bg)
            card.setInfoAreaBackgroundColor(bg)
            card.setMainImageScaleType(ImageView.ScaleType.FIT_CENTER)
            return ViewHolder(card)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            val ch = item as LiveTvChannel
            val card = viewHolder.view as ImageCardView
            card.titleText = ch.name
            card.contentText = when {
                ch.isLive == true -> "● EN DIRECT"
                ch.timestamp != null -> timeFormat.format(Date(ch.timestamp))
                else -> ch.description.orEmpty()
            }
            card.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
            val image = ch.logo ?: ch.poster
            if (!image.isNullOrBlank()) {
                Glide.with(card.context).load(image)
                    .fitCenter()
                    .placeholder(defaultImage)
                    .error(defaultImage)
                    .into(card.mainImageView!!)
            } else {
                card.mainImageView!!.setImageDrawable(defaultImage)
            }
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {
            (viewHolder.view as ImageCardView).mainImage = null
        }
    }

    companion object {
        private const val TAG = "LiveTvFragment"
        private const val CARD_WIDTH = 320
        private const val CARD_HEIGHT = 180
    }
}
