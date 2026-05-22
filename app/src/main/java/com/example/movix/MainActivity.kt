package com.example.movix

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.movix.config.MirrorResolver
import com.example.movix.update.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Heartbeat : écrit dans filesDir la version qui s'est lancée
        writeHeartbeat()

        // Badge version overlay (toujours visible top-right)
        findViewById<TextView>(R.id.version_badge)?.text = "v${BuildConfig.VERSION_NAME}"

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, MainFragment())
                .commitNow()
        }

        // Toast indéniable au lancement
        Toast.makeText(
            this,
            "Movix TV v${BuildConfig.VERSION_NAME} chargée",
            Toast.LENGTH_LONG
        ).show()

        // Auto-détection du domaine Movix actif (silencieuse, en arrière-plan)
        lifecycleScope.launch {
            MirrorResolver.resolve(applicationContext)
        }

        lifecycleScope.launch {
            delay(2000)
            UpdateManager.checkAndPrompt(this@MainActivity)
        }
    }

    private fun writeHeartbeat() {
        runCatching {
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            File(filesDir, "last_launch.txt").writeText(
                "Movix TV ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) - $now"
            )
        }
    }
}
