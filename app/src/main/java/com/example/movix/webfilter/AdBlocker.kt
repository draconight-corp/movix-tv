package com.example.movix.webfilter

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Bloqueur de pub/popup minimaliste pour les pages embed Movix.
 *
 * 1. Une liste de hosts à bloquer (regie pub + popunder connus). Une requête
 *    qui matche reçoit une réponse vide 200 (au lieu d'un network error qui
 *    pourrait casser des pages).
 * 2. Un bout de JS injecté dans chaque page qui désactive `window.open`,
 *    `window.alert/confirm/prompt`, et neutralise les `<a target="_blank">`.
 */
object AdBlocker {

    /**
     * Liste de régies pub / trackers / popunders couramment chargés par les
     * sites de streaming embed. Pas exhaustif, mais couvre les plus gros.
     */
    private val BLOCKED_HOSTS: Set<String> = setOf(
        // Régies pub
        "googlesyndication.com", "doubleclick.net", "googletagservices.com",
        "googleadservices.com", "google-analytics.com", "googletagmanager.com",
        "adservice.google.com", "adsystem.amazon.com", "amazon-adsystem.com",
        "facebook.com", "facebook.net", "connect.facebook.net",
        "scorecardresearch.com", "quantserve.com", "adnxs.com", "rubiconproject.com",
        "pubmatic.com", "openx.net", "criteo.com", "criteo.net",
        "moatads.com", "adsrvr.org", "yandex.ru", "mc.yandex.ru",
        // Popunder + sketchy ad networks couramment vus sur les embeds
        "popads.net", "popcash.net", "popmyads.com", "propellerads.com",
        "propellerclick.com", "propu.sh", "adsterra.com", "adscpm.com",
        "exoclick.com", "exosrv.com", "trafficfactory.biz", "trafficjunky.net",
        "juicyads.com", "juicyads.rocks", "adcash.com", "ad-maven.com",
        "clickadu.com", "hilltopads.com", "revcontent.com", "outbrain.com",
        "taboola.com", "mgid.com", "redirectvoluum.com", "voluumtrk.com",
        "histats.com", "go2cloud.org", "go2top.xyz", "go2affise.com",
        "onclickads.net", "onclkds.com", "onclickperformance.com",
        "popcash.net", "popunder.net", "popmyads.com",
        "smartadserver.com", "trafficstars.com", "tsyndicate.com",
        "creativecdn.com", "smatoo.com", "media.net",
        "adskeeper.com", "adskeeper.co.uk", "adskeeper.net",
        // Pub-redirect typique vu sur uqload/vidmoly et compagnie
        "linkscash.com", "yieldlove.com", "yllix.com", "highrevenuegate.com",
        "highperformancecpm.com", "topcreativeformat.com", "fastcontactmedia.com"
    )

    /**
     * Hôtes courants des players embed à ne JAMAIS bloquer même si une URL
     * leur ressemble (whitelist défensive).
     */
    private val ALLOWED_HOSTS: Set<String> = setOf(
        "uqload.cx", "uqload.is", "uqload.net", "uqload.io",
        "vidmoly.me", "vidmoly.net", "vidmoly.to",
        "voe.sx", "voe.sx", "doodstream.com", "doply.net",
        "filemoon.sx", "lulustream.com", "luluvdo.com",
        "wishonly.site", "darkibox.com", "veev.to", "waaw.to", "waaw1.tv",
        "listeamed.net", "mivalyo.com", "coflix.upn.one",
        "lecteurvideo.com", "lecteur3.xtremestream.xyz",
        "kakaflix.lol", "fsvid.lol", "vidzy.org", "ralphysuccessfull.org",
        "younetu.org", "lukefirst.lol", "minochinos.com", "hgcloud.to",
        "playmogo.com", "fembed.com", "upvid.co", "vidoza.net", "uptostream.link"
    )

    fun shouldBlock(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return false

        // Whitelist gagne toujours
        if (ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }) return false

        // Blacklist suffix-match (sous-domaines inclus)
        return BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )

    /**
     * Script injecté dans chaque page pour neutraliser popups et redirections
     * forcées. Exécuté avec evaluateJavascript après onPageStarted.
     */
    val ANTI_POPUP_JS = """
        (function() {
          try {
            // Bloque window.open complètement
            window.open = function() { return null; };
            // Empêche les boites système intrusives
            window.alert = function() {};
            window.confirm = function() { return true; };
            window.prompt = function() { return null; };
            // Bloque les redirections via beforeunload
            window.onbeforeunload = null;
            // Force tous les <a target="_blank"> à s'ouvrir dans la page (donc bloqués par notre webViewClient)
            document.addEventListener('click', function(e) {
              var a = e.target.closest && e.target.closest('a');
              if (a && a.target === '_blank') a.target = '_self';
            }, true);
          } catch (e) {}
        })();
    """.trimIndent()

    /**
     * Tente de lancer la lecture sans intervention utilisateur :
     *  - démute et appelle .play() sur tous les <video>
     *  - clique tout bouton "play" évident (selectors les plus courants)
     *  - retry à 0/1.5s/3s/5s pour les players qui se chargent en JS
     */
    val FORCE_PLAY_JS = """
        (function() {
          function tryPlay() {
            var clicked = false;
            try {
              document.querySelectorAll('video').forEach(function(v){
                try { v.muted = false; v.removeAttribute('muted'); v.play(); } catch(e){}
              });
            } catch(e){}
            var selectors = [
              '.vjs-big-play-button', '.jw-icon-display', '.jw-display-icon-display',
              '.plyr__control--overlaid', '.play-button', '.play_button', '.btn-play',
              'button.play', '.player-play', '.player_play', '.play-overlay',
              '#play', '#play-button', '#playButton',
              '[aria-label="Play"]', '[aria-label*="Play"]', '[aria-label*="Lire"]',
              '[title="Play"]', '[title*="Play"]',
              '.vjs-poster', '.video-overlay',
              '[class*="play-btn"]', '[class*="PlayBtn"]', '[class*="play_btn"]'
            ];
            for (var i = 0; i < selectors.length; i++) {
              try {
                document.querySelectorAll(selectors[i]).forEach(function(el){
                  if (el && el.offsetParent !== null) {
                    try { el.click(); clicked = true; } catch(e){}
                  }
                });
              } catch(e){}
            }
            return clicked;
          }
          tryPlay();
          setTimeout(tryPlay, 1500);
          setTimeout(tryPlay, 3000);
          setTimeout(tryPlay, 5000);
        })();
    """.trimIndent()
}
