package com.babyschedule.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babyschedule.data.Diaper
import com.babyschedule.data.DiaperType
import com.babyschedule.data.Feeding
import com.babyschedule.data.FeedingTimer
import com.babyschedule.data.FeedingType
import com.babyschedule.data.Profile
import com.babyschedule.data.Side
import com.babyschedule.data.Sleep
import com.babyschedule.data.totalSeconds
import com.babyschedule.domain.StatsCalculator
import com.babyschedule.ui.theme.SeriesColors
import com.babyschedule.util.formatAge
import com.babyschedule.util.formatAgo
import com.babyschedule.util.formatClock
import com.babyschedule.util.formatDuration
import com.babyschedule.util.formatMinutes
import com.babyschedule.util.formatTime

@Composable
fun TodayScreen(vm: BabyViewModel, ui: UiActions, onSeeHistory: () -> Unit) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val sleepStart by vm.sleepStart.collectAsStateWithLifecycle()
    val day by vm.todayData.collectAsStateWithLifecycle()
    val lastFeeding by vm.lastFeeding.collectAsStateWithLifecycle()
    val lastBreast by vm.lastBreastFeeding.collectAsStateWithLifecycle()
    val lastDiaper by vm.lastDiaper.collectAsStateWithLifecycle()
    val lastSleep by vm.lastSleep.collectAsStateWithLifecycle()
    val events = day.timeline()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!profile.isSet) {
            item { ProfilePrompt { ui.openDialog(DialogRequest.EditProfile) } }
        } else {
            item { BabyHeader(profile) }
        }
        item {
            BreastfeedingCard(
                timer = timer,
                lastBreast = lastBreast,
                onTapSide = vm::tapSide,
                onPause = vm::pauseTimer,
                onFinish = {
                    ui.launch {
                        val result = vm.finishTimer()
                        if (result != null) {
                            ui.undoable("Feeding saved") { vm.undoFinish(result.first, result.second) }
                        }
                    }
                },
                onDiscard = vm::discardTimer,
                onAddManual = { ui.openDialog(DialogRequest.newBreastFeeding(System.currentTimeMillis())) },
            )
        }
        item {
            QuickLogCard(
                onDiaper = { type ->
                    ui.launch {
                        val saved = vm.logDiaper(type)
                        ui.undoable("${type.label} diaper logged") { vm.delete(saved) }
                    }
                },
                onBottle = { ui.openDialog(DialogRequest.newBottle(System.currentTimeMillis())) },
                onWeight = { ui.openDialog(DialogRequest.newGrowth(System.currentTimeMillis())) },
            )
        }
        item {
            SleepCard(
                sleepStart = sleepStart,
                lastSleep = lastSleep,
                onStart = vm::startSleep,
                onStop = {
                    ui.launch {
                        val saved = vm.stopSleep()
                        if (saved != null) ui.undoable("Sleep saved") { vm.delete(saved) }
                    }
                },
                onCancel = vm::cancelSleep,
                onAddManual = { ui.openDialog(DialogRequest.newSleep(System.currentTimeMillis())) },
            )
        }
        item { SinceLastCard(lastFeeding, lastDiaper, lastSleep, sleepStart) }
        item { TodaySummaryCard(day) }
        if (events.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Today's log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSeeHistory) { Text("History") }
                }
            }
            items(events.take(15), key = { it.key }) { event ->
                TimelineRow(event) { ui.openDialog(event.editRequest()) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ProfilePrompt(onClick: () -> Unit) {
    SectionCard(title = "Welcome!") {
        Text("Add your baby's name, date of birth and birth weight to see their age and growth progress.")
        Button(onClick = onClick) { Text("Set up profile") }
    }
}

@Composable
private fun BabyHeader(profile: Profile) {
    Column {
        Text(
            profile.name.ifBlank { "Baby" },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        profile.birthDate?.let {
            Text(formatAge(it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BreastfeedingCard(
    timer: FeedingTimer,
    lastBreast: Feeding?,
    onTapSide: (Side) -> Unit,
    onPause: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onAddManual: () -> Unit,
) {
    val now by rememberNow()
    var confirmDiscard by remember { mutableStateOf(false) }
    val suggested = StatsCalculator.suggestedSide(lastBreast)

    SectionCard(
        title = "Breastfeeding",
        action = {
            if (timer.isActive) {
                Text(
                    formatClock(timer.totalAt(now)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) {
        val subtitle = when {
            timer.isActive && timer.isRunning ->
                "Started ${formatTime(timer.sessionStart ?: now)} · tap other side to switch"
            timer.isActive -> "Paused"
            lastBreast != null -> {
                val sides = buildList {
                    if (lastBreast.leftSeconds > 0) add("L ${formatDuration(lastBreast.leftSeconds.toLong())}")
                    if (lastBreast.rightSeconds > 0) add("R ${formatDuration(lastBreast.rightSeconds.toLong())}")
                }.joinToString(" · ")
                "Last: $sides, ${formatAgo(lastBreast.startTime, now)}"
            }
            else -> "Tap Left or Right to start"
        }
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Side.entries.forEach { side ->
                SideButton(
                    side = side,
                    elapsedMs = timer.sideAt(side, now),
                    running = timer.activeSide == side,
                    suggested = !timer.isActive && side == suggested,
                    onClick = { onTapSide(side) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (timer.isActive) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (timer.isRunning) {
                    OutlinedButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pause")
                    }
                }
                Button(onClick = onFinish) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Finish")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmDiscard = true }) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Discard")
                }
            }
        } else {
            TextButton(onClick = onAddManual) { Text("Log a past feeding") }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this feeding?") },
            text = { Text("The timer will be reset and nothing will be saved.") },
            confirmButton = {
                TextButton(onClick = { onDiscard(); confirmDiscard = false }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun SideButton(
    side: Side,
    elapsedMs: Long,
    running: Boolean,
    suggested: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (side == Side.LEFT) SeriesColors.Left else SeriesColors.Right
    val container = if (running) accent.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(
        onClick = onClick,
        modifier = modifier.height(132.dp),
        shape = MaterialTheme.shapes.large,
        color = container,
        border = if (running || suggested) BorderStroke(2.dp, accent) else null,
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(side.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
            Text(formatClock(elapsedMs), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    running -> "Running"
                    suggested -> "Start here"
                    elapsedMs > 0 -> "Paused"
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickLogCard(
    onDiaper: (DiaperType) -> Unit,
    onBottle: () -> Unit,
    onWeight: () -> Unit,
) {
    SectionCard(title = "Quick log") {
        Text("Diaper", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickButton("Wet", Icons.Filled.WaterDrop, Modifier.weight(1f)) { onDiaper(DiaperType.WET) }
            QuickButton("Dirty", Icons.Filled.BabyChangingStation, Modifier.weight(1f)) { onDiaper(DiaperType.DIRTY) }
            QuickButton("Both", Icons.Filled.BabyChangingStation, Modifier.weight(1f)) { onDiaper(DiaperType.MIXED) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickButton("Bottle", Icons.Filled.LocalDrink, Modifier.weight(1f), onBottle)
            QuickButton("Weight", Icons.Filled.MonitorWeight, Modifier.weight(1f), onWeight)
        }
    }
}

@Composable
private fun QuickButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text)
    }
}

@Composable
private fun SleepCard(
    sleepStart: Long?,
    lastSleep: Sleep?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onAddManual: () -> Unit,
) {
    val now by rememberNow()
    SectionCard(
        title = "Sleep",
        action = {
            if (sleepStart != null) {
                Text(
                    formatClock(now - sleepStart),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SeriesColors.Sleep,
                )
            }
        },
    ) {
        if (sleepStart != null) {
            Text("Asleep since ${formatTime(sleepStart)}", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = SeriesColors.Sleep),
                ) {
                    Icon(Icons.Filled.WbSunny, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Woke up")
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        } else {
            Text(
                lastSleep?.let { "Last woke up ${formatAgo(it.endTime, now)}" } ?: "No sleep logged yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onStart) {
                    Icon(Icons.Filled.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fell asleep")
                }
                TextButton(onClick = onAddManual) { Text("Log past sleep") }
            }
        }
    }
}

@Composable
private fun SinceLastCard(lastFeeding: Feeding?, lastDiaper: Diaper?, lastSleep: Sleep?, sleepStart: Long?) {
    val now by rememberNow(30_000)
    SectionCard(title = "Last time") {
        LastLine(
            "Feed",
            lastFeeding?.let { f ->
                val what = if (f.type == FeedingType.BREAST) "breast ${formatDuration(f.totalSeconds.toLong())}"
                else "bottle ${f.amountMl ?: 0} ml"
                "${formatAgo(f.startTime, now)} ($what)"
            },
        )
        LastLine("Diaper", lastDiaper?.let { "${formatAgo(it.time, now)} (${it.type.label.lowercase()})" })
        LastLine(
            "Sleep",
            when {
                sleepStart != null -> "sleeping now"
                lastSleep != null -> "woke up ${formatAgo(lastSleep.endTime, now)}"
                else -> null
            },
        )
    }
}

@Composable
private fun LastLine(label: String, value: String?) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp))
        Text(value ?: "—", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TodaySummaryCard(day: DayData) {
    val s = day.summary
    SectionCard(title = "Today so far") {
        TileGrid(
                { m -> StatTile("Feeds", s.feeds.toString(), m, sub = "${s.breastFeeds} breast · ${s.bottleFeeds} bottle", accent = SeriesColors.Left) },
                { m ->
                    StatTile(
                        "Breastfeeding", formatMinutes(s.breastMin), m,
                        sub = "L ${formatMinutes(s.leftMin)} · R ${formatMinutes(s.rightMin)}",
                        accent = SeriesColors.Right,
                    )
                },
                { m -> StatTile("Bottle", "${s.bottleMl} ml", m, accent = SeriesColors.Bottle) },
                { m -> StatTile("Diapers", s.diapers.toString(), m, sub = "${s.wetTotal} wet · ${s.dirtyTotal} dirty", accent = SeriesColors.Wet) },
                { m -> StatTile("Sleep", formatMinutes(s.sleepMin), m, accent = SeriesColors.Sleep) },
        )
    }
}
