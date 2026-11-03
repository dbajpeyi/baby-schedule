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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.babyschedule.data.Session
import com.babyschedule.data.Sleep
import com.babyschedule.data.totalSeconds
import com.babyschedule.domain.SessionDraft
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
    val sleepSessionId by vm.sleepSessionId.collectAsStateWithLifecycle()
    val day by vm.todayData.collectAsStateWithLifecycle()
    val lastFeeding by vm.lastFeeding.collectAsStateWithLifecycle()
    val lastBreast by vm.lastBreastFeeding.collectAsStateWithLifecycle()
    val lastSleep by vm.lastSleep.collectAsStateWithLifecycle()
    val events = day.timeline(sleepSessionId, sleepStart)
    var attachPrompt by remember { mutableStateOf<Session?>(null) }
    var showMore by rememberSaveable { mutableStateOf(false) }

    fun wokeUp(attachTo: Long?) {
        ui.launch {
            val saved = vm.stopSleep(attachTo)
            if (saved != null) ui.undoable("Sleep saved") { vm.delete(saved) }
        }
    }

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

        // 1. Main action: log what happened.
        item {
            PrimaryButton(
                text = "Log a session",
                subtitle = "Diaper, feeding, weights, sleep",
                icon = Icons.Outlined.EditCalendar,
                onClick = { ui.editDraft(SessionDraft.new(System.currentTimeMillis())) },
                modifier = Modifier.fillMaxWidth().height(72.dp),
            )
        }

        // Running timers stay visible while they run.
        if (timer.isActive) {
            item { LiveFeedingCard(timer, onOpenFeeding) }
        }
        if (sleepStart != null) {
            item {
                LiveSleepCard(
                    sleepStart = sleepStart!!,
                    onWokeUp = {
                        ui.launch {
                            val suggestion = vm.sessionToAttachSleep()
                            if (suggestion != null) attachPrompt = suggestion else wokeUp(null)
                        }
                    },
                    onCancel = vm::cancelSleep,
                )
            }
        }

        // 2. Today so far.
        item { TodaySummaryCard(day, lastFeeding, lastBreast, lastSleep, sleepStart, onOpenDetail) }

        // 3. Secondary actions, tucked away.
        item {
            Column {
                TextButton(onClick = { showMore = !showMore }) {
                    Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Timers and single entries")
                    Icon(
                        if (showMore) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                    )
                }
                if (showMore) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!timer.isActive) {
                                SecondaryButton("Feeding timer", onOpenFeeding, Modifier.weight(1f), icon = Icons.Outlined.Timer)
                            }
                            if (sleepStart == null) {
                                SecondaryButton(
                                    "Fell asleep now",
                                    onClick = {
                                        vm.startSleep()
                                        if (vm.sleepAlert.value.enabled) ui.requestNotifications()
                                    },
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Outlined.Bedtime,
                                )
                            }
                        }
                        SecondaryButton(
                            "Add a single entry (e.g. bottle)",
                            onClick = { ui.openDialog(DialogRequest.AddMenu(System.currentTimeMillis())) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.CheckBox,
                        )
                    }
                }
            }
        }

        if (events.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Today's log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSeeHistory) { Text("History") }
                }
            }
            items(events.take(10), key = { it.key }) { event ->
                TimelineRow(event) { ui.open(event) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    attachPrompt?.let { session ->
        AlertDialog(
            onDismissRequest = { attachPrompt = null },
            title = { Text("Add to the ${formatTime(session.startTime)} session?") },
            text = { Text("This sleep can be logged together with the session just before it.") },
            confirmButton = {
                TextButton(onClick = {
                    attachPrompt = null
                    wokeUp(session.id)
                }) { Text("Add to session") }
            },
            dismissButton = {
                TextButton(onClick = {
                    attachPrompt = null
                    wokeUp(null)
                }) { Text("Keep separate") }
            },
        )
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
private fun StatusLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LiveFeedingCard(timer: FeedingTimer, onOpen: () -> Unit) {
    val now by rememberNow()
    SectionCard(title = "Feeding timer") {
        val active = timer.activeSide
        Text(
            (if (active != null) "${active.label} side · " else "Paused · ") + formatClock(timer.totalAt(now)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text("Started at ${formatTime(timer.sessionStart ?: now)}", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Open feeding timer") }
    }
}

@Composable
private fun LiveSleepCard(sleepStart: Long, onWokeUp: () -> Unit, onCancel: () -> Unit) {
    val now by rememberNow()
    SectionCard(title = "Sleeping") {
        Text(
            "Since ${formatTime(sleepStart)} · ${formatClock(now - sleepStart)}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = SeriesColors.Sleep,
        )
        Button(
            onClick = onWokeUp,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SeriesColors.Sleep),
        ) { Text("Woke up") }
        TextButton(onClick = onCancel) { Text("Started by mistake? Cancel") }
    }
}

@Composable
private fun TodaySummaryCard(
    day: DayData,
    lastFeeding: Feeding?,
    lastBreast: Feeding?,
    lastSleep: Sleep?,
    sleepStart: Long?,
    onOpenDetail: (Category) -> Unit,
) {
    val s = day.summary
    val now by rememberNow(30_000)
    SectionCard(title = "Today so far", icon = Icons.Outlined.Assessment) {
        StatusLine(
            "Last feed",
            lastFeeding?.let { f ->
                val what = if (f.type == FeedingType.BREAST) "breast, ${formatDuration(f.totalSeconds.toLong())}"
                else "bottle, ${f.amountMl ?: 0} ml"
                "${formatAgo(f.startTime, now)} ($what)"
            } ?: "—",
        )
        StatusLine("Next side", StatsCalculator.suggestedSide(lastBreast).label)
        StatusLine(
            "Sleep",
            when {
                sleepStart != null -> "asleep for ${formatDuration((now - sleepStart) / 1000)}"
                lastSleep != null -> "awake for ${formatDuration((now - lastSleep.endTime) / 1000)}"
                else -> "—"
            },
        )
        HorizontalDivider()
        SummaryLine(
            "Feeds",
            "${s.feeds}",
            "${s.breastFeeds} breast · ${s.bottleFeeds} bottle" + if (s.measuredFeeds > 0) " · ${s.foodG} g" else "",
        ) { onOpenDetail(Category.FEEDS) }
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
