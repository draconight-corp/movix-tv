package com.example.movix.config

import android.content.Context

/**
 * Mode global de l'app : catalogue Films & Séries "classique" ou catalogue
 * Animés (rangées TMDB filtrées par animation + origin_country=JP, recherche
 * filtrée client-side aux titres japonais animés).
 *
 * Persisté dans les SharedPreferences de MovixConfig pour rester stable
 * entre relances. Par défaut : FILMS_SERIES.
 */
enum class AppMode { FILMS_SERIES, ANIME }

object AppModeStore {
    private const val PREFS = "movix_config"
    private const val KEY_MODE = "app_mode"

    fun current(ctx: Context): AppMode {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, AppMode.FILMS_SERIES.name) ?: AppMode.FILMS_SERIES.name
        return runCatching { AppMode.valueOf(raw) }.getOrDefault(AppMode.FILMS_SERIES)
    }

    fun set(ctx: Context, mode: AppMode) {
        ctx.getSharedPreferences(PREFS, 0).edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    fun toggle(ctx: Context): AppMode {
        val next = if (current(ctx) == AppMode.ANIME) AppMode.FILMS_SERIES else AppMode.ANIME
        set(ctx, next)
        return next
    }

    fun label(mode: AppMode): String = when (mode) {
        AppMode.FILMS_SERIES -> "Films & Séries"
        AppMode.ANIME        -> "Animés & Dessins animés"
    }
}
