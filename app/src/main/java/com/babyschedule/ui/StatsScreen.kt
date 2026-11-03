package com.babyschedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babyschedule.domain.Stats
import com.babyschedule.ui.theme.SeriesColors
import com.babyschedule.util.formatMinutes
import com.babyschedule.util.formatNumber
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEEE", Locale.getDefault())
private val dayOfMonthFormatter = DateTimeFormatter.ofPattern("d", Locale.getDefault())

@Composable
fun StatsScreen(vm: BabyViewModel) {
    val range by vm.statsRange.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ChoiceChips(
                options = listOf(7, 14, 30),
                selected = range,
                label = { "$it days" },
                onSelect = vm::setStatsRange,
            )
        }
        val s = stats
        if (s == null) {
            item {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        } else {
            statsContent(s)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.statsContent(s: Stats) {
    val labels = s.days.map {
        if (s.days.size <= 7) weekdayFormatter.format(it.date) else dayOfMonthFormatter.format(it.date)
    }

    item {
        SectionCard(title = "Average per day") {
            if (s.days.none { it.hasData }) {
                Text(
                    "No entries in this period yet. Start logging feeds, diapers and sleep to see statistics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TileGrid(
                { m -> StatTile("Feeds", formatNumber(s.avgFeedsPerDay), m, sub = "${s.totalFeeds} total", accent = SeriesColors.Left) },
                { m ->
                    StatTile(
                        "Time between feeds",
                        s.avgIntervalMin?.let { formatMinutes(it) } ?: "—",
                        m, accent = SeriesColors.Left,
                    )
                },
                { m ->
                    StatTile(
                        "Left / right",
                        s.leftShare?.let { "${(it * 100).roundToInt()}% / ${100 - (it * 100).roundToInt()}%" } ?: "—",
                        m, accent = SeriesColors.Right,
                    )
                },
                { m ->
                    StatTile(
                        "Food", "${s.avgFoodPerDay.roundToInt()} g", m,
                        sub = s.avgFoodPerFeed?.let { "≈ ${it.roundToInt()} g per feed" },
                        accent = SeriesColors.Weight,
                    )
                },
                { m -> StatTile("Bottle", "${s.avgBottleMlPerDay.roundToInt()} ml", m, accent = SeriesColors.Bottle) },
                { m ->
                    StatTile(
                        "Diapers", formatNumber(s.avgDiapersPerDay), m,
                        sub = "${formatNumber(s.avgWetPerDay)} pee · ${formatNumber(s.avgDirtyPerDay)} poop",
                        accent = SeriesColors.Wet,
                    )
                },
                { m -> StatTile("Sleep", formatMinutes(s.avgSleepHoursPerDay * 60f), m, accent = SeriesColors.Sleep) },
            )
        }
    }

    item {
        SectionCard(title = "Feeds per day") {
            Legend(listOf("Breast" to SeriesColors.Left, "Bottle" to SeriesColors.Bottle))
            StackedBarChart(
                labels = labels,
                values = s.days.map { listOf(it.breastFeeds.toFloat(), it.bottleFeeds.toFloat()) },
                colors = listOf(SeriesColors.Left, SeriesColors.Bottle),
            )
        }
    }

    if (s.days.any { it.foodG > 0 }) {
        item {
            SectionCard(title = "Food per day (g)") {
                Legend(listOf("Breast (weighed)" to SeriesColors.Left, "Bottle" to SeriesColors.Bottle))
                StackedBarChart(
                    labels = labels,
                    values = s.days.map { listOf(it.breastIntakeG.toFloat().coerceAtLeast(0f), it.bottleMl.toFloat()) },
                    colors = listOf(SeriesColors.Left, SeriesColors.Bottle),
                    format = { it.roundToInt().toString() },
                )
                Text(
                    "Breast amounts come from weighing before and after a feed; bottle ml count as grams.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    item {
        SectionCard(title = "Breastfeeding minutes") {
            Legend(listOf("Left" to SeriesColors.Left, "Right" to SeriesColors.Right))
            StackedBarChart(
                labels = labels,
                values = s.days.map { listOf(it.leftMin, it.rightMin) },
                colors = listOf(SeriesColors.Left, SeriesColors.Right),
                format = { it.roundToInt().toString() },
            )
        }
    }

    if (s.days.any { it.bottleMl > 0 }) {
        item {
            SectionCard(title = "Bottle ml") {
                StackedBarChart(
                    labels = labels,
                    values = s.days.map { listOf(it.bottleMl.toFloat()) },
                    colors = listOf(SeriesColors.Bottle),
                    format = { it.roundToInt().toString() },
                )
            }
        }
    }

    item {
        SectionCard(title = "Diapers per day") {
            Legend(listOf("Pee" to SeriesColors.Wet, "Poop" to SeriesColors.Dirty, "Pee + poop" to SeriesColors.Mixed))
            StackedBarChart(
                labels = labels,
                values = s.days.map { listOf(it.wet.toFloat(), it.dirty.toFloat(), it.mixed.toFloat()) },
                colors = listOf(SeriesColors.Wet, SeriesColors.Dirty, SeriesColors.Mixed),
            )
        }
    }

    item {
        SectionCard(title = "Sleep hours") {
            StackedBarChart(
                labels = labels,
                values = s.days.map { listOf(it.sleepMin / 60f) },
                colors = listOf(SeriesColors.Sleep),
                format = { formatNumber(it) },
            )
        }
    }

}
