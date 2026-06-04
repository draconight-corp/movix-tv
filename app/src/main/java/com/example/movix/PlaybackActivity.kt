package com.example.movix

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.movix.history.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackActivity : FragmentActivity() {

    private var urls: MutableList<String> = mutableListOf()
    private var labels: MutableList<String> = mutableListOf()
    private var currentIndex: Int = 0
    private var longPressFired = false

    // Décompte "épisode suivant" déclenché par la fin de lecture ExoPlayer
    private var nextEpisodeCountdown: CountDownTimer? = null
    private var countdownDialog: AlertDialog? = null

    private var tmdbId: Long = 0
    private var isTv: Boolean = false
    private var currentSeason: Int = -1
    private var currentEpisode: Int = -1
    private var movieTitle: String? = null
    private var posterUrl: String? = null
    private var backdropUrl: String? = null

    private val isEpisode: Boolean get() = isTv && currentSeason >= 0 && currentEpisode >= 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        urls = (intent.getStringArrayListExtra(EXTRA_URLS) ?: arrayListOf()).toMutableList()
        labels = (intent.getStringArrayListExtra(EXTRA_LABELS) ?: arrayListOf()).toMutableList()
        currentIndex = intent.getIntExtra(EXTRA_INDEX, 0)
        tmdbId = intent.getLongExtra(EXTRA_TMDB_ID, 0)
        isTv = intent.getBooleanExtra(EXTRA_IS_TV, false)
        currentSeason = intent.getIntExtra(EXTRA_SEASON, -1)
        currentEpisode = intent.getIntExtra(EXTRA_EPISODE, -1)
        movieTitle = intent.getStringExtra(EXTRA_TITLE)
        posterUrl = intent.getStringExtra(EXTRA_POSTER)
        backdropUrl = intent.getStringExtra(EXTRA_BACKDROP)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, PlaybackVideoFragment())
                .commit()
        }
        if (urls.size > 1) {
            Toast.makeText(
                this,
                "Maintiens RETOUR pour changer de source",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    longPressFired = false
                    event.startTracking()
                } else if (event.isLongPress) {
                    if (urls.size > 1) {
                        longPressFired = true
                        showSourcePicker()
                    }
                }
                true
            }
            KeyEvent.ACTION_UP -> {
                if (longPressFired) {
                    longPressFired = false
                } else {
                    showQuitConfirmation()
                }
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun showQuitConfirmation() {
        val items = buildList {
            add("Continuer la lecture")
            if (isEpisode) add("Épisode suivant")
            if (urls.size > 1) add("Changer de source")
            add("Quitter")
        }.toTypedArray()

        AlertDialog.Builder(this, R.style.MovixDialog)
            .setTitle("Quitter la lecture ?")
            .setItems(items) { _, idx ->
                when (items[idx]) {
                    "Continuer la lecture" -> { /* dismiss */ }
                    "Épisode suivant" -> playNextEpisode()
                    "Changer de source" -> showSourcePicker()
                    "Quitter" -> finish()
                }
            }
            .show()
    }

    private fun showSourcePicker() {
        AlertDialog.Builder(this, R.style.MovixDialog)
            .setTitle("Changer de source")
            .setSingleChoiceItems(labels.toTypedArray(), currentIndex) { dialog, which ->
                dialog.dismiss()
                if (which == currentIndex) return@setSingleChoiceItems
                currentIndex = which
                val newUrl = urls.getOrNull(which) ?: return@setSingleChoiceItems
                val frag = supportFragmentManager.findFragmentById(android.R.id.content)
                if (frag is PlaybackVideoFragment) {
                    frag.changeSource(newUrl)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    /**
     * Appelé par [PlaybackVideoFragment] quand ExoPlayer atteint STATE_ENDED
     * (vraie fin de lecture, pas une pause). Lance un décompte annulable avant
     * d'enchaîner sur l'épisode suivant.
     */
    fun onPlaybackEnded() {
        if (isFinishing || isDestroyed) return
        if (!isEpisode) return
        if (countdownDialog?.isShowing == true) return
        startNextEpisodeCountdown()
    }

    private fun startNextEpisodeCountdown() {
        nextEpisodeCountdown?.cancel()
        val seconds = NEXT_EP_COUNTDOWN_MS / 1000
        val dialog = AlertDialog.Builder(this, R.style.MovixDialog)
            .setTitle("Épisode terminé")
            .setMessage("Épisode suivant dans $seconds s…")
            .setCancelable(true)
            .setPositiveButton("Lancer maintenant", null)
            .setNegativeButton("Annuler", null)
            .create()
        countdownDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                nextEpisodeCountdown?.cancel()
                dialog.dismiss()
                playNextEpisode()
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                nextEpisodeCountdown?.cancel()
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener {
            countdownDialog = null
            nextEpisodeCountdown?.cancel()
            nextEpisodeCountdown = null
        }
        dialog.show()
        nextEpisodeCountdown = object : CountDownTimer(NEXT_EP_COUNTDOWN_MS, 1000) {
            override fun onTick(msUntilFinished: Long) {
                val s = (msUntilFinished / 1000) + 1
                dialog.setMessage("Épisode suivant dans $s s…")
            }
            override fun onFinish() {
                if (dialog.isShowing) dialog.dismiss()
                playNextEpisode()
            }
        }.start()
    }

    override fun onDestroy() {
        nextEpisodeCountdown?.cancel()
        nextEpisodeCountdown = null
        countdownDialog?.dismiss()
        countdownDialog = null
        super.onDestroy()
    }

    private fun playNextEpisode() {
        if (!isEpisode) return
        Toast.makeText(this, "Recherche de l'épisode suivant…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val next = withContext(Dispatchers.IO) {
                PlaybackNavigator.resolveNext(applicationContext, tmdbId, currentSeason, currentEpisode, movieTitle)
            }
            if (next == null) {
                Toast.makeText(this@PlaybackActivity, "Pas d'épisode suivant disponible", Toast.LENGTH_LONG).show()
                return@launch
            }
            val newUrls = ArrayList(next.links.map { it.bestUrl() })
            val newLabels = ArrayList(next.links.map { "[${it.category}] ${it.displayName()}" })
            val firstUrl = newUrls.firstOrNull() ?: return@launch

            val fakeMovie = Movie(
                id = tmdbId,
                tmdbId = tmdbId,
                isTv = true,
                title = movieTitle,
                cardImageUrl = posterUrl,
                backgroundImageUrl = backdropUrl
            )
            WatchHistory.record(applicationContext, fakeMovie, next.season, next.episode)

            // Si la 1ère source du nouvel épisode est un embed, on bascule sur la WebView
            val activityClass = if (isDirectStream(firstUrl)) PlaybackActivity::class.java
                                else WebPlaybackActivity::class.java
            val intent = Intent(this@PlaybackActivity, activityClass).apply {
                putExtra(EXTRA_URL, firstUrl)
                putExtra(EXTRA_TITLE, movieTitle)
                putStringArrayListExtra(EXTRA_URLS, newUrls)
                putStringArrayListExtra(EXTRA_LABELS, newLabels)
                putExtra(EXTRA_INDEX, 0)
                putExtra(EXTRA_TMDB_ID, tmdbId)
                putExtra(EXTRA_IS_TV, true)
                putExtra(EXTRA_SEASON, next.season)
                putExtra(EXTRA_EPISODE, next.episode)
                putExtra(EXTRA_POSTER, posterUrl)
                putExtra(EXTRA_BACKDROP, backdropUrl)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun isDirectStream(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains(".mpd") ||
                lower.endsWith(".mp4") || lower.endsWith(".webm") ||
                lower.endsWith(".mkv")
    }

    companion object {
        // Délai avant lancement automatique de l'épisode suivant en fin de lecture
        private const val NEXT_EP_COUNTDOWN_MS = 8_000L

        // Extras partagés avec WebPlaybackActivity (mêmes clés pour symétrie)
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_URLS = "extra_urls"
        const val EXTRA_LABELS = "extra_labels"
        const val EXTRA_INDEX = "extra_index"
        const val EXTRA_TMDB_ID = "extra_tmdb_id"
        const val EXTRA_IS_TV = "extra_is_tv"
        const val EXTRA_SEASON = "extra_season"
        const val EXTRA_EPISODE = "extra_episode"
        const val EXTRA_POSTER = "extra_poster"
        const val EXTRA_BACKDROP = "extra_backdrop"
    }
}
