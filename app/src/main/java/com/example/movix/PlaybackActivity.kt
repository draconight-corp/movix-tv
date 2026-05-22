package com.example.movix

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

class PlaybackActivity : FragmentActivity() {

    private val urls: List<String> by lazy {
        intent.getStringArrayListExtra(EXTRA_URLS) ?: emptyList()
    }
    private val labels: List<String> by lazy {
        intent.getStringArrayListExtra(EXTRA_LABELS) ?: emptyList()
    }
    private var currentIndex: Int = 0
    private var longPressFired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentIndex = intent.getIntExtra(EXTRA_INDEX, 0)
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

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_URLS = "extra_urls"
        const val EXTRA_LABELS = "extra_labels"
        const val EXTRA_INDEX = "extra_index"
    }
}
