package com.babyschedule.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babyschedule.data.DiaperType
import com.babyschedule.data.FeedingType
import com.babyschedule.data.MilkType
import com.babyschedule.data.durationMs
import com.babyschedule.data.totalSeconds
import com.babyschedule.util.defaultTimeFor
import com.babyschedule.util.formatDayLabel
import com.babyschedule.util.formatDuration
import com.babyschedule.util.formatMinutes
import com.babyschedule.util.formatTime
import java.time.LocalDate
import kotlin.math.roundToInt

enum class Category(val title: String) {
    FEEDS("Feeds"),
    BREAST("Breastfeeding"),
    BOTTLE("Bottle"),
    DIAPERS("Diapers"),
    SLEEP("Sleep"),
}

/** Breakdown of one category (feeds, diapers, …) for the day selected in History. */
@Composable
fun DayDetailScreen(vm: BabyViewModel, ui: UiActions, category: Category, onClose: () -> Unit) {
    val selected by vm.selectedDay.collectAsStateWithLifecycle()
    val day by vm.historyData.collectAsStateWithLifecycle()
    val isToday = selected == LocalDate.now()
    BackHandler(onBack = onClose)

    val events = day.timeline().filter { e ->
        when (category) {
            Category.FEEDS -> e is TimelineEvent.FeedingEvent
            Category.BREAST -> e is TimelineEvent.FeedingEvent && e.feeding.type == FeedingType.BREAST
            Category.BOTTLE -> e is TimelineEvent.FeedingEvent && e.feeding.type == FeedingType.BOTTLE
            Category.DIAPERS -> e is TimelineEvent.DiaperEvent
            Category.SLEEP -> e is TimelineEvent.SleepEvent
        }
    }
    val time = defaultTimeFor(selected)
    val (addLabel, addRequest) = when (category) {
        Category.FEEDS, Category.BREAST -> "Add past breastfeed" to DialogRequest.newBreastFeeding(time)
        Category.BOTTLE -> "Add bottle feed" to DialogRequest.newBottle(time)
        Category.DIAPERS -> "Add diaper" to DialogRequest.newDiaper(time)
        Category.SLEEP -> "Add sleep" to DialogRequest.newSleep(time)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category.title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.selectDay(selected.minusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                    Text(
                        formatDayLabel(selected),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { vm.selectDay(selected.plusDays(1)) }, enabled = !isToday) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                    }
                }
            }
            item {
                SectionCard(title = null) {
                    breakdown(category, day).forEachIndexed { i, (label, value) ->
                        if (i > 0) HorizontalDivider()
                        DetailRow(label, value, bold = i == 0)
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { ui.openDialog(addRequest) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  $addLabel")
                }
            }
            if (events.isEmpty()) {
                item {
                    Text(
                        "Nothing logged.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                item { Text("Entries · tap to edit", style = MaterialTheme.typography.titleSmall) }
                items(events, key = { it.key }) { event ->
                    TimelineRow(event) { ui.openDialog(event.editRequest()) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, bold: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

private fun gapText(times: List<Long>, pick: (List<Long>) -> Long?): String {
    val gaps = times.sorted().zipWithNext { a, b -> b - a }
    return pick(gaps)?.let { formatDuration(it / 1000) } ?: "—"
}

private fun percent(part: Int, total: Int): String =
    if (total == 0) "" else " (${(part * 100f / total).roundToInt()}%)"

/** The label/value lines shown at the top of the breakdown; the first is the headline. */
private fun breakdown(category: Category, day: DayData): List<Pair<String, String>> {
    val s = day.summary
    return when (category) {
        Category.FEEDS -> {
            val starts = day.feedings.map { it.startTime }
            listOf(
                "Total feeds" to "${s.feeds}",
                "Breastfeeds" to "${s.breastFeeds}",
                "Bottle feeds" to "${s.bottleFeeds}",
                "Average time between" to gapText(starts) { g -> if (g.isEmpty()) null else g.average().toLong() },
                "Longest time between" to gapText(starts) { g -> g.maxOrNull() },
                "First feed" to (starts.minOrNull()?.let { formatTime(it) } ?: "—"),
                "Last feed" to (starts.maxOrNull()?.let { formatTime(it) } ?: "—"),
            )
        }
        Category.BREAST -> {
            val sessions = day.feedings.filter { it.type == FeedingType.BREAST }
            val left = sessions.sumOf { it.leftSeconds }
            val right = sessions.sumOf { it.rightSeconds }
            listOf(
                "Total time" to formatDuration((left + right).toLong()),
                "Left" to formatDuration(left.toLong()) + percent(left, left + right),
                "Right" to formatDuration(right.toLong()) + percent(right, left + right),
                "Sessions" to "${sessions.size}",
                "Average session" to (if (sessions.isEmpty()) "—" else formatDuration((left + right).toLong() / sessions.size)),
                "Longest session" to (sessions.maxOfOrNull { it.totalSeconds }?.let { formatDuration(it.toLong()) } ?: "—"),
            )
        }
        Category.BOTTLE -> {
            val bottles = day.feedings.filter { it.type == FeedingType.BOTTLE }
            val total = bottles.sumOf { it.amountMl ?: 0 }
            fun ml(type: MilkType) = bottles.filter { it.milkType == type }.sumOf { it.amountMl ?: 0 }
            listOf(
                "Total" to "$total ml",
                "Bottles" to "${bottles.size}",
                "Average per bottle" to (if (bottles.isEmpty()) "—" else "${total / bottles.size} ml"),
                "Formula" to "${ml(MilkType.FORMULA)} ml",
                "Breast milk" to "${ml(MilkType.BREAST_MILK)} ml",
            )
        }
        Category.DIAPERS -> listOf(
            "Total changes" to "${s.diapers}",
            "Wet only" to "${s.wet}",
            "Dirty only" to "${s.dirty}",
            "Wet + dirty" to "${s.mixed}",
            "All wet" to "${s.wetTotal}",
            "All dirty" to "${s.dirtyTotal}",
            "Last change" to (day.diapers.maxOfOrNull { it.time }?.let { formatTime(it) } ?: "—"),
        ).let { rows -> if (day.diapers.any { it.type == DiaperType.MIXED }) rows else rows.filterNot { it.first.startsWith("All ") } }
        Category.SLEEP -> {
            val durations = day.sleeps.map { it.durationMs }
            listOf(
                "Total sleep" to formatMinutes(s.sleepMin),
                "Sleeps" to "${day.sleeps.size}",
                "Longest" to (durations.maxOrNull()?.let { formatDuration(it / 1000) } ?: "—"),
                "Average" to (if (durations.isEmpty()) "—" else formatDuration(durations.average().toLong() / 1000)),
            )
        }
    }
}
