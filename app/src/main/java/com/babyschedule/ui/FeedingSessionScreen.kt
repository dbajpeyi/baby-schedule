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
import com.babyschedule.domain.SessionDraft
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
 * Live breastfeeding timer (secondary to logging sessions afterwards): choose the
 * starting side, time it with side switches, then "End feeding" opens the session
 * form pre-filled so the diaper, weights and sleep can be added.
 */
@Composable
fun FeedingSessionScreen(vm: BabyViewModel, ui: UiActions, onClose: () -> Unit, onFinish: (SessionDraft) -> Unit) {
    val timer by vm.timer.collectAsStateWithLifecycle()
    val lastBreast by vm.lastBreastFeeding.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feeding timer") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
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
            if (!timer.isActive) {
                ChooseSide(
                    suggested = StatsCalculator.suggestedSide(lastBreast),
                    onChoose = { side, weight -> vm.startFeeding(side, weight) },
                )
            } else {
                RunningSession(
                    timer = timer,
                    onSwitch = vm::switchSide,
                    onPause = vm::pauseTimer,
                    onResume = vm::resumeTimer,
                    onEnd = {
                        vm.pauseTimer()
                        onFinish(SessionDraft.fromTimer(vm.timer.value, System.currentTimeMillis()))
                    },
                )
                TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Discard this feeding", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this feeding?") },
            text = { Text("The timer is reset and nothing is saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    vm.discardTimer()
                    onClose()
                    ui.message("Feeding discarded")
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep") } },
        )
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
        Text("End feeding and log session", style = MaterialTheme.typography.titleMedium)
    }
    Text(
        "You can leave this screen; the timer keeps running.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
