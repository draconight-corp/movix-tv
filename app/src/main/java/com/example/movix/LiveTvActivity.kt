package com.example.movix

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/** Écran "TV en direct" : catégories à gauche, chaînes en cartes. */
class LiveTvActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, LiveTvFragment())
                .commit()
        }
    }
}
