package com.babyschedule.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.babyschedule.data.Diaper
import com.babyschedule.data.DiaperType
import com.babyschedule.data.Feeding
import com.babyschedule.data.FeedingType
import com.babyschedule.data.Growth
import com.babyschedule.data.MilkType
import com.babyschedule.data.Profile
import com.babyschedule.data.Side
import com.babyschedule.data.Sleep
import com.babyschedule.util.formatDateFull
import com.babyschedule.util.formatDateShort
import com.babyschedule.util.formatDuration
import com.babyschedule.util.formatNumber
import com.babyschedule.util.formatTime
import com.babyschedule.util.toZoned
import com.babyschedule.util.zone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.roundToInt

sealed interface DialogRequest {
    data class EditFeeding(val feeding: Feeding) : DialogRequest
    data class EditDiaper(val diaper: Diaper) : DialogRequest
    data class EditSleep(val sleep: Sleep) : DialogRequest
    data class EditGrowth(val growth: Growth) : DialogRequest
    data object EditProfile : DialogRequest

    companion object {
        fun newBreastFeeding(time: Long) = EditFeeding(
            Feeding(type = FeedingType.BREAST, startTime = time, endTime = time)
        )

        fun newBottle(time: Long) = EditFeeding(
            Feeding(type = FeedingType.BOTTLE, startTime = time, endTime = time, milkType = MilkType.FORMULA)
        )

        fun newDiaper(time: Long) = EditDiaper(Diaper(time = time, type = DiaperType.WET))

        fun newSleep(time: Long) = EditSleep(Sleep(startTime = time - 60 * 60_000L, endTime = time))

        fun newGrowth(time: Long) = EditGrowth(Growth(time = time))
    }
}

@Composable
fun DialogHost(request: DialogRequest?, vm: BabyViewModel, ui: UiActions, onDismiss: () -> Unit) {
    if (request == null) return
    fun deleteAction(item: Any, isNew: Boolean, label: String): (() -> Unit)? =
        if (isNew) null else {
            {
                onDismiss()
                ui.launch {
                    vm.delete(item)
                    ui.undoable("$label deleted") { vm.restore(item) }
                }
            }
        }

    key(request) {
        when (request) {
            is DialogRequest.EditFeeding -> FeedingDialog(
                initial = request.feeding,
                onDismiss = onDismiss,
                onSave = { vm.save(it); onDismiss() },
                onDelete = deleteAction(request.feeding, request.feeding.id == 0L, "Feeding"),
            )
            is DialogRequest.EditDiaper -> DiaperDialog(
                initial = request.diaper,
                onDismiss = onDismiss,
                onSave = { vm.save(it); onDismiss() },
                onDelete = deleteAction(request.diaper, request.diaper.id == 0L, "Diaper"),
            )
            is DialogRequest.EditSleep -> SleepDialog(
                initial = request.sleep,
                onDismiss = onDismiss,
                onSave = { vm.save(it); onDismiss() },
                onDelete = deleteAction(request.sleep, request.sleep.id == 0L, "Sleep"),
            )
            is DialogRequest.EditGrowth -> GrowthDialog(
                initial = request.growth,
                onDismiss = onDismiss,
                onSave = { vm.save(it); onDismiss() },
                onDelete = deleteAction(request.growth, request.growth.id == 0L, "Measurement"),
            )
            DialogRequest.EditProfile -> ProfileDialog(
                initial = vm.profile.value,
                onDismiss = onDismiss,
                onSave = { vm.saveProfile(it); onDismiss() },
            )
        }
    }
}

@Composable
private fun EntryDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    onDelete: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = saveEnabled) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun localDateToPickerMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun pickerMillisToLocalDate(ms: Long): LocalDate =
    Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun DatePickerPopup(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = localDateToPickerMillis(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(pickerMillisToLocalDate(it)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun DateTimeField(label: String, millis: Long, onChange: (Long) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val zdt = toZoned(millis)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showDate = true }) { Text(formatDateShort(zdt.toLocalDate())) }
            OutlinedButton(onClick = { showTime = true }) { Text(formatTime(millis)) }
        }
    }
    if (showDate) {
        DatePickerPopup(zdt.toLocalDate(), onDismiss = { showDate = false }) { date ->
            onChange(ZonedDateTime.of(date, zdt.toLocalTime(), zone()).toInstant().toEpochMilli())
        }
    }
    if (showTime) {
        val context = LocalContext.current
        val state = rememberTimePickerState(
            initialHour = zdt.hour,
            initialMinute = zdt.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(
                        zdt.withHour(state.hour).withMinute(state.minute).withSecond(0).withNano(0)
                            .toInstant().toEpochMilli()
                    )
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String? = null,
    decimal: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val suffixContent: (@Composable () -> Unit)? = if (suffix != null) {
        { Text(suffix) }
    } else {
        null
    }
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.replace(',', '.').filter { it.isDigit() || (decimal && it == '.') }) },
        label = { Text(label) },
        suffix = suffixContent,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun NotesField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Notes (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Keeps any pause time from the original session (end shifts with the start),
 * but never ends before start + timed duration.
 */
private fun breastEndTime(initial: Feeding, start: Long, totalSeconds: Int): Long {
    val minimum = start + totalSeconds * 1000L
    return if (initial.id != 0L && initial.type == FeedingType.BREAST) {
        maxOf(minimum, initial.endTime + (start - initial.startTime))
    } else {
        minimum
    }
}

private fun minutesText(seconds: Int): String =
    if (seconds <= 0) "" else formatNumber(seconds / 60f)

private fun parseMinutesToSeconds(text: String): Int =
    ((text.toFloatOrNull() ?: 0f) * 60).roundToInt().coerceAtLeast(0)

@Composable
fun FeedingDialog(
    initial: Feeding,
    onDismiss: () -> Unit,
    onSave: (Feeding) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var type by remember { mutableStateOf(initial.type) }
    var start by remember { mutableLongStateOf(initial.startTime) }
    var left by remember { mutableStateOf(minutesText(initial.leftSeconds)) }
    var right by remember { mutableStateOf(minutesText(initial.rightSeconds)) }
    var firstSide by remember { mutableStateOf(initial.firstSide ?: Side.LEFT) }
    var amount by remember { mutableStateOf(initial.amountMl?.toString() ?: "") }
    var milk by remember { mutableStateOf(initial.milkType ?: MilkType.FORMULA) }
    var notes by remember { mutableStateOf(initial.notes) }

    val leftS = parseMinutesToSeconds(left)
    val rightS = parseMinutesToSeconds(right)
    val amountMl = amount.toIntOrNull() ?: 0
    val valid = if (type == FeedingType.BREAST) leftS + rightS > 0 else amountMl > 0

    EntryDialog(
        title = if (initial.id == 0L) "Add feeding" else "Edit feeding",
        onDismiss = onDismiss,
        saveEnabled = valid,
        onDelete = onDelete,
        onSave = {
            val result = if (type == FeedingType.BREAST) {
                val first = when {
                    leftS == 0 -> Side.RIGHT
                    rightS == 0 -> Side.LEFT
                    else -> firstSide
                }
                val last = when {
                    leftS == 0 -> Side.RIGHT
                    rightS == 0 -> Side.LEFT
                    initial.lastSide != null && initial.leftSeconds > 0 && initial.rightSeconds > 0 -> initial.lastSide
                    else -> first.other()
                }
                initial.copy(
                    type = FeedingType.BREAST,
                    startTime = start,
                    endTime = breastEndTime(initial, start, leftS + rightS),
                    leftSeconds = leftS,
                    rightSeconds = rightS,
                    firstSide = first,
                    lastSide = last,
                    amountMl = null,
                    milkType = null,
                    notes = notes.trim(),
                )
            } else {
                initial.copy(
                    type = FeedingType.BOTTLE,
                    startTime = start,
                    endTime = start,
                    leftSeconds = 0,
                    rightSeconds = 0,
                    firstSide = null,
                    lastSide = null,
                    amountMl = amountMl,
                    milkType = milk,
                    notes = notes.trim(),
                )
            }
            onSave(result)
        },
    ) {
        ChoiceChips(
            options = FeedingType.entries,
            selected = type,
            label = { if (it == FeedingType.BREAST) "Breast" else "Bottle" },
            onSelect = { type = it },
        )
        DateTimeField("Started", start) { start = it }
        if (type == FeedingType.BREAST) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(left, { left = it }, "Left", "min", modifier = Modifier.weight(1f))
                NumberField(right, { right = it }, "Right", "min", modifier = Modifier.weight(1f))
            }
            if (leftS > 0 && rightS > 0) {
                Text("Started on", style = MaterialTheme.typography.labelMedium)
                ChoiceChips(Side.entries, firstSide, { it.label }, { firstSide = it })
            }
            if (leftS + rightS > 0) {
                Text(
                    "Total ${formatDuration((leftS + rightS).toLong())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            NumberField(amount, { amount = it }, "Amount", "ml", decimal = false)
            ChoiceChips(MilkType.entries, milk, { it.label }, { milk = it })
        }
        NotesField(notes) { notes = it }
    }
}

@Composable
fun DiaperDialog(
    initial: Diaper,
    onDismiss: () -> Unit,
    onSave: (Diaper) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var time by remember { mutableLongStateOf(initial.time) }
    var type by remember { mutableStateOf(initial.type) }
    var notes by remember { mutableStateOf(initial.notes) }
    EntryDialog(
        title = if (initial.id == 0L) "Add diaper" else "Edit diaper",
        onDismiss = onDismiss,
        saveEnabled = true,
        onDelete = onDelete,
        onSave = { onSave(initial.copy(time = time, type = type, notes = notes.trim())) },
    ) {
        DateTimeField("Time", time) { time = it }
        ChoiceChips(DiaperType.entries, type, { it.label }, { type = it })
        NotesField(notes) { notes = it }
    }
}

@Composable
fun SleepDialog(
    initial: Sleep,
    onDismiss: () -> Unit,
    onSave: (Sleep) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var start by remember { mutableLongStateOf(initial.startTime) }
    var end by remember { mutableLongStateOf(initial.endTime) }
    var notes by remember { mutableStateOf(initial.notes) }
    val valid = end > start
    EntryDialog(
        title = if (initial.id == 0L) "Add sleep" else "Edit sleep",
        onDismiss = onDismiss,
        saveEnabled = valid,
        onDelete = onDelete,
        onSave = { onSave(initial.copy(startTime = start, endTime = end, notes = notes.trim())) },
    ) {
        DateTimeField("Fell asleep", start) { start = it }
        DateTimeField("Woke up", end) { end = it }
        Text(
            if (valid) "Duration ${formatDuration((end - start) / 1000)}" else "Wake-up time must be after the start",
            style = MaterialTheme.typography.bodySmall,
            color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        NotesField(notes) { notes = it }
    }
}

private fun kgText(grams: Int?): String =
    grams?.let { String.format(Locale.US, "%.3f", it / 1000.0).trimEnd('0').trimEnd('.') } ?: ""

private fun parseKgToGrams(text: String): Int? =
    text.toDoubleOrNull()?.let { (it * 1000).roundToInt() }?.takeIf { it > 0 }

@Composable
fun GrowthDialog(
    initial: Growth,
    onDismiss: () -> Unit,
    onSave: (Growth) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var time by remember { mutableLongStateOf(initial.time) }
    var weight by remember { mutableStateOf(kgText(initial.weightGrams)) }
    var length by remember { mutableStateOf(initial.lengthCm?.let { formatNumber(it) } ?: "") }
    var head by remember { mutableStateOf(initial.headCm?.let { formatNumber(it) } ?: "") }
    var notes by remember { mutableStateOf(initial.notes) }

    val grams = parseKgToGrams(weight)
    val lengthCm = length.toFloatOrNull()?.takeIf { it > 0 }
    val headCm = head.toFloatOrNull()?.takeIf { it > 0 }
    val valid = grams != null || lengthCm != null || headCm != null

    EntryDialog(
        title = if (initial.id == 0L) "Add measurement" else "Edit measurement",
        onDismiss = onDismiss,
        saveEnabled = valid,
        onDelete = onDelete,
        onSave = {
            onSave(initial.copy(time = time, weightGrams = grams, lengthCm = lengthCm, headCm = headCm, notes = notes.trim()))
        },
    ) {
        DateTimeField("Measured", time) { time = it }
        NumberField(weight, { weight = it }, "Weight", "kg")
        if (grams != null) {
            Text("= $grams g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(length, { length = it }, "Length", "cm", modifier = Modifier.weight(1f))
            NumberField(head, { head = it }, "Head", "cm", modifier = Modifier.weight(1f))
        }
        NotesField(notes) { notes = it }
    }
}

@Composable
fun ProfileDialog(initial: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var birth by remember { mutableStateOf(initial.birthDate) }
    var birthWeight by remember { mutableStateOf(kgText(initial.birthWeightGrams)) }
    var showDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Baby profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Date of birth", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { showDate = true }) {
                        Text(birth?.let { formatDateFull(it) } ?: "Choose date")
                    }
                }
                NumberField(birthWeight, { birthWeight = it }, "Birth weight", "kg")
                Text(
                    "Birth weight is used as the starting point for growth tracking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Profile(name.trim(), birth, parseKgToGrams(birthWeight)))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (showDate) {
        DatePickerPopup(birth ?: LocalDate.now(), onDismiss = { showDate = false }) { birth = it }
    }
}
