package com.example.movix.webfilter

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Bloqueur de pub/popup minimaliste pour les pages embed Movix.
 *
 * 1. Une liste de hosts à bloquer (régie pub + popunder connus). Une requête
 *    qui matche reçoit une réponse vide 200 (au lieu d'un network error qui
 *    pourrait casser des pages).
 * 2. Un bout de JS injecté dans chaque page qui désactive `window.open`,
 *    `window.alert/confirm/prompt`, neutralise les `<a target="_blank">`,
 *    intercepte les tentatives de redirection forcée via window.top.location,
 *    et masque les overlays plein-écran de clickjacking.
 */
object AdBlocker {

    /**
     * Liste de régies pub / trackers / popunders couramment chargés par les
     * sites de streaming embed. Pas exhaustif, mais couvre les plus gros.
     */
    private val BLOCKED_HOSTS: Set<String> = setOf(
        // Régies pub mainstream
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
        // Pub-redirect typique vu sur uqload/vidmoly/voe et compagnie
        "linkscash.com", "yieldlove.com", "yllix.com", "highrevenuegate.com",
        "highperformancecpm.com", "topcreativeformat.com", "fastcontactmedia.com",
        // Ajouts : trackers VAST/VPAID, redirect chains, popunder modernes
        "aniview.com", "aniview.tv", "spotxchange.com", "spotx.tv",
        "adblade.com", "adsupply.com", "bidvertiser.com", "infolinks.com",
        "popunder.bid", "popunder.net", "trafmag.com", "adexc.net",
        "clickaine.com", "clickservices.org", "adventory.com",
        "adwizard.io", "syndication.exoclick.com", "syndication.adsrv.eacdn.com",
        "ads.exoclick.com", "main.exoclick.com", "static.exoclick.com",
        "outstream.exoclick.com", "syndication.outsmart.com",
        "trafficshop.com", "trafficforce.com", "advertica.com",
        "popunderclick.com", "ad-feeds.net", "advdelivery.com",
        "engagement-platform.com", "appsflyer.com",
        // Cryptominers + skimmers parfois greffés sur ces players
        "coinhive.com", "coin-hive.com", "jsecoin.com", "cryptoloot.pro",
        "minero.cc", "webmine.cz", "authedmine.com",
        // Notification spam / push subscription pushers
        "notifizr.com", "notif.io", "pushnative.com", "pushwhy.com",
        "pushedrich.com", "richpush.co", "pushmoney.com", "subscriptions.pushjs.com",
        // Toolbar/extension installers
        "ad.plus", "ad-delivery.net", "imasdk.googleapis.com",
        // Ad serving via TLD .top/.xyz souvent utilisés pour popunders
        "kerygmatickol.com", "spkywqo.com", "uwhsegcgn.com", "rixaba.com",
        "ushuasiaeve.com", "swatswenghe.com", "popsoma.com", "lijit.com"
    )

    /**
     * Patterns d'URL ad-server qui n'ont pas un host fixe (généralement des
     * paths typiques : /ads/, /adserver/, /pop/, /popup/, etc.).
     */
    private val BLOCKED_PATH_TOKENS: List<String> = listOf(
        "/ads/", "/adserver/", "/advert/", "/popunder", "/popup",
        "/ads.js", "/ad.js", "/adframe", "/banners/", "/adsbygoogle"
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
        val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val host = parsed.host?.lowercase() ?: return false
        val path = parsed.path?.lowercase().orEmpty()

        // Whitelist gagne toujours (player officiel)
        if (ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }) return false

        // Blacklist suffix-match (sous-domaines inclus)
        if (BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }) return true

        // Path-based ad pattern (catch-all pour /ads/, /popunder etc.)
        if (BLOCKED_PATH_TOKENS.any { path.contains(it) }) return true

        return false
    }

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )

    /**
     * Script injecté dans chaque page pour neutraliser popups et redirections
     * forcées. Exécuté avec evaluateJavascript après onPageStarted ET onPageFinished.
     */
    val ANTI_POPUP_JS = """
        (function() {
          try {
            // ── Popups / dialogs natifs ────────────────────────────────────
            window.open = function() { return null; };
            window.alert = function() {};
            window.confirm = function() { return true; };
            window.prompt = function() { return null; };
            window.print = function() {};

            // ── Beforeunload / pagehide (forcent souvent un popup) ─────────
            window.onbeforeunload = null;
            window.onpagehide = null;
            try {
              var _add = EventTarget.prototype.addEventListener;
              EventTarget.prototype.addEventListener = function(type, listener, opts) {
                if (type === 'beforeunload' || type === 'pagehide' ||
                    type === 'unload' || type === 'visibilitychange') return;
                return _add.call(this, type, listener, opts);
              };
            } catch(e){}

            // ── Bloque redirection programmatique vers ad-server ───────────
            // Beaucoup d'embeds font: window.top.location = "url-pub" au 1er clic.
            // On wrappe document.location et window.top.location setter pour
            // ignorer toute URL qui ne matche pas l'origine de la page.
            try {
              var origin = location.origin;
              function isSafe(u) {
                if (!u) return true;
                try {
                  var parsed = new URL(u, location.href);
                  return parsed.origin === origin;
                } catch(e) { return true; }
              }
              try {
                var topProto = Object.getPrototypeOf(window.top) || Window.prototype;
                var locDesc = Object.getOwnPropertyDescriptor(topProto, 'location');
                if (!locDesc || locDesc.configurable !== false) {
                  Object.defineProperty(window.top, 'location', {
                    get: function() { return document.location; },
                    set: function(v) { if (isSafe(v)) document.location = v; }
                  });
                }
              } catch(e){}
              // Bloque les replace/assign sur location
              var _assign = location.assign.bind(location);
              var _replace = location.replace.bind(location);
              location.assign = function(u) { if (isSafe(u)) _assign(u); };
              location.replace = function(u) { if (isSafe(u)) _replace(u); };
            } catch(e){}

            // ── <a target="_blank"> et liens externes ──────────────────────
            document.addEventListener('click', function(e) {
              var a = e.target.closest && e.target.closest('a');
              if (a) {
                if (a.target === '_blank') a.target = '_self';
                // Si l'href quitte l'origine, on bloque le clic (l'embed peut
                // toujours utiliser des handlers JS internes pour son player)
                var href = a.getAttribute('href') || '';
                if (/^https?:\/\//i.test(href)) {
                  try {
                    var u = new URL(href, location.href);
                    if (u.origin !== location.origin) {
                      e.preventDefault(); e.stopPropagation();
                    }
                  } catch(e){}
                }
              }
            }, true);

            // ── Tueur d'overlays plein-écran de clickjacking ───────────────
            // Beaucoup d'embeds collent un <div> ou <iframe> transparent par
            // dessus le player qui redirige au 1er clic. On les détecte
            // périodiquement et on les retire.
            function killOverlays() {
              try {
                var w = window.innerWidth, h = window.innerHeight;
                var nodes = document.querySelectorAll('iframe, div, a');
                for (var i = 0; i < nodes.length; i++) {
                  var n = nodes[i];
                  var s = window.getComputedStyle(n);
                  if (!s) continue;
                  if (s.position !== 'fixed' && s.position !== 'absolute') continue;
                  var r = n.getBoundingClientRect();
                  // Cible : élément qui couvre > 60% de la viewport et est en haut z-index
                  if (r.width > w * 0.6 && r.height > h * 0.6 &&
                      parseInt(s.zIndex || '0', 10) > 100) {
                    // On laisse tranquille les iframes du player connu (host whitelist côté natif)
                    var tag = (n.tagName || '').toLowerCase();
                    if (tag === 'iframe') {
                      var src = (n.src || '').toLowerCase();
                      // Heuristique : si l'iframe ne contient pas "video"/"player"
                      // et est plein écran et translucide, c'est probablement un overlay pub.
                      var alpha = parseFloat(s.opacity || '1');
                      if (alpha < 0.5) { try { n.remove(); } catch(e){} continue; }
                      if (!/video|player|stream|embed/.test(src)) {
                        try { n.remove(); } catch(e){}
                      }
                    } else {
                      // div/a translucide ou vide = overlay
                      var hasText = (n.innerText || '').trim().length > 0;
                      var alpha2 = parseFloat(s.opacity || '1');
                      if (alpha2 < 0.5 || !hasText) {
                        try { n.remove(); } catch(e){}
                      }
                    }
                  }
                }
              } catch(e){}
            }
            killOverlays();
            setTimeout(killOverlays, 1000);
            setTimeout(killOverlays, 3000);
            setTimeout(killOverlays, 6000);
            try {
              new MutationObserver(function(){ killOverlays(); })
                .observe(document.documentElement, { childList: true, subtree: true });
            } catch(e){}
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
