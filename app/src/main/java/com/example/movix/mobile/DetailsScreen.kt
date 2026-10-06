package com.example.movix.mobile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.movix.Movie
import com.example.movix.PlayerLauncher
import com.example.movix.data.ApiConfig
import com.example.movix.data.MovixLink
import com.example.movix.data.Repository
import com.example.movix.data.TmdbEpisode
import com.example.movix.data.TmdbItem
import com.example.movix.history.WatchHistory
import kotlinx.coroutines.launch

/** Sources trouvées pour un film / un épisode, en attente du choix. */
private data class SourceChoice(val links: List<MovixLink>, val season: Int?, val episode: Int?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(movie: Movie, onBack: () -> Unit, onPlayed: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var details by remember { mutableStateOf<TmdbItem?>(null) }
    var season by remember { mutableStateOf<Int?>(null) }
    var episodes by remember { mutableStateOf<List<TmdbEpisode>?>(null) }
    var resolving by remember { mutableStateOf<String?>(null) } // libellé en cours de recherche
    var choice by remember { mutableStateOf<SourceChoice?>(null) }
    val history = remember { WatchHistory.forMovie(ctx, movie.tmdbId, movie.isTv) }

    LaunchedEffect(Unit) {
        val d = Repository.details(TmdbItem(id = movie.tmdbId, mediaType = if (movie.isTv) "tv" else "movie"))
        details = d
        if (movie.isTv) {
            val seasons = d.seasons?.filter { it.seasonNumber > 0 }.orEmpty()
            season = history?.lastSeason?.takeIf { s -> seasons.any { it.seasonNumber == s } }
                ?: seasons.firstOrNull()?.seasonNumber
        }
    }
    LaunchedEffect(season) {
        val s = season ?: return@LaunchedEffect
        episodes = null
        episodes = Repository.seasonDetails(movie.tmdbId, s)?.episodes.orEmpty()
    }

    // Movie enrichi par les détails TMDB (pour l'historique et le lecteur)
    val full = remember(details) {
        val d = details
        movie.copy(
            title = movie.title ?: d?.displayTitle,
            description = movie.description ?: d?.overview,
            cardImageUrl = movie.cardImageUrl ?: ApiConfig.posterUrl(d?.posterPath),
            backgroundImageUrl = ApiConfig.backdropUrl(d?.backdropPath) ?: movie.backgroundImageUrl
        )
    }

    fun play(s: Int?, e: Int?) {
        if (resolving != null) return
        resolving = if (s != null && e != null) "S%02dE%02d".format(s, e) else full.title.orEmpty()
        scope.launch {
            val links = PlayerLauncher.resolveSources(full, s, e)
            resolving = null
            when {
                links.isEmpty() -> Toast.makeText(ctx, "Aucune source trouvée pour le moment.", Toast.LENGTH_LONG).show()
                links.size == 1 -> launch(ctx, full, links, 0, s, e, onPlayed)
                else -> choice = SourceChoice(links, s, e)
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { Header(full, details, onBack) }

        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                val label: String
                val action: () -> Unit
                if (movie.isTv) {
                    val hs = history?.lastSeason
                    val he = history?.lastEpisode
                    if (hs != null && he != null) {
                        label = "Reprendre S%02dE%02d".format(hs, he)
                        action = { play(hs, he) }
                    } else {
                        val s = season ?: 1
                        label = "Regarder S%02dE01".format(s)
                        action = { play(s, episodes?.firstOrNull()?.episodeNumber ?: 1) }
                    }
                } else {
                    label = "Regarder"
                    action = { play(null, null) }
                }
                Button(onClick = action, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontWeight = FontWeight.Bold)
                }
                full.description?.takeIf { it.isNotBlank() }?.let { Overview(it) }
            }
        }

        if (movie.isTv) {
            val seasons = details?.seasons?.filter { it.seasonNumber > 0 }.orEmpty()
            if (seasons.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(seasons, key = { it.seasonNumber }) { s ->
                            FilterChip(
                                selected = season == s.seasonNumber,
                                onClick = { season = s.seasonNumber },
                                label = { Text("Saison ${s.seasonNumber}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MovixRed,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
            val eps = episodes
            if (eps == null && season != null) {
                item { CenteredLoading(Modifier.height(160.dp)) }
            } else if (eps != null) {
                items(eps, key = { "${it.seasonNumber}-${it.episodeNumber}" }) { ep ->
                    val lastSeen = history?.lastSeason == ep.seasonNumber && history.lastEpisode == ep.episodeNumber
                    EpisodeRow(ep, lastSeen) { play(ep.seasonNumber, ep.episodeNumber) }
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
    }

    resolving?.let { what ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Recherche des sources…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(what)
                }
            }
        )
    }

    choice?.let { c ->
        ModalBottomSheet(onDismissRequest = { choice = null }) {
            Text(
                "${c.links.size} sources",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                "Si une source ne marche pas, reviens et essaie la suivante.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(Modifier.navigationBarsPadding()) {
                c.links.withIndex().groupBy { it.value.category }.forEach { (category, group) ->
                    item(key = "h-$category") {
                        Text(
                            category,
                            color = MovixRed,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
                        )
                    }
                    items(group, key = { it.index }) { (idx, link) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    choice = null
                                    launch(ctx, full, c.links, idx, c.season, c.episode, onPlayed)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(link.displayName(), modifier = Modifier.weight(1f))
                            if (PlayerLauncher.isDirectStream(link.bestUrl())) {
                                Text("natif", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

private fun launch(
    ctx: android.content.Context,
    m: Movie,
    links: List<MovixLink>,
    idx: Int,
    season: Int?,
    episode: Int?,
    onPlayed: () -> Unit
) {
    WatchHistory.record(ctx, m, season, episode)
    onPlayed()
    ctx.startActivity(PlayerLauncher.intentFor(ctx, m, links, idx, season, episode))
}

@Composable
private fun Header(movie: Movie, details: TmdbItem?, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            AsyncImage(
                model = movie.backgroundImageUrl ?: movie.cardImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x66000000),
                        0.4f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background
                    )
                )
            )
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color(0x80000000), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
        }
    }
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            // L'affiche chevauche le bas de l'image de fond (sans laisser de vide dessous)
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                val overlap = 40.dp.roundToPx()
                layout(p.width, p.height - overlap) { p.place(0, -overlap) }
            },
        verticalAlignment = Alignment.Bottom
    ) {
        AsyncImage(
            model = movie.cardImageUrl,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(100.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                movie.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                (details?.displayDate ?: movie.year)?.take(4),
                if (movie.isTv) details?.numberOfSeasons?.let { "$it saison" + if (it > 1) "s" else "" } else details?.runtime?.takeIf { it > 0 }?.let { "${it / 60}h%02d".format(it % 60) },
                (details?.voteAverage ?: movie.rating)?.takeIf { it > 0 }?.let { "★ %.1f".format(it) }
            ).joinToString("  •  ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            details?.genres?.takeIf { it.isNotEmpty() }?.let { g ->
                Text(
                    g.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun Overview(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(top = 12.dp)
            .clickable { expanded = !expanded }
    )
}

@Composable
private fun EpisodeRow(ep: TmdbEpisode, lastSeen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(130.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            ep.stillPath?.let {
                AsyncImage(
                    model = ApiConfig.posterUrl(it, "w300"),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.background(Color(0x80000000), CircleShape).padding(4.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${ep.episodeNumber}. ${ep.name.orEmpty()}",
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (lastSeen) {
                Text("Dernier vu", style = MaterialTheme.typography.labelSmall, color = MovixRed)
            }
            ep.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
