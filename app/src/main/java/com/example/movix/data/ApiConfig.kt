package com.example.movix.data

object ApiConfig {
    // Backend public Movix.
    const val MOVIX_BASE_URL = "https://api.movix.tax/"
    const val MOVIX_SITE_ORIGIN = "https://movix.tax"

    // TMDB - utilisé pour le catalogue (popular, trending, détails).
    // Cette clé v3 publique est diffusée largement et est en lecture seule ;
    // si elle expire, remplace-la par la tienne (gratuite sur https://themoviedb.org).
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    const val TMDB_API_KEY = "8265bd1679663a7ea12ac168da84d2e8"
    const val TMDB_IMG_BASE = "https://image.tmdb.org/t/p/"
    const val TMDB_LANGUAGE = "fr-FR"

    fun posterUrl(path: String?, size: String = "w500"): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http")) return path
        return "$TMDB_IMG_BASE$size$path"
    }

    fun backdropUrl(path: String?, size: String = "w1280"): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http")) return path
        return "$TMDB_IMG_BASE$size$path"
    }
}
