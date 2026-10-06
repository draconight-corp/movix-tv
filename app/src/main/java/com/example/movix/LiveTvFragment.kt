package com.example.movix

import android.graphics.drawable.Drawable
import android.os.Bundle
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
import com.example.movix.data.LiveTvChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Section "TV en direct" (UI TV) — la logique est dans [LiveTv]. */
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
            val catalogs = LiveTv.catalogs()
            if (catalogs.isEmpty()) {
                toast("Impossible de charger la TV en direct. Vérifie la connexion.")
                return@launch
            }
            val presenter = ChannelPresenter()
            // Rangées créées tout de suite dans l'ordre, remplies au fil des réponses
            val rows = catalogs.mapIndexed { i, c ->
                val row = ListRow(HeaderItem(i.toLong(), LiveTv.label(c)), ArrayObjectAdapter(presenter))
                rowsAdapter.add(row)
                c to row
            }
            rows.map { (catalog, row) ->
                async {
                    val channels = LiveTv.channels(catalog.id)
                    if (channels.isEmpty()) rowsAdapter.remove(row)
                    else (row.adapter as ArrayObjectAdapter).addAll(0, channels)
                }
            }.awaitAll()
        }
    }

    private fun openChannel(channel: LiveTvChannel) {
        if (opening) return
        opening = true
        toast("Chargement de ${channel.name}…")
        lifecycleScope.launch {
            try {
                when (val r = LiveTv.open(requireContext(), channel)) {
                    is LiveTv.Opening.Ready -> startActivity(r.intent)
                    is LiveTv.Opening.Failed -> toast(r.message)
                }
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
