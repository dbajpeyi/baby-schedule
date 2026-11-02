package com.babyschedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babyschedule.ui.theme.SeriesColors
import com.babyschedule.util.formatAxisDate
import com.babyschedule.util.formatDateFull
import com.babyschedule.util.formatWeight
import com.babyschedule.util.formatWeightChange
import com.babyschedule.util.toLocalDate
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GrowthScreen(vm: BabyViewModel, ui: UiActions) {
    val entries by vm.growthEntries.collectAsStateWithLifecycle()
    val summary by vm.growthSummary.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Weight & growth", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { ui.openDialog(DialogRequest.newGrowth(System.currentTimeMillis())) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add")
                }
            }
        }

        if (profile.birthWeightGrams == null) {
            item {
                SectionCard(title = null) {
                    Text("Add the birth weight in the baby profile to track change since birth.")
                    TextButton(onClick = { ui.openDialog(DialogRequest.EditProfile) }) { Text("Open profile") }
                }
            }
        }

        val latest = summary.latest
        if (latest != null) {
            item {
                SectionCard(title = "Summary") {
                    TileGrid(
                        { m ->
                            StatTile(
                                "Current weight", formatWeight(latest.grams), m,
                                sub = formatDateFull(toLocalDate(latest.time)), accent = SeriesColors.Weight,
                            )
                        },
                        { m ->
                            StatTile(
                                "Since birth",
                                summary.changeSinceBirth?.let { formatWeightChange(it) } ?: "—",
                                m,
                                sub = summary.percentOfBirth?.let {
                                    String.format(Locale.getDefault(), "%.1f%% of birth weight", it)
                                },
                                accent = SeriesColors.Weight,
                            )
                        },
                        { m ->
                            StatTile(
                                "Gain (last week)",
                                summary.recentGainPerDay?.let { "${formatGain(it * 7)} g/week" } ?: "—",
                                m,
                                sub = summary.recentGainPerDay?.let { "${formatGain(it)} g per day" },
                                accent = SeriesColors.Weight,
                            )
                        },
                    )
                }
            }
        }

        if (summary.points.isNotEmpty()) {
            item {
                SectionCard(title = "Weight (kg)") {
                    TimeLineChart(
                        points = summary.points.map { it.time to it.grams / 1000f },
                        color = SeriesColors.Weight,
                        yFormat = { String.format(Locale.getDefault(), "%.2f", it) },
                        xFormat = { formatAxisDate(toLocalDate(it)) },
                        highlightFirst = summary.points.first().isBirth,
                    )
                    if (summary.points.first().isBirth) {
                        Text(
                            "Circled point is birth weight.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    "No measurements yet. Tap “Add” to log a weight.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item { Text("Measurements", style = MaterialTheme.typography.titleMedium) }
            items(entries.reversed(), key = { it.id }) { g ->
                TimelineRow(TimelineEvent.GrowthEvent(g)) { ui.openDialog(DialogRequest.EditGrowth(g)) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun formatGain(v: Float): String {
    val r = v.roundToInt()
    return if (r > 0) "+$r" else r.toString()
}
