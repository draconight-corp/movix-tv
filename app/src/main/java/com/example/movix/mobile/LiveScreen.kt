package com.example.movix.mobile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.movix.LiveTv
import com.example.movix.data.LiveTvChannel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveScreen(vm: MobileViewModel) {
    LaunchedEffect(Unit) { vm.loadLive() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var openingId by remember { mutableStateOf<String?>(null) }

    val open: (LiveTvChannel) -> Unit = open@{ ch ->
        if (openingId != null) return@open
        openingId = ch.id
        scope.launch {
            try {
                when (val r = LiveTv.open(ctx, ch)) {
                    is LiveTv.Opening.Ready -> ctx.startActivity(r.intent)
                    is LiveTv.Opening.Failed -> Toast.makeText(ctx, r.message, Toast.LENGTH_LONG).show()
                }
            } finally {
                openingId = null
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SectionTitle("TV en direct", Modifier.padding(top = 8.dp))
        val catalogs = vm.liveCatalogs
        when {
            catalogs == null -> CenteredLoading()
            catalogs.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Impossible de charger la TV en direct.")
                TextButton(onClick = { vm.loadLive(force = true) }) { Text("Réessayer") }
            }
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(catalogs, key = { it.id }) { c ->
                        FilterChip(
                            selected = vm.selectedCatalog == c.id,
                            onClick = { vm.selectCatalog(c.id) },
                            label = { Text(LiveTv.shortLabel(c)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MovixRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                val channels = vm.selectedCatalog?.let { vm.channels[it] }
                when {
                    channels == null -> CenteredLoading()
                    channels.isEmpty() -> CenteredMessage("Aucune chaîne dans cette catégorie pour le moment.")
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(channels, key = { it.id }) { ch ->
                            ChannelCard(ch, loading = openingId == ch.id) { open(ch) }
                        }
                    }
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)

@Composable
private fun ChannelCard(ch: LiveTvChannel, loading: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val image = ch.logo ?: ch.poster
            if (!image.isNullOrBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = ch.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            } else {
                Text(
                    ch.name.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }
            if (ch.isLive == true) {
                Text(
                    "● DIRECT",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(MovixRed, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            if (loading) {
                Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        Text(
            ch.name.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        val sub = when {
            ch.isLive != true && ch.timestamp != null -> timeFormat.format(Date(ch.timestamp))
            else -> ch.description
        }
        if (!sub.isNullOrBlank()) {
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
