package com.babyschedule.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babyschedule.data.DiaperOptions
import com.babyschedule.data.DiaperType
import com.babyschedule.data.MilkType
import com.babyschedule.data.Segment
import com.babyschedule.data.Side
import com.babyschedule.domain.FeedKind
import com.babyschedule.domain.SessionDraft
import com.babyschedule.domain.SleepMode
import com.babyschedule.domain.StatsCalculator
import com.babyschedule.ui.theme.SeriesColors
import com.babyschedule.util.formatDuration
import com.babyschedule.util.formatTime
import com.babyschedule.util.formatWeightChange
import com.babyschedule.util.resolveClockTime
import com.babyschedule.util.toZoned

private const val MIN = 60_000L

/**
 * The main way to log: one form for a whole session, filled in after the fact
 * (diaper, weight before, each breast with start/end times, weight after, sleep).
 */
@Composable
fun SessionFormScreen(initial: SessionDraft, vm: BabyViewModel, ui: UiActions, onClose: () -> Unit) {
    var d by remember(initial) { mutableStateOf(initial) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val lastBreast by vm.lastBreastFeeding.collectAsStateWithLifecycle()
    val liveSleepStart by vm.sleepStart.collectAsStateWithLifecycle()
    val liveSleepSession by vm.sleepSessionId.collectAsStateWithLifecycle()
    // "Sleeping now" is offered unless another sleep timer is already running.
    val canSleepNow = liveSleepStart == null || (!d.isNew && liveSleepSession == d.sessionId)

    fun leave() {
        if (d != initial) confirmLeave = true else onClose()
    }
    BackHandler { leave() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (d.isNew) "Log session" else "Edit session") },
                navigationIcon = {
                    IconButton(onClick = { leave() }) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                },
                actions = {
                    TextButton(
                        enabled = d.canSave,
                        onClick = {
                            val draft = d
                            ui.launch {
                                vm.saveSession(draft)
                                onClose()
                                ui.message("Session saved")
                            }
                        },
                    ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DateTimeField("When", d.start) { d = d.shiftedTo(it) }

            SectionCard(title = "Diaper") {
                ChoiceChips(
                    options = listOf<DiaperType?>(null) + DiaperType.entries,
                    selected = d.diaperType,
                    label = { it?.label ?: "No" },
                    onSelect = { d = d.copy(diaperType = it) },
                )
                if (d.diaperType != null) {
                    Text("Amount", style = MaterialTheme.typography.labelMedium)
                    OptionChips(DiaperOptions.amounts, d.diaperAmount) { d = d.copy(diaperAmount = it) }
                    OutlinedTextField(
                        value = d.diaperNotes,
                        onValueChange = { d = d.copy(diaperNotes = it) },
                        label = { Text("Diaper notes (e.g. colour)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionCard(title = "Feeding") {
                ChoiceChips(
                    options = FeedKind.entries,
                    selected = d.feedKind,
                    label = { it.label },
                    onSelect = { kind ->
                        d = if (kind == FeedKind.BREAST && d.segments.isEmpty()) {
                            val side = StatsCalculator.suggestedSide(lastBreast)
                            d.copy(feedKind = kind, segments = listOf(Segment(side, d.start, d.start + 15 * MIN)))
                        } else {
                            d.copy(feedKind = kind)
                        }
                    },
                )
                when (d.feedKind) {
                    FeedKind.BREAST -> BreastSection(d) { d = it }
                    FeedKind.BOTTLE -> {
                        NumberField(
                            d.bottleMl?.toString() ?: "",
                            { d = d.copy(bottleMl = it.toIntOrNull()) },
                            "Amount", "ml", decimal = false,
                        )
                        ChoiceChips(MilkType.entries, d.milkType, { it.label }, { d = d.copy(milkType = it) })
                    }
                    FeedKind.NONE -> {}
                }
            }

            SectionCard(title = "Sleep") {
                ChoiceChips(
                    options = SleepMode.entries.filter { it != SleepMode.NOW || canSleepNow || d.sleepMode == SleepMode.NOW },
                    selected = d.sleepMode,
                    label = { it.label },
                    onSelect = { mode ->
                        val now = System.currentTimeMillis()
                        d = when (mode) {
                            SleepMode.NONE -> d.copy(sleepMode = mode)
                            SleepMode.TIMES -> {
                                val s = d.sleepStart ?: d.feedEnd
                                d.copy(sleepMode = mode, sleepStart = s, sleepEnd = d.sleepEnd ?: (s + 60 * MIN))
                            }
                            SleepMode.NOW -> d.copy(sleepMode = mode, sleepStart = d.sleepStart ?: minOf(now, maxOf(d.feedEnd, d.start)))
                        }
                    },
                )
                val sleepStart = d.sleepStart
                if (d.sleepMode != SleepMode.NONE && sleepStart != null) {
                    LabeledTime("Fell asleep", sleepStart) { h, m ->
                        d = d.copy(sleepStart = resolveClockTime(d.feedEnd, h, m, after = false))
                    }
                }
                val sleepEnd = d.sleepEnd
                if (d.sleepMode == SleepMode.TIMES && sleepStart != null && sleepEnd != null) {
                    LabeledTime("Woke up", sleepEnd) { h, m ->
                        d = d.copy(sleepEnd = resolveClockTime(sleepStart, h, m, after = true))
                    }
                    Text(
                        if (sleepEnd > sleepStart) "Slept ${formatDuration((sleepEnd - sleepStart) / 1000)}" else "Wake-up must be after falling asleep",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sleepEnd > sleepStart) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                if (d.sleepMode == SleepMode.NOW) {
                    Text(
                        "The sleep timer runs until you tap “Woke up” on the Today tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = d.notes,
                onValueChange = { d = d.copy(notes = it) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                enabled = d.canSave,
                onClick = {
                    val draft = d
                    ui.launch {
                        vm.saveSession(draft)
                        onClose()
                        ui.message("Session saved")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save session", style = MaterialTheme.typography.titleMedium) }

            if (!d.isNew) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete session", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Discard changes?") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep editing") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session?") },
            text = { Text("The diaper, feed and sleep in it are deleted too.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val id = d.sessionId
                    ui.launch {
                        val removed = vm.deleteSession(id)
                        onClose()
                        if (removed != null) ui.undoable("Session deleted") { vm.restoreSession(removed) }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BreastSection(d: SessionDraft, onChange: (SessionDraft) -> Unit) {
    NumberField(
        d.weightBefore?.toString() ?: "",
        { onChange(d.copy(weightBefore = it.toIntOrNull()?.takeIf { g -> g > 0 })) },
        "Weight before", "g", decimal = false,
    )

    Text("Sides (tap a side to change it)", style = MaterialTheme.typography.labelMedium)
    d.segments.forEachIndexed { i, seg ->
        SegmentRow(
            seg = seg,
            onSide = { onChange(d.copy(segments = d.segments.replace(i, seg.copy(side = seg.side.other())))) },
            onStart = { h, m ->
                val start = resolveClockTime(seg.start, h, m, after = false)
                val end = if (seg.end > start) seg.end else start + (seg.end - seg.start).coerceAtLeast(MIN)
                onChange(d.copy(segments = d.segments.replace(i, seg.copy(start = start, end = end))))
            },
            onEnd = { h, m ->
                onChange(d.copy(segments = d.segments.replace(i, seg.copy(end = resolveClockTime(seg.start, h, m, after = true)))))
            },
            onRemove = if (d.segments.size > 1) {
                { onChange(d.copy(segments = d.segments.filterIndexed { j, _ -> j != i })) }
            } else null,
        )
    }
    OutlinedButton(
        onClick = {
            val last = d.segments.maxByOrNull { it.end }
            val side = last?.side?.other() ?: Side.LEFT
            val start = last?.end ?: d.start
            onChange(d.copy(segments = d.segments + Segment(side, start, start + 10 * MIN)))
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Add side")
    }
    val total = d.segments.sumOf { it.durationMs }
    Text("Total ${formatDuration(total / 1000)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

    NumberField(
        d.weightAfter?.toString() ?: "",
        { onChange(d.copy(weightAfter = it.toIntOrNull()?.takeIf { g -> g > 0 })) },
        "Weight after", "g", decimal = false,
    )
    d.intakeGrams?.let {
        Text(
            "Drank ≈ ${formatWeightChange(it)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun List<Segment>.replace(i: Int, seg: Segment) = mapIndexed { j, s -> if (j == i) seg else s }

/** "Right  [01:15] – [01:31]  16m  ×" */
@Composable
private fun SegmentRow(
    seg: Segment,
    onSide: () -> Unit,
    onStart: (Int, Int) -> Unit,
    onEnd: (Int, Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val color = if (seg.side == Side.LEFT) SeriesColors.Left else SeriesColors.Right
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onSide, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.width(60.dp)) {
            Text(seg.side.label, color = color, fontWeight = FontWeight.Bold)
        }
        TimeButton(seg.start, onStart, Modifier.weight(1f))
        Text(" – ")
        TimeButton(seg.end, onEnd, Modifier.weight(1f))
        Text(
            if (seg.end > seg.start) formatDuration(seg.durationMs / 1000) else "!",
            style = MaterialTheme.typography.bodySmall,
            color = if (seg.end > seg.start) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.width(44.dp).padding(start = 4.dp),
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Remove side", modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(Modifier.width(36.dp))
        }
    }
}

@Composable
private fun TimeButton(millis: Long, onPick: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = modifier, contentPadding = PaddingValues(horizontal = 6.dp)) {
        Text(formatTime(millis), maxLines = 1)
    }
    if (show) {
        val z = toZoned(millis)
        TimePickerPopup(z.hour, z.minute, onDismiss = { show = false }, onPick = onPick)
    }
}

@Composable
private fun LabeledTime(label: String, millis: Long, onPick: (Int, Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(110.dp))
        TimeButton(millis, onPick, Modifier.width(120.dp))
    }
}
