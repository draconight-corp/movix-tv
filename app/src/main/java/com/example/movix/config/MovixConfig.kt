package com.example.movix.config

import android.content.Context

/**
 * Persiste le domaine API courant et permet à l'utilisateur de l'overrider manuellement.
 *
 * Ordre de priorité pour la résolution :
 *   1. Override manuel (saisi dans les paramètres)
 *   2. Dernier hôte résolu automatiquement (TTL 6h)
 *   3. Fallback hardcodé
 */
object MovixConfig {

    const val DEFAULT_API_HOST = "api.movix.golf"
    const val DEFAULT_SITE_HOST = "movix.golf"
    private const val PREFS = "movix_config"
    private const val KEY_OVERRIDE = "api_host_override"
    private const val KEY_RESOLVED = "api_host_resolved"
    private const val KEY_RESOLVED_AT = "api_host_resolved_at"
    private const val TTL_MS = 6L * 60 * 60 * 1000

    fun currentApiHost(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.getString(KEY_OVERRIDE, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val resolved = p.getString(KEY_RESOLVED, null)
        val resolvedAt = p.getLong(KEY_RESOLVED_AT, 0L)
        if (!resolved.isNullOrBlank() && System.currentTimeMillis() - resolvedAt < TTL_MS) {
            return resolved
        }
        return DEFAULT_API_HOST
    }

    fun currentSiteHost(ctx: Context): String {
        val api = currentApiHost(ctx)
        return if (api.startsWith("api.")) api.removePrefix("api.") else DEFAULT_SITE_HOST
    }

    fun manualOverride(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, 0).getString(KEY_OVERRIDE, null)

    fun setManualOverride(ctx: Context, host: String?) {
        val cleaned = host?.trim()
            ?.removePrefix("https://")?.removePrefix("http://")
            ?.removeSuffix("/")
        ctx.getSharedPreferences(PREFS, 0).edit().apply {
            if (cleaned.isNullOrBlank()) remove(KEY_OVERRIDE) else putString(KEY_OVERRIDE, cleaned)
            apply()
        }
    }

    fun lastResolved(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, 0).getString(KEY_RESOLVED, null)

    fun lastResolvedAt(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, 0).getLong(KEY_RESOLVED_AT, 0L)

    fun setResolved(ctx: Context, host: String) {
        ctx.getSharedPreferences(PREFS, 0).edit()
            .putString(KEY_RESOLVED, host)
            .putLong(KEY_RESOLVED_AT, System.currentTimeMillis())
            .apply()
    }

    private const val KEY_FILTER_DEAD = "filter_dead_links"
    fun isDeadLinkFilterEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, 0).getBoolean(KEY_FILTER_DEAD, true)
    fun setDeadLinkFilterEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY_FILTER_DEAD, enabled).apply()
    }
}
