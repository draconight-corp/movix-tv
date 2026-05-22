package com.example.movix

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
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

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var cursorView: View

    private val urls: List<String> by lazy { intent.getStringArrayListExtra(EXTRA_URLS) ?: emptyList() }
    private val labels: List<String> by lazy { intent.getStringArrayListExtra(EXTRA_LABELS) ?: emptyList() }
    private var currentIndex: Int = 0

    private var backLongPressFired = false
    private var okLongPressFired = false
    private var cursorMode = false
    private var cursorX = 0f
    private var cursorY = 0f
    private val cursorStepPx by lazy {
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
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

        webView = createWebView()
        cursorView = createCursorView()

        root = FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(cursorView, FrameLayout.LayoutParams(
                cursorStepPx.toInt() * 2,
                cursorStepPx.toInt() * 2
            ))
        }
        setContentView(root)

        showFirstTimeHints()
        webView.loadUrl(url)
    }

    private fun showFirstTimeHints() {
        val hints = mutableListOf<String>()
        if (urls.size > 1) hints += "Maintiens RETOUR pour changer de source"
        hints += "Maintiens OK pour activer le curseur"
        Toast.makeText(this, hints.joinToString(" • "), Toast.LENGTH_LONG).show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
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
                if (target.startsWith("intent:") || target.startsWith("market:") ||
                    target.contains("play.google.com") || target.startsWith("mailto:") ||
                    target.startsWith("tel:") || target.startsWith("sms:")) return true
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
                view.evaluateJavascript(AdBlocker.FORCE_PLAY_JS, null)
            }
        }
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    private fun createCursorView(): View {
        val v = View(this)
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x88FFEB3B.toInt())
            setStroke(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics).toInt(),
                0xFF000000.toInt()
            )
        }
        v.background = ring
        v.visibility = View.GONE
        return v
    }

    // ── Key handling ─────────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> handleBack(event)
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_SELECT -> handleOk(event)
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> handleDpad(event)
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun handleBack(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    backLongPressFired = false
                    event.startTracking()
                } else if (event.isLongPress && urls.size > 1) {
                    backLongPressFired = true
                    showSourcePicker()
                }
            }
            KeyEvent.ACTION_UP -> {
                if (backLongPressFired) {
                    backLongPressFired = false
                } else if (cursorMode) {
                    setCursorMode(false)
                } else {
                    showQuitConfirmation()
                }
            }
        }
        return true
    }

    private fun handleOk(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    okLongPressFired = false
                    event.startTracking()
                } else if (event.isLongPress) {
                    okLongPressFired = true
                    setCursorMode(!cursorMode)
                }
            }
            KeyEvent.ACTION_UP -> {
                if (okLongPressFired) {
                    okLongPressFired = false
                    return true
                }
                if (cursorMode) {
                    tapAtCursor()
                } else {
                    // Court OK : tente le clic via JS sur tout bouton play visible
                    webView.evaluateJavascript(AdBlocker.FORCE_PLAY_JS, null)
                    // Et envoie un tap au centre comme fallback
                    tapAt(webView.width / 2f, webView.height / 2f)
                }
            }
        }
        return true
    }

    private fun handleDpad(event: KeyEvent): Boolean {
        if (!cursorMode) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT  -> cursorX -= cursorStepPx
            KeyEvent.KEYCODE_DPAD_RIGHT -> cursorX += cursorStepPx
            KeyEvent.KEYCODE_DPAD_UP    -> cursorY -= cursorStepPx
            KeyEvent.KEYCODE_DPAD_DOWN  -> cursorY += cursorStepPx
        }
        clampCursor()
        positionCursorView()
        return true
    }

    private fun setCursorMode(on: Boolean) {
        cursorMode = on
        if (on) {
            if (cursorX == 0f && cursorY == 0f) {
                cursorX = root.width / 2f
                cursorY = root.height / 2f
            }
            positionCursorView()
            cursorView.visibility = View.VISIBLE
            Toast.makeText(
                this,
                "Mode curseur ACTIVÉ — flèches = déplacer, OK = clic, RETOUR ou long-OK = quitter",
                Toast.LENGTH_LONG
            ).show()
        } else {
            cursorView.visibility = View.GONE
            Toast.makeText(this, "Mode curseur désactivé", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clampCursor() {
        val size = cursorView.width.takeIf { it > 0 }?.toFloat() ?: (cursorStepPx * 2f)
        val maxX = (root.width - size).coerceAtLeast(0f)
        val maxY = (root.height - size).coerceAtLeast(0f)
        cursorX = cursorX.coerceIn(0f, maxX)
        cursorY = cursorY.coerceIn(0f, maxY)
    }

    private fun positionCursorView() {
        cursorView.translationX = cursorX
        cursorView.translationY = cursorY
    }

    /**
     * Injecte un vrai MotionEvent (ACTION_DOWN + ACTION_UP) au centre du
     * curseur, dans la WebView. La WebView dispatche ça au DOM comme un
     * tap utilisateur — c'est ce qui satisfait l'exigence "user gesture"
     * des players HTML5.
     */
    private fun tapAtCursor() {
        val centerX = cursorX + cursorView.width / 2f
        val centerY = cursorY + cursorView.height / 2f
        tapAt(centerX, centerY)
    }

    private fun tapAt(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, x, y, 0)
        try {
            webView.dispatchTouchEvent(down)
            webView.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    private fun showQuitConfirmation() {
        val items = if (urls.size > 1) {
            arrayOf("Continuer la lecture", "Changer de source", "Quitter")
        } else {
            arrayOf("Continuer la lecture", "Quitter")
        }
        AlertDialog.Builder(this, R.style.MovixDialog)
            .setTitle("Quitter la lecture ?")
            .setItems(items) { _, idx ->
                when {
                    idx == 0 -> { /* dismiss */ }
                    idx == 1 && urls.size > 1 -> showSourcePicker()
                    else -> finish()
                }
            }
            .setOnCancelListener { /* ne fait rien */ }
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
                webView.stopLoading()
                webView.loadUrl(newUrl)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

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
