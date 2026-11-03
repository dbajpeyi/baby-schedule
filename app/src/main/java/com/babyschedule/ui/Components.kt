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
import com.babyschedule.data.Sleep
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
) {
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

    /** A feed; [diaper] is a change logged as part of the same session. */
    data class FeedingEvent(val feeding: Feeding, val diaper: Diaper? = null) : TimelineEvent {
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

fun DayData.timeline(): List<TimelineEvent> = buildList {
    val feedingIds = feedings.map { it.id }.toSet()
    val sessionDiapers = diapers.filter { it.feedingId != null && it.feedingId in feedingIds }
        .associateBy { it.feedingId }
    feedings.forEach { add(TimelineEvent.FeedingEvent(it, sessionDiapers[it.id])) }
    diapers.filter { it.feedingId == null || it.feedingId !in feedingIds }
        .forEach { add(TimelineEvent.DiaperEvent(it)) }
    sleeps.forEach { add(TimelineEvent.SleepEvent(it)) }
    growth.forEach { add(TimelineEvent.GrowthEvent(it)) }
}.sortedByDescending { it.time }

fun TimelineEvent.editRequest(): DialogRequest = when (this) {
    is TimelineEvent.FeedingEvent -> DialogRequest.EditFeeding(feeding)
    is TimelineEvent.DiaperEvent -> DialogRequest.EditDiaper(diaper)
    is TimelineEvent.SleepEvent -> DialogRequest.EditSleep(sleep)
    is TimelineEvent.GrowthEvent -> DialogRequest.EditGrowth(growth)
}

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

private fun TimelineEvent.look(): EventLook = when (this) {
    is TimelineEvent.FeedingEvent -> {
        val f = feeding
        if (f.type == FeedingType.BREAST) {
            val segments = f.segmentList
            val sideLines = if (segments.isNotEmpty()) {
                segments.map { "${it.side.label} ${formatTime(it.start)} – ${formatTime(it.end)} (${formatDuration(it.durationMs / 1000)})" }
            } else {
                listOf(
                    buildList {
                        if (f.leftSeconds > 0) add("Left ${formatDuration(f.leftSeconds.toLong())}")
                        if (f.rightSeconds > 0) add("Right ${formatDuration(f.rightSeconds.toLong())}")
                    }.joinToString(" · ")
                )
            }
            EventLook(
                Icons.Filled.ChildCare, SeriesColors.Left,
                "Breastfeeding · ${formatDuration(f.totalSeconds.toLong())}",
                sideLines + listOfNotNull(
                    weightLine(f),
                    diaper?.let { d -> "Diaper: ${diaperSummary(d)}" + if (d.notes.isNotBlank()) " · ${d.notes}" else "" },
                    f.notes.takeIf { it.isNotBlank() },
                ),
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
