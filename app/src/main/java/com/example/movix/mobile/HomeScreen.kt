package com.example.movix.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.movix.BuildConfig
import com.example.movix.Movie
import com.example.movix.config.AppMode
import com.example.movix.config.AppModeStore
import com.example.movix.history.WatchEntry

@Composable
fun HomeScreen(vm: MobileViewModel, onOpen: (Movie) -> Unit) {
    LaunchedEffect(Unit) { vm.loadHome() }
    val rows = vm.homeRows

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "MOVIX",
                        color = MovixRed,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppMode.entries.forEach { m ->
                        FilterChip(
                            selected = vm.mode == m,
                            onClick = { vm.switchMode(m) },
                            label = { Text(AppModeStore.label(m)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MovixRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        if (rows == null) {
            item { CenteredLoading(Modifier.height(400.dp)) }
            return@LazyColumn
        }
        if (rows.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Impossible de charger le catalogue. Vérifie la connexion.")
                    TextButton(onClick = { vm.loadHome(force = true) }) { Text("Réessayer") }
                }
            }
            return@LazyColumn
        }

        rows.firstOrNull()?.second?.firstOrNull { !it.backgroundImageUrl.isNullOrBlank() }?.let { hero ->
            item(key = "hero") { HeroBanner(hero) { onOpen(hero) } }
        }

        if (vm.history.isNotEmpty()) {
            item(key = "resume") {
                PosterRow("Reprendre", vm.history.map(WatchEntry::toMovie), onOpen)
            }
        }

        items(rows, key = { it.first }) { (title, movies) ->
            PosterRow(title, movies, onOpen)
        }
    }
}

@Composable
private fun HeroBanner(movie: Movie, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = movie.backgroundImageUrl,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color(0x55000000),
                    1f to MaterialTheme.colorScheme.background
                )
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                movie.title.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onClick) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Voir la fiche")
            }
        }
    }
}

/** Entrée d'historique → Movie (le badge SxxEyy prend la place de l'année). */
fun WatchEntry.toMovie(): Movie = Movie(
    id = tmdbId,
    tmdbId = tmdbId,
    isTv = isTv,
    title = title,
    cardImageUrl = posterUrl,
    backgroundImageUrl = backdropUrl,
    year = if (isTv && lastSeason != null && lastEpisode != null)
        "S%02dE%02d".format(lastSeason, lastEpisode) else null
)
