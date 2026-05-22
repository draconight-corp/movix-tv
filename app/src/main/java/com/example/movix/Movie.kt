package com.example.movix

import com.example.movix.data.ApiConfig
import com.example.movix.data.TmdbItem
import java.io.Serializable

/**
 * Objet film/série utilisé dans toute l'UI (Leanback impose Serializable pour passer entre activités).
 */
data class Movie(
    var id: Long = 0,
    var tmdbId: Long = 0,
    var isTv: Boolean = false,
    var title: String? = null,
    var description: String? = null,
    var backgroundImageUrl: String? = null,
    var cardImageUrl: String? = null,
    var year: String? = null,
    var rating: Double? = null
) : Serializable {

    val studio: String?
        get() = year

    companion object {
        internal const val serialVersionUID = 727566175075960653L

        fun fromTmdb(item: TmdbItem): Movie = Movie(
            id = item.id,
            tmdbId = item.id,
            isTv = item.isTv,
            title = item.displayTitle,
            description = item.overview,
            backgroundImageUrl = ApiConfig.backdropUrl(item.backdropPath),
            cardImageUrl = ApiConfig.posterUrl(item.posterPath),
            year = item.displayDate?.take(4),
            rating = item.voteAverage
        )
    }
}
