package com.example.movix

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter

class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    override fun onBindDescription(viewHolder: ViewHolder, item: Any) {
        val movie = item as Movie
        viewHolder.title.text = movie.title
        viewHolder.subtitle.text = listOfNotNull(
            movie.year,
            movie.rating?.let { String.format("★ %.1f", it) },
            if (movie.isTv) "Série" else "Film"
        ).joinToString("  •  ")
        viewHolder.body.text = movie.description
    }
}
