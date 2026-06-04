package com.example.movix

import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.widget.PlaybackControlsRow
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.leanback.LeanbackPlayerAdapter
import com.example.movix.data.ApiClient

@UnstableApi
class PlaybackVideoFragment : VideoSupportFragment() {

    private var player: ExoPlayer? = null
    private lateinit var glue: androidx.leanback.media.PlaybackTransportControlGlue<LeanbackPlayerAdapter>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = requireActivity().intent
        val url = args.getStringExtra(PlaybackActivity.EXTRA_URL) ?: return finishWith("URL manquante")
        val title = args.getStringExtra(PlaybackActivity.EXTRA_TITLE).orEmpty()
        val desc = args.getStringExtra(PlaybackActivity.EXTRA_DESCRIPTION).orEmpty()

        if (looksLikeEmbed(url)) {
            Toast.makeText(
                requireContext(),
                "Cette source est un embed (player web) — non lisible nativement. Essaie une autre source.",
                Toast.LENGTH_LONG
            ).show()
        }

        val httpFactory = OkHttpDataSource.Factory(ApiClient.okHttp).setUserAgent(
            "MovixTV/1.0 (Android TV)"
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(requireContext())
            .setDataSourceFactory(httpFactory)

        val exo = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        val mediaItem = buildMediaItem(url, title)
        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.playWhenReady = true
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    requireContext(),
                    "Erreur lecture: ${error.errorCodeName}",
                    Toast.LENGTH_LONG
                ).show()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                // Vraie fin de lecture (≠ pause) → délègue à l'activité qui
                // lance le décompte vers l'épisode suivant.
                if (playbackState == Player.STATE_ENDED) {
                    (activity as? PlaybackActivity)?.onPlaybackEnded()
                }
            }
        })

        val adapter = LeanbackPlayerAdapter(requireContext(), exo, UPDATE_DELAY)
        glue = androidx.leanback.media.PlaybackTransportControlGlue(requireActivity(), adapter)
        glue.host = VideoSupportFragmentGlueHost(this)
        glue.title = title
        glue.subtitle = desc
        glue.isSeekEnabled = true

        player = exo
    }

    private fun buildMediaItem(url: String, title: String): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
        val mime = when {
            url.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            url.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            url.contains(".mp4") -> MimeTypes.VIDEO_MP4
            else -> null
        }
        mime?.let { builder.setMimeType(it) }
        return builder.build()
    }

    private fun looksLikeEmbed(url: String): Boolean {
        // Les players "embed" sont des pages HTML, pas des flux : ExoPlayer ne saura pas les lire.
        val lower = url.lowercase()
        return ("/embed/" in lower || "embed-" in lower || lower.endsWith(".html")) &&
                !lower.contains(".m3u8") && !lower.contains(".mp4")
    }

    private fun finishWith(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        requireActivity().finish()
    }

    fun changeSource(newUrl: String) {
        val exo = player ?: return
        val newItem = buildMediaItem(newUrl, glue.title?.toString().orEmpty())
        exo.setMediaItem(newItem)
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onStart() {
        super.onStart()
        player?.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    companion object {
        private const val UPDATE_DELAY = 16
    }
}
