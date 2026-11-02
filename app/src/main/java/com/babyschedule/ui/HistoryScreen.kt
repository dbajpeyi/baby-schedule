package com.babyschedule.ui

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.babyschedule.ui.theme.SeriesColors
import com.babyschedule.util.defaultTimeFor
import com.babyschedule.util.formatDayLabel
import com.babyschedule.util.formatMinutes
import java.time.LocalDate

@Composable
fun HistoryScreen(vm: BabyViewModel, ui: UiActions) {
    val selected by vm.selectedDay.collectAsStateWithLifecycle()
    val data by vm.historyData.collectAsStateWithLifecycle()
    var pickDate by remember { mutableStateOf(false) }
    val events = data.timeline()
    val s = data.summary
    val isToday = selected == LocalDate.now()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.selectDay(selected.minusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                }
                TextButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) {
                    Text(formatDayLabel(selected), style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { vm.selectDay(selected.plusDays(1)) }, enabled = !isToday) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                }
            }
        }
        item {
            SectionCard(title = null) {
                TileGrid(
                    { m -> StatTile("Feeds", s.feeds.toString(), m, sub = "${s.breastFeeds} breast · ${s.bottleFeeds} bottle", accent = SeriesColors.Left) },
                    { m -> StatTile("Breastfeeding", formatMinutes(s.breastMin), m, sub = "L ${formatMinutes(s.leftMin)} · R ${formatMinutes(s.rightMin)}", accent = SeriesColors.Right) },
                    { m -> StatTile("Bottle", "${s.bottleMl} ml", m, accent = SeriesColors.Bottle) },
                    { m -> StatTile("Diapers", s.diapers.toString(), m, sub = "${s.wetTotal} wet · ${s.dirtyTotal} dirty", accent = SeriesColors.Wet) },
                    { m -> StatTile("Sleep", formatMinutes(s.sleepMin), m, accent = SeriesColors.Sleep) },
                )
            }
        }
        item {
            val time = defaultTimeFor(selected)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                AddChip("Feed") { ui.openDialog(DialogRequest.newBreastFeeding(time)) }
                AddChip("Bottle") { ui.openDialog(DialogRequest.newBottle(time)) }
                AddChip("Diaper") { ui.openDialog(DialogRequest.newDiaper(time)) }
                AddChip("Sleep") { ui.openDialog(DialogRequest.newSleep(time)) }
            }
        }
        if (events.isEmpty()) {
            item {
                Text(
                    "Nothing logged on this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            item {
                Text(
                    "${events.size} entries · tap one to edit",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(events, key = { it.key }) { event ->
                TimelineRow(event) { ui.openDialog(event.editRequest()) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (pickDate) {
        DatePickerPopup(selected, onDismiss = { pickDate = false }) { vm.selectDay(it) }
    }
}

@Composable
private fun AddChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
    )
}
