package com.babyschedule.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.babyschedule.data.Diaper
import com.babyschedule.data.DiaperType
import com.babyschedule.data.Feeding
import com.babyschedule.data.FeedingType
import com.babyschedule.data.Growth
import com.babyschedule.data.MilkType
import com.babyschedule.data.Session
import com.babyschedule.data.Sleep
import com.babyschedule.data.intakeGrams
import com.babyschedule.domain.SessionDraft
import com.babyschedule.data.durationMs
import com.babyschedule.data.segmentList
import com.babyschedule.data.totalSeconds
import com.babyschedule.ui.theme.SeriesColors
import com.babyschedule.util.formatDuration
import com.babyschedule.util.formatNumber
import com.babyschedule.util.formatTime
import com.babyschedule.util.formatWeight
import com.babyschedule.util.formatWeightChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shared UI plumbing passed to every screen. */
class UiActions(
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
    /** Asks for notification permission (Android 13+) if not yet granted. */
    val requestNotifications: () -> Unit,
    val openDialog: (DialogRequest) -> Unit,
    /** Opens the session form for an existing session. */
    val openSession: (Long) -> Unit,
    /** Opens the session form with a draft (new session, or one built from a live timer). */
    val editDraft: (SessionDraft) -> Unit,
) {
    /** Opens the right editor for a log entry: its session if it has one, otherwise its own form. */
    fun open(event: TimelineEvent) {
        when (event) {
            is TimelineEvent.SessionEvent -> openSession(event.session.id)
            is TimelineEvent.FeedingEvent -> {
                val f = event.feeding
                val sid = f.sessionId
                when {
                    sid != null -> openSession(sid)
                    f.type == FeedingType.BREAST -> editDraft(SessionDraft.from(null, f, null, null))
                    else -> openDialog(DialogRequest.EditFeeding(f))
                }
            }
            is TimelineEvent.DiaperEvent -> {
                val sid = event.diaper.sessionId
                if (sid != null) openSession(sid) else openDialog(DialogRequest.EditDiaper(event.diaper))
            }
            is TimelineEvent.SleepEvent -> {
                val sid = event.sleep.sessionId
                if (sid != null) openSession(sid) else openDialog(DialogRequest.EditSleep(event.sleep))
            }
            is TimelineEvent.GrowthEvent -> openDialog(DialogRequest.EditGrowth(event.growth))
        }
    }

    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    /** Shows a snackbar with an "Undo" action. Must be called from a coroutine. */
    suspend fun undoable(text: String, undo: suspend () -> Unit) {
        snackbar.currentSnackbarData?.dismiss()
        val result = snackbar.showSnackbar(text, actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) undo()
    }
}

/** Current time in millis that updates every [intervalMs] while on screen. */
@Composable
fun rememberNow(intervalMs: Long = 1_000): State<Long> =
    produceState(System.currentTimeMillis(), intervalMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(intervalMs)
        }
    }

@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null || action != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    action?.invoke()
                }
            }
            content()
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (accent != null) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (onClick != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Lays tiles out two per row. */
@Composable
fun TileGrid(vararg tiles: @Composable (Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.toList().chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile -> tile(Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

/** Optional single choice: tapping the selected chip clears it (empty string). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(if (option == selected) "" else option) },
                label = { Text(option) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(items: List<Pair<String, Color>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Timeline
// ---------------------------------------------------------------------------

sealed interface TimelineEvent {
    val time: Long
    val key: String
    val item: Any

    /** A whole session: diaper, feed, weights and sleep logged together. */
    data class SessionEvent(
        val session: Session,
        val feeding: Feeding?,
        val diapers: List<Diaper>,
        val sleep: Sleep?,
        /** Set when the baby is asleep right now as part of this session. */
        val sleepingSince: Long? = null,
    ) : TimelineEvent {
        override val time get() = session.startTime
        override val key get() = "x${session.id}"
        override val item: Any get() = session
    }

    data class FeedingEvent(val feeding: Feeding) : TimelineEvent {
        override val time get() = feeding.startTime
        override val key get() = "f${feeding.id}"
        override val item: Any get() = feeding
    }

    data class DiaperEvent(val diaper: Diaper) : TimelineEvent {
        override val time get() = diaper.time
        override val key get() = "d${diaper.id}"
        override val item: Any get() = diaper
    }

    data class SleepEvent(val sleep: Sleep) : TimelineEvent {
        override val time get() = sleep.startTime
        override val key get() = "s${sleep.id}"
        override val item: Any get() = sleep
    }

    data class GrowthEvent(val growth: Growth) : TimelineEvent {
        override val time get() = growth.time
        override val key get() = "g${growth.id}"
        override val item: Any get() = growth
    }
}

/**
 * The day's log: sessions as single entries, plus anything logged on its own.
 * Pass the live sleep so a session can show "sleeping now".
 */
fun DayData.timeline(liveSleepSessionId: Long? = null, liveSleepStart: Long? = null): List<TimelineEvent> = buildList {
    sessions.forEach { s ->
        add(
            TimelineEvent.SessionEvent(
                session = s,
                feeding = sessionFeedings.firstOrNull { it.sessionId == s.id },
                diapers = sessionDiapers.filter { it.sessionId == s.id },
                sleep = sessionSleeps.firstOrNull { it.sessionId == s.id },
                sleepingSince = if (liveSleepSessionId == s.id) liveSleepStart else null,
            )
        )
    }
    feedings.filter { it.sessionId == null }.forEach { add(TimelineEvent.FeedingEvent(it)) }
    diapers.filter { it.sessionId == null }.forEach { add(TimelineEvent.DiaperEvent(it)) }
    sleeps.filter { it.sessionId == null }.forEach { add(TimelineEvent.SleepEvent(it)) }
    growth.forEach { add(TimelineEvent.GrowthEvent(it)) }
}.sortedByDescending { it.time }

private data class EventLook(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val lines: List<String>,
    val timeText: String,
)

fun diaperSummary(d: Diaper): String =
    listOf(d.type.label, d.color, d.amount).filter { it.isNotBlank() }.joinToString(" · ")

private fun weightLine(f: Feeding): String? {
    val before = f.weightBeforeGrams
    val after = f.weightAfterGrams
    return when {
        before != null && after != null -> "Weight ${formatWeight(before)} → ${formatWeight(after)} (${formatWeightChange(after - before)})"
        before != null -> "Weight before ${formatWeight(before)}"
        after != null -> "Weight after ${formatWeight(after)}"
        else -> null
    }
}

private fun sideLines(f: Feeding): List<String> {
    val segments = f.segmentList
    return if (segments.isNotEmpty()) {
        segments.map { "${it.side.label} ${formatTime(it.start)} – ${formatTime(it.end)} (${formatDuration(it.durationMs / 1000)})" }
    } else {
        listOf(
            buildList {
                if (f.leftSeconds > 0) add("Left ${formatDuration(f.leftSeconds.toLong())}")
                if (f.rightSeconds > 0) add("Right ${formatDuration(f.rightSeconds.toLong())}")
            }.joinToString(" · ")
        )
    }
}

private fun diaperLine(d: Diaper): String =
    "Diaper: ${diaperSummary(d)}" + if (d.notes.isNotBlank()) " · ${d.notes}" else ""

private fun sessionLook(e: TimelineEvent.SessionEvent): EventLook {
    val f = e.feeding
    val lines = buildList {
        e.diapers.forEach { add(diaperLine(it)) }
        if (f != null && f.type == FeedingType.BREAST) {
            f.weightBeforeGrams?.let { add("Weight before ${formatWeight(it)}") }
            addAll(sideLines(f))
            f.weightAfterGrams?.let { after ->
                add("Weight after ${formatWeight(after)}" + (f.intakeGrams?.let { " (drank ${formatWeightChange(it)})" } ?: ""))
            }
        } else if (f != null) {
            add("Bottle ${f.amountMl ?: 0} ml · ${(f.milkType ?: MilkType.FORMULA).label}")
        }
        e.sleep?.let { add("Sleep ${formatTime(it.startTime)} – ${formatTime(it.endTime)} (${formatDuration(it.durationMs / 1000)})") }
        e.sleepingSince?.let { add("Sleeping since ${formatTime(it)}") }
        e.session.notes.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    val title = when {
        f != null && f.type == FeedingType.BREAST -> "Breastfeeding · ${formatDuration(f.totalSeconds.toLong())}"
        f != null -> "Bottle · ${f.amountMl ?: 0} ml"
        e.sleep != null || e.sleepingSince != null -> "Sleep"
        e.diapers.isNotEmpty() -> "Diaper"
        else -> "Session"
    }
    val end = listOfNotNull(f?.endTime, e.sleep?.endTime).maxOrNull()
    return EventLook(
        if (f != null) Icons.Filled.ChildCare else if (e.sleep != null) Icons.Filled.Bedtime else Icons.Filled.BabyChangingStation,
        SeriesColors.Left,
        title,
        lines,
        if (end != null && end > e.session.startTime) "${formatTime(e.session.startTime)} – ${formatTime(end)}" else formatTime(e.session.startTime),
    )
}

private fun TimelineEvent.look(): EventLook = when (this) {
    is TimelineEvent.SessionEvent -> sessionLook(this)
    is TimelineEvent.FeedingEvent -> {
        val f = feeding
        if (f.type == FeedingType.BREAST) {
            EventLook(
                Icons.Filled.ChildCare, SeriesColors.Left,
                "Breastfeeding · ${formatDuration(f.totalSeconds.toLong())}",
                sideLines(f) + listOfNotNull(weightLine(f), f.notes.takeIf { it.isNotBlank() }),
                "${formatTime(f.startTime)} – ${formatTime(f.endTime)}",
            )
        } else {
            EventLook(
                Icons.Filled.LocalDrink, SeriesColors.Bottle,
                "Bottle · ${f.amountMl ?: 0} ml",
                listOfNotNull((f.milkType ?: MilkType.FORMULA).label, f.notes.takeIf { it.isNotBlank() }),
                formatTime(f.startTime),
            )
        }
    }
    is TimelineEvent.DiaperEvent -> EventLook(
        Icons.Filled.BabyChangingStation,
        when (diaper.type) {
            DiaperType.WET -> SeriesColors.Wet
            DiaperType.DIRTY -> SeriesColors.Dirty
            DiaperType.MIXED -> SeriesColors.Mixed
        },
        "Diaper · ${diaperSummary(diaper)}",
        listOfNotNull(diaper.notes.takeIf { it.isNotBlank() }),
        formatTime(diaper.time),
    )
    is TimelineEvent.SleepEvent -> EventLook(
        Icons.Filled.Bedtime, SeriesColors.Sleep,
        "Sleep · ${formatDuration(sleep.durationMs / 1000)}",
        listOfNotNull(sleep.notes.takeIf { it.isNotBlank() }),
        "${formatTime(sleep.startTime)} – ${formatTime(sleep.endTime)}",
    )
    is TimelineEvent.GrowthEvent -> EventLook(
        Icons.Filled.MonitorWeight, SeriesColors.Weight,
        growth.weightGrams?.let { "Weight · ${formatWeight(it)}" } ?: "Measurement",
        listOfNotNull(
            listOfNotNull(
                growth.lengthCm?.let { "Length ${formatNumber(it)} cm" },
                growth.headCm?.let { "Head ${formatNumber(it)} cm" },
            ).joinToString(" · ").takeIf { it.isNotBlank() },
            growth.notes.takeIf { it.isNotBlank() },
        ),
        formatTime(growth.time),
    )
}

@Composable
fun TimelineRow(event: TimelineEvent, onClick: () -> Unit) {
    val look = event.look()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(look.color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(look.icon, contentDescription = null, tint = look.color)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(look.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            look.lines.filter { it.isNotBlank() }.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            look.timeText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
