package com.example.movix

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.movix.webfilter.AdBlocker

class WebPlaybackActivity : FragmentActivity() {

    private lateinit var webView: WebView
    private val urls: List<String> by lazy { intent.getStringArrayListExtra(EXTRA_URLS) ?: emptyList() }
    private val labels: List<String> by lazy { intent.getStringArrayListExtra(EXTRA_LABELS) ?: emptyList() }
    private var currentIndex: Int = 0
    private var longPressFired = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )

        val url = intent.getStringExtra(EXTRA_URL)
        currentIndex = intent.getIntExtra(EXTRA_INDEX, 0)
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "URL manquante", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowContentAccess = true
                allowFileAccess = false
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString =
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
            }
            webChromeClient = object : WebChromeClient() {
                // Refuse toute ouverture de nouvelle fenêtre (popunders).
                override fun onCreateWindow(
                    view: WebView, isDialog: Boolean,
                    isUserGesture: Boolean, resultMsg: android.os.Message
                ): Boolean = false
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest
                ): WebResourceResponse? {
                    val u = request.url.toString()
                    return if (AdBlocker.shouldBlock(u)) AdBlocker.emptyResponse() else null
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    val target = request.url.toString()
                    // Intents externes interdits
                    if (target.startsWith("intent:") || target.startsWith("market:") ||
                        target.contains("play.google.com") || target.startsWith("mailto:") ||
                        target.startsWith("tel:") || target.startsWith("sms:")
                    ) return true
                    // Hosts dans la blocklist → bloque la navigation aussi
                    if (AdBlocker.shouldBlock(target)) return true
                    return false
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view.evaluateJavascript(AdBlocker.ANTI_POPUP_JS, null)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    view.evaluateJavascript(AdBlocker.ANTI_POPUP_JS, null)
                }
            }
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }
        setContentView(webView)

        if (urls.size > 1) {
            Toast.makeText(
                this,
                "Maintiens RETOUR pour changer de source",
                Toast.LENGTH_LONG
            ).show()
        }

        webView.loadUrl(url)
    }

    // Intercepte BACK AVANT que la WebView ne le consomme (sinon elle l'utilise pour
    // naviguer dans son historique). dispatchKeyEvent est appelé sur l'activité en
    // premier dans la chaîne de dispatch.
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
                    finish()
                }
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun showSourcePicker() {
        AlertDialog.Builder(this, R.style.MovixDialog)
            .setTitle("Changer de source")
            .setSingleChoiceItems(labels.toTypedArray(), currentIndex) { dialog, which ->
                dialog.dismiss()
                if (which == currentIndex) return@setSingleChoiceItems
                currentIndex = which
                val newUrl = urls.getOrNull(which) ?: return@setSingleChoiceItems
                webView.stopLoading()
                webView.loadUrl(newUrl)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.requestFocus()
    }

    override fun onDestroy() {
        runCatching {
            webView.stopLoading()
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_URLS = "extra_urls"
        const val EXTRA_LABELS = "extra_labels"
        const val EXTRA_INDEX = "extra_index"
    }
}
