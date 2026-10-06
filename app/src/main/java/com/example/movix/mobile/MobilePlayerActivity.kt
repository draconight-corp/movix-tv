package com.example.movix.mobile

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.movix.Movie
import com.example.movix.PlaybackActivity
import com.example.movix.PlaybackNavigator
import com.example.movix.PlayerLauncher
import com.example.movix.R
import com.example.movix.data.ApiClient
import com.example.movix.history.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lecteur natif pour téléphone : PlayerView Media3 (contrôles tactiles,
 * plein écran paysage) + barre du haut (retour, titre, sources, épisode
 * suivant). Utilise les mêmes extras que [PlaybackActivity].
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MobilePlayerActivity : FragmentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var topBar: LinearLayout
    private lateinit var nextButton: Button

    private var urls: MutableList<String> = mutableListOf()
    private var labels: MutableList<String> = mutableListOf()
    private var currentIndex = 0
    private var tmdbId = 0L
    private var isTv = false
    private var season = -1
    private var episode = -1
    private var title: String? = null
    private var posterUrl: String? = null
    private var backdropUrl: String? = null

    private val isEpisode get() = isTv && season >= 0 && episode >= 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        val url = intent.getStringExtra(PlaybackActivity.EXTRA_URL)
        urls = (intent.getStringArrayListExtra(PlaybackActivity.EXTRA_URLS) ?: arrayListOf()).toMutableList()
        labels = (intent.getStringArrayListExtra(PlaybackActivity.EXTRA_LABELS) ?: arrayListOf()).toMutableList()
        currentIndex = intent.getIntExtra(PlaybackActivity.EXTRA_INDEX, 0)
        tmdbId = intent.getLongExtra(PlaybackActivity.EXTRA_TMDB_ID, 0)
        isTv = intent.getBooleanExtra(PlaybackActivity.EXTRA_IS_TV, false)
        season = intent.getIntExtra(PlaybackActivity.EXTRA_SEASON, -1)
        episode = intent.getIntExtra(PlaybackActivity.EXTRA_EPISODE, -1)
        title = intent.getStringExtra(PlaybackActivity.EXTRA_TITLE)
        posterUrl = intent.getStringExtra(PlaybackActivity.EXTRA_POSTER)
        backdropUrl = intent.getStringExtra(PlaybackActivity.EXTRA_BACKDROP)
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "URL manquante", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (urls.isEmpty()) { urls += url; labels += "Source 1" }

        setContentView(buildLayout())
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(
                    OkHttpDataSource.Factory(ApiClient.okHttp).setUserAgent("MovixTV/1.0 (Android)")
                )
            )
            .build()
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val msg = if (urls.size > 1) "Erreur de lecture — essaie une autre source"
                else "Erreur de lecture (${error.errorCodeName})"
                Toast.makeText(this@MobilePlayerActivity, msg, Toast.LENGTH_LONG).show()
                if (urls.size > 1) showSourcePicker()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && isEpisode) playNextEpisode()
            }
        })
        playerView.player = exo
        player = exo
        play(url)
    }

    private fun buildLayout(): View {
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }
        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            setShowNextButton(false)
            setShowPreviousButton(false)
            keepScreenOn = true
        }
        val back = TextView(this).apply {
            text = "←"
            textSize = 26f
            setTextColor(Color.WHITE)
            // Zone tactile large (doigt)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { finish() }
        }
        val titleView = TextView(this).apply {
            text = buildTitle()
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 1
            setPadding(dp(8), 0, dp(8), 0)
        }
        val sources = Button(this).apply {
            text = "Sources"
            setOnClickListener { showSourcePicker() }
            visibility = if (urls.size > 1) View.VISIBLE else View.GONE
        }
        nextButton = Button(this).apply {
            text = "Épisode suivant"
            setOnClickListener { playNextEpisode() }
            visibility = if (isEpisode) View.VISIBLE else View.GONE
        }
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0x99000000.toInt())
            setPadding(dp(8), dp(8), dp(16), dp(8))
            addView(back)
            addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(sources)
            addView(nextButton)
        }
        // La barre du haut suit l'affichage des contrôles du lecteur
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
            topBar.visibility = v
        })
        return FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(playerView, FrameLayout.LayoutParams(-1, -1))
            addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        }
    }

    private fun buildTitle(): String = buildString {
        append(title.orEmpty())
        if (isEpisode) append("  •  S%02dE%02d".format(season, episode))
    }

    private fun play(url: String) {
        val item = MediaItem.Builder().setUri(url).apply {
            when {
                url.contains(".m3u8") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                url.contains(".mpd") -> setMimeType(MimeTypes.APPLICATION_MPD)
            }
        }.build()
        player?.run {
            setMediaItem(item)
            prepare()
            playWhenReady = true
        }
    }

    private fun showSourcePicker() {
        if (isFinishing) return
        AlertDialog.Builder(this, R.style.MovixDialog)
            .setTitle("Changer de source")
            .setSingleChoiceItems(labels.toTypedArray(), currentIndex) { d, which ->
                d.dismiss()
                if (which == currentIndex) return@setSingleChoiceItems
                currentIndex = which
                val newUrl = urls.getOrNull(which) ?: return@setSingleChoiceItems
                if (PlayerLauncher.isDirectStream(newUrl)) {
                    play(newUrl)
                } else {
                    // Source "page web" : on passe au lecteur web verrouillé
                    startActivity(Intent(intent).setClass(this, com.example.movix.WebPlaybackActivity::class.java)
                        .putExtra(com.example.movix.WebPlaybackActivity.EXTRA_URL, newUrl)
                        .putExtra(com.example.movix.WebPlaybackActivity.EXTRA_INDEX, which))
                    finish()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun playNextEpisode() {
        if (!isEpisode) return
        nextButton.isEnabled = false
        Toast.makeText(this, "Recherche de l'épisode suivant…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val next = withContext(Dispatchers.IO) {
                PlaybackNavigator.resolveNext(applicationContext, tmdbId, season, episode, title)
            }
            nextButton.isEnabled = true
            if (next == null || next.links.isEmpty()) {
                Toast.makeText(this@MobilePlayerActivity, "Pas d'épisode suivant disponible", Toast.LENGTH_LONG).show()
                return@launch
            }
            val movie = Movie(id = tmdbId, tmdbId = tmdbId, isTv = true, title = title,
                cardImageUrl = posterUrl, backgroundImageUrl = backdropUrl)
            WatchHistory.record(applicationContext, movie, next.season, next.episode)
            startActivity(PlayerLauncher.intentFor(this@MobilePlayerActivity, movie, next.links, 0, next.season, next.episode))
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
