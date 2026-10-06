package com.example.movix.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.movix.Movie

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Filled.Home),
    SEARCH("Recherche", Icons.Filled.Search),
    LIVE("Direct", Icons.Filled.LiveTv),
    HISTORY("Historique", Icons.Filled.History)
}

/**
 * Racine de l'UI mobile : 4 onglets en bas + pile de fiches détail
 * par-dessus (le bouton retour dépile).
 */
@Composable
fun MobileApp(vm: MobileViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val detailsStack = remember { mutableStateListOf<Movie>() }
    val openDetails: (Movie) -> Unit = { detailsStack.add(it) }

    BackHandler(enabled = detailsStack.isNotEmpty()) { detailsStack.removeAt(detailsStack.lastIndex) }
    BackHandler(enabled = detailsStack.isEmpty() && tab != Tab.HOME) { tab = Tab.HOME }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.HOME -> HomeScreen(vm, openDetails)
                    Tab.SEARCH -> SearchScreen(vm, openDetails)
                    Tab.LIVE -> LiveScreen(vm)
                    Tab.HISTORY -> HistoryScreen(vm, openDetails)
                }
            }
        }

        // Fiche détail par-dessus les onglets : l'onglet garde sa position de
        // défilement quand on revient en arrière.
        val current = detailsStack.lastOrNull()
        if (current != null) {
            // key() : nouvel état (saisons, sources…) pour chaque fiche ouverte
            key(current.tmdbId, current.isTv, detailsStack.size) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DetailsScreen(
                        movie = current,
                        onBack = { detailsStack.removeAt(detailsStack.lastIndex) },
                        onPlayed = { vm.refreshHistory() }
                    )
                }
            }
        }
    }
}
