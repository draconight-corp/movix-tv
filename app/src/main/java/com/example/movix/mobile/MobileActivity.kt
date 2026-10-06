package com.example.movix.mobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.movix.config.MirrorResolver
import com.example.movix.update.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Point d'entrée sur téléphone / tablette (catégorie LAUNCHER). Sur TV,
 * c'est MainActivity (LEANBACK_LAUNCHER) qui s'ouvre avec l'UI télécommande.
 */
class MobileActivity : FragmentActivity() {

    private val vm: MobileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Icônes de barre d'état claires : l'UI est toujours sombre
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            MovixTheme { MobileApp(vm) }
        }

        // Auto-détection du domaine Movix actif (silencieuse, en arrière-plan)
        lifecycleScope.launch { MirrorResolver.resolve(applicationContext) }

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                delay(2000)
                UpdateManager.checkAndPrompt(this@MobileActivity)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // L'historique a pu changer pendant la lecture
        vm.refreshHistory()
    }
}
