package com.babyschedule.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babyschedule.data.Feeding
import com.babyschedule.data.FeedingTimer
import com.babyschedule.data.FeedingType
import com.babyschedule.data.Profile
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
fun TodayScreen(
    vm: BabyViewModel,
    ui: UiActions,
    onOpenFeeding: () -> Unit,
    onOpenDetail: (Category) -> Unit,
    onSeeHistory: () -> Unit,
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val sleepStart by vm.sleepStart.collectAsStateWithLifecycle()
    val day by vm.todayData.collectAsStateWithLifecycle()
    val lastFeeding by vm.lastFeeding.collectAsStateWithLifecycle()
    val lastBreast by vm.lastBreastFeeding.collectAsStateWithLifecycle()
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
            FeedingCard(
                timer = timer,
                lastFeeding = lastFeeding,
                lastBreast = lastBreast,
                onOpen = onOpenFeeding,
            )
        }
        item {
            SleepCard(
                sleepStart = sleepStart,
                lastSleep = lastSleep,
                onStart = {
                    vm.startSleep()
                    if (vm.sleepAlert.value.enabled) ui.requestNotifications()
                },
                onStop = {
                    ui.launch {
                        val saved = vm.stopSleep()
                        if (saved != null) ui.undoable("Sleep saved") { vm.delete(saved) }
                    }
                },
                onCancel = vm::cancelSleep,
            )
        }
        item {
            OutlinedButton(
                onClick = { ui.openDialog(DialogRequest.AddMenu(System.currentTimeMillis())) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add bottle, diaper, sleep or weight", style = MaterialTheme.typography.titleSmall)
            }
        }
        item { TodaySummaryCard(day, onOpenDetail) }
        if (events.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Today's log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSeeHistory) { Text("History") }
                }
            }
            items(events.take(10), key = { it.key }) { event ->
                TimelineRow(event) { ui.openDialog(event.editRequest()) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ProfilePrompt(onClick: () -> Unit) {
    SectionCard(title = "Welcome!") {
        Text("Add your baby's name, date of birth and birth weight to see their age and growth.")
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
private fun FeedingCard(
    timer: FeedingTimer,
    lastFeeding: Feeding?,
    lastBreast: Feeding?,
    onOpen: () -> Unit,
) {
    val now by rememberNow()
    SectionCard(title = "Feeding") {
        if (timer.isActive) {
            val active = timer.activeSide
            Text(
                if (active != null) "In progress · ${active.label} side" else "Paused",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                formatClock(timer.totalAt(now)),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Started at ${formatTime(timer.sessionStart ?: now)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Open feeding", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Text(
                lastFeeding?.let { f ->
                    val what = if (f.type == FeedingType.BREAST) "breast, ${formatDuration(f.totalSeconds.toLong())}"
                    else "bottle, ${f.amountMl ?: 0} ml"
                    "Last feed ${formatAgo(f.startTime, now)} ($what)"
                } ?: "No feeds yet today",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Next: start on ${StatsCalculator.suggestedSide(lastBreast).label}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Start breastfeeding", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SleepCard(
    sleepStart: Long?,
    lastSleep: Sleep?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val now by rememberNow()
    SectionCard(title = "Sleep") {
        if (sleepStart != null) {
            Text("Sleeping since ${formatTime(sleepStart)}", style = MaterialTheme.typography.titleMedium, color = SeriesColors.Sleep)
            Text(formatClock(now - sleepStart), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SeriesColors.Sleep),
            ) {
                Text("Woke up", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onCancel) { Text("Started by mistake? Cancel") }
        } else {
            Text(
                lastSleep?.let { "Awake for ${formatDuration((now - it.endTime) / 1000)}" } ?: "No sleep logged yet",
                style = MaterialTheme.typography.bodyLarge,
            )
            FilledTonalButton(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Fell asleep", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(day: DayData, onOpenDetail: (Category) -> Unit) {
    val s = day.summary
    SectionCard(title = "Today so far") {
        SummaryLine("Feeds", "${s.feeds}", "${s.breastFeeds} breast · ${s.bottleFeeds} bottle") { onOpenDetail(Category.FEEDS) }
        SummaryLine(
            "Breast",
            formatMinutes(s.breastMin),
            "Left ${formatMinutes(s.leftMin)} · Right ${formatMinutes(s.rightMin)}" +
                if (s.weighedFeeds > 0) " · drank ≈ ${s.breastIntakeG} g" else "",
        ) { onOpenDetail(Category.BREAST) }
        if (s.bottleMl > 0) SummaryLine("Bottle", "${s.bottleMl} ml", null) { onOpenDetail(Category.BOTTLE) }
        SummaryLine("Diapers", "${s.diapers}", "${s.wetTotal} pee · ${s.dirtyTotal} poop") { onOpenDetail(Category.DIAPERS) }
        SummaryLine("Sleep", formatMinutes(s.sleepMin), null) { onOpenDetail(Category.SLEEP) }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, detail: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp))
        Text(
            detail ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
