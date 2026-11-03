package com.babyschedule.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babyschedule.data.Diaper
import com.babyschedule.data.DiaperType
import com.babyschedule.data.FeedingTimer
import com.babyschedule.data.Side
import com.babyschedule.domain.StatsCalculator
import com.babyschedule.ui.theme.SeriesColors
import com.babyschedule.util.formatClock
import com.babyschedule.util.formatDuration
import com.babyschedule.util.formatTime
import com.babyschedule.util.formatWeight
import com.babyschedule.util.formatWeightChange

private fun sideColor(side: Side) = if (side == Side.LEFT) SeriesColors.Left else SeriesColors.Right

/**
 * A full-screen breastfeeding session: choose the starting side, time it
 * (switching sides as needed), then review and save it as one feed.
 */
@Composable
fun FeedingSessionScreen(vm: BabyViewModel, ui: UiActions, onClose: () -> Unit) {
    val timer by vm.timer.collectAsStateWithLifecycle()
    val lastBreast by vm.lastBreastFeeding.collectAsStateWithLifecycle()
    var reviewing by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        if (reviewing) reviewing = false else onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (reviewing) "Review feeding" else "Breastfeeding") },
                navigationIcon = {
                    IconButton(onClick = { if (reviewing) reviewing = false else onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                !timer.isActive -> ChooseSide(
                    suggested = StatsCalculator.suggestedSide(lastBreast),
                    onChoose = { side, weight -> vm.startFeeding(side, weight) },
                )
                !reviewing -> RunningSession(
                    timer = timer,
                    onSwitch = vm::switchSide,
                    onPause = vm::pauseTimer,
                    onResume = vm::resumeTimer,
                    onEnd = {
                        vm.pauseTimer()
                        reviewing = true
                    },
                )
                else -> ReviewSession(
                    timer = timer,
                    onSave = { weightAfter, diaper, notes ->
                        ui.launch {
                            val saved = vm.saveSession(weightAfter, diaper, notes)
                            reviewing = false
                            onClose()
                            if (saved != null) ui.undoable("Feeding saved") { vm.undoSession(saved) }
                        }
                    },
                    onContinue = {
                        vm.resumeTimer()
                        reviewing = false
                    },
                    onDiscard = {
                        vm.discardTimer()
                        reviewing = false
                        onClose()
                        ui.message("Feeding discarded")
                    },
                )
            }
        }
    }
}

@Composable
private fun ChooseSide(suggested: Side, onChoose: (Side, Int?) -> Unit) {
    var weightBefore by rememberSaveable { mutableStateOf("") }
    Text("Weight before feeding (optional)", style = MaterialTheme.typography.titleMedium)
    NumberField(weightBefore, { weightBefore = it }, "Weight before", "g", decimal = false)
    Spacer(Modifier.height(8.dp))
    Text("Which side are you starting on?", style = MaterialTheme.typography.titleMedium)
    Text(
        "Suggested: ${suggested.label} (based on the last feed)",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Side.entries.forEach { side ->
            val isSuggested = side == suggested
            Surface(
                onClick = { onChoose(side, weightBefore.toIntOrNull()?.takeIf { it > 0 }) },
                modifier = Modifier.weight(1f).height(140.dp),
                shape = MaterialTheme.shapes.large,
                color = if (isSuggested) sideColor(side).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                border = if (isSuggested) BorderStroke(2.dp, sideColor(side)) else null,
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(side.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = sideColor(side))
                    if (isSuggested) Text("Suggested", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun RunningSession(
    timer: FeedingTimer,
    onSwitch: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
) {
    val now by rememberNow()
    val active = timer.activeSide
    val current = active ?: timer.lastSide ?: Side.LEFT

    Text(
        if (active != null) "Feeding on ${active.label}" else "Paused",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (active != null) sideColor(active) else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        formatClock(timer.totalAt(now)),
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Started at ${formatTime(timer.sessionStart ?: now)}",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Side.entries.forEach { side ->
            val running = active == side
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                color = if (running) sideColor(side).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                border = if (running) BorderStroke(2.dp, sideColor(side)) else null,
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(side.label, style = MaterialTheme.typography.titleMedium, color = sideColor(side), fontWeight = FontWeight.Bold)
                    Text(formatClock(timer.sideAt(side, now)), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }

    SessionTimeline(timer, now)

    Button(onClick = onSwitch, modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Text("Switch to ${current.other().label}", style = MaterialTheme.typography.titleMedium)
    }
    OutlinedButton(
        onClick = if (active != null) onPause else onResume,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(if (active != null) "Pause" else "Resume ${current.label}", style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("End feeding", style = MaterialTheme.typography.titleMedium)
    }
    Text(
        "You can leave this screen; the timer keeps running.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReviewSession(
    timer: FeedingTimer,
    onSave: (Int?, Diaper?, String) -> Unit,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
) {
    val now by rememberNow()
    var weightAfter by rememberSaveable { mutableStateOf("") }
    var diaperChanged by rememberSaveable { mutableStateOf(false) }
    var diaperType by rememberSaveable { mutableStateOf(DiaperType.MIXED) }
    var color by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val start = timer.sessionStart ?: now
    val end = timer.endTime(now)
    val before = timer.weightBeforeGrams
    val after = weightAfter.toIntOrNull()?.takeIf { it > 0 }

    SectionCard(title = "${formatTime(start)} – ${formatTime(end)} · ${formatDuration(timer.totalAt(now) / 1000)}") {
        SessionTimeline(timer, now)
        HorizontalDivider()
        ReviewRow("Left total", formatDuration(timer.leftAt(now) / 1000))
        ReviewRow("Right total", formatDuration(timer.rightAt(now) / 1000))
        if (before != null) ReviewRow("Weight before", formatWeight(before))
    }

    Text("Weight after feeding (optional)", style = MaterialTheme.typography.titleMedium)
    NumberField(weightAfter, { weightAfter = it }, "Weight after", "g", decimal = false)
    if (before != null && after != null) {
        Text(
            "Drank ≈ ${formatWeightChange(after - before)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    Text("Diaper changed?", style = MaterialTheme.typography.titleMedium)
    ChoiceChips(listOf(false, true), diaperChanged, { if (it) "Yes" else "No" }, { diaperChanged = it })
    if (diaperChanged) {
        DiaperFields(diaperType, { diaperType = it }, color, { color = it }, amount, { amount = it })
    }

    OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Notes (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = {
            val diaper = if (diaperChanged) {
                Diaper(
                    time = start,
                    type = diaperType,
                    color = if (diaperType != DiaperType.WET) color else "",
                    amount = amount,
                )
            } else null
            onSave(after, diaper, notes)
        },
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        Text("Save feeding", style = MaterialTheme.typography.titleMedium)
    }
    OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("Continue feeding", style = MaterialTheme.typography.titleMedium)
    }
    TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Discard this feeding", color = MaterialTheme.colorScheme.error)
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this feeding?") },
            text = { Text("Nothing will be saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep") } },
        )
    }
}

/** Each side stretch so far, e.g. "Right  1:15 – 1:31  16m". */
@Composable
private fun SessionTimeline(timer: FeedingTimer, now: Long) {
    val segments = timer.segmentsAt(now)
    if (segments.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEachIndexed { i, seg ->
            val running = i == segments.lastIndex && timer.isRunning
            Row(Modifier.fillMaxWidth()) {
                Text(
                    seg.side.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = sideColor(seg.side),
                    modifier = Modifier.width(56.dp),
                )
                Text(
                    "${formatTime(seg.start)} – ${if (running) "now" else formatTime(seg.end)}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(seg.durationMs / 1000), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
