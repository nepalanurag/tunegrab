package com.tunegrab.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tunegrab.app.AppSettings
import com.tunegrab.app.player.EqualizerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Equalizer settings, styled as a graphic EQ: vertical faders per band,
 * a master on/off card, and a scrollable preset strip. Drives the
 * platform audio effect attached to the library player's audio session
 * (see [EqualizerController]); changes apply live while music plays.
 *
 * The audio engine is untouched: band levels are still stored as
 * integer dB in [AppSettings] (x100 internally) and presets behave
 * exactly as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val info = remember { EqualizerController.probe() }
    val scope = rememberCoroutineScope()
    val enabled by AppSettings.eqEnabled.collectAsState()
    val preset by AppSettings.eqPreset.collectAsState()
    val bands by AppSettings.eqBands.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Equalizer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (info == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Equalizer isn't supported on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        val presetName = info.presetNames.getOrNull(preset) ?: "Custom"
        val status = if (enabled) "On · $presetName" else "Off"

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Master on/off card.
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Equalizer",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { AppSettings.setEqEnabled(it) },
                        )
                    }
                }
            }

            // Presets: one scrollable strip instead of a wrapping grid.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Presets",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = preset == -1,
                                onClick = { AppSettings.setEqPreset(-1) },
                                label = { Text("Custom") },
                                enabled = enabled,
                            )
                        }
                        items(info.presetNames.size) { index ->
                            FilterChip(
                                selected = preset == index,
                                onClick = {
                                    // Read the preset's curve off a throwaway
                                    // effect so the faders move to match
                                    // what you hear.
                                    scope.launch(Dispatchers.Default) {
                                        val levels =
                                            EqualizerController.presetBandLevels(index)
                                        if (levels != null) AppSettings.applyEqPreset(index, levels)
                                        else AppSettings.setEqPreset(index)
                                    }
                                },
                                label = { Text(info.presetNames[index]) },
                                enabled = enabled,
                            )
                        }
                    }
                }
            }

            // Bands: graphic-EQ faders.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (preset >= 0) "Bands · move a fader to customize"
                            else "Bands",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                AppSettings.setEqBands(List(info.bandCount) { 0 })
                            },
                            enabled = enabled,
                        ) {
                            Text("Reset")
                        }
                    }
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Top,
                        ) {
                            items(info.bandCount) { band ->
                                val db = (bands.getOrNull(band) ?: 0) / 100
                                BandFader(
                                    freqLabel = info.bandLabels.getOrElse(band) { "" },
                                    db = db,
                                    minDb = info.minDb,
                                    maxDb = info.maxDb,
                                    enabled = enabled,
                                    onDbChange = { newDb ->
                                        val newLevels = (0 until info.bandCount).map { b ->
                                            if (b == band) newDb * 100
                                            else bands.getOrNull(b) ?: 0
                                        }
                                        AppSettings.setEqBands(newLevels)
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        "Start with a preset, then move any fader to fine-tune. It switches to Custom.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * One vertical fader: tap or drag anywhere on the track to set the
 * band level. Fill grows from the 0 dB center line so boosts and cuts
 * read at a glance.
 */
@Composable
private fun BandFader(
    freqLabel: String,
    db: Int,
    minDb: Int,
    maxDb: Int,
    enabled: Boolean,
    onDbChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var trackHeightPx by remember { mutableStateOf(0f) }
    val range = max(1, maxDb - minDb)

    fun yToDb(y: Float): Int {
        val h = trackHeightPx
        if (h <= 0f) return db
        val frac = (y / h).coerceIn(0f, 1f)
        return (maxDb - frac * range).roundToInt()
    }

    val latestYToDb by rememberUpdatedState(::yToDb)
    val latestOnDb by rememberUpdatedState(onDbChange)

    val valueColor = if (db == 0) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val fillColor = MaterialTheme.colorScheme.primary
    val thumbColor = MaterialTheme.colorScheme.primary
    val gripColor = MaterialTheme.colorScheme.onPrimary
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Column(
        modifier = modifier
            .width(64.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .semantics { contentDescription = "$freqLabel equalizer band, $db decibels" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            formatDb(db),
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
        )
        Box(
            modifier = Modifier
                .height(168.dp)
                .width(56.dp)
                .onSizeChanged { trackHeightPx = it.height.toFloat() }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        latestOnDb(latestYToDb(down.position.y))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            latestOnDb(latestYToDb(change.position.y))
                            change.consume()
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackW = 6.dp.toPx()
                val cx = size.width / 2f
                val frac = ((maxDb - db).toFloat() / range).coerceIn(0f, 1f)
                val valueY = frac * size.height
                val zeroFrac = (maxDb.toFloat() / range).coerceIn(0f, 1f)
                val zeroY = zeroFrac * size.height

                // Track.
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(cx - trackW / 2f, 0f),
                    size = Size(trackW, size.height),
                    cornerRadius = CornerRadius(trackW / 2f, trackW / 2f),
                )
                // 0 dB center tick.
                val tickHalf = 9.dp.toPx()
                drawLine(
                    color = tickColor,
                    start = Offset(cx - tickHalf, zeroY),
                    end = Offset(cx + tickHalf, zeroY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                // Fill from the center line to the value.
                if (db != 0) {
                    val fillTop = minOf(zeroY, valueY)
                    val fillH = max(abs(zeroY - valueY), trackW)
                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(cx - trackW / 2f, fillTop),
                        size = Size(trackW, fillH),
                        cornerRadius = CornerRadius(trackW / 2f, trackW / 2f),
                    )
                }
                // Thumb handle.
                val thumbW = 34.dp.toPx()
                val thumbH = 20.dp.toPx()
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(cx - thumbW / 2f, valueY - thumbH / 2f),
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
                val gripHalf = 7.dp.toPx()
                drawLine(
                    color = gripColor,
                    start = Offset(cx - gripHalf, valueY),
                    end = Offset(cx + gripHalf, valueY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Text(
            freqLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDb(db: Int): String = if (db > 0) "+${db} dB" else "$db dB"
