package com.dip.attendify.ui.setup

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dip.attendify.data.entity.SubjectEntity
import com.dip.attendify.data.entity.TimeSlotEntity
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("hh:mm a")
private val days    = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed",
    4 to "Thu", 5 to "Fri", 6 to "Sat")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableSetupScreen(
    onComplete: () -> Unit,
    vm: TimetableSetupViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isDone) { if (state.isDone) onComplete() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (state.step == TimetableSetupStep.PERIOD_CONFIG)
                                "Period Schedule" else "Fill Timetable"
                        )
                        Text(
                            if (state.step == TimetableSetupStep.PERIOD_CONFIG)
                                "Step 1 of 2" else "Step 2 of 2",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState  = state.step,
            transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it } },
            modifier     = Modifier.padding(padding),
            label        = "setup_step",
        ) { step ->
            when (step) {
                TimetableSetupStep.PERIOD_CONFIG ->
                    PeriodConfigStep(state = state.periodConfig, vm = vm)
                TimetableSetupStep.GRID_FILL ->
                    GridFillStep(state = state, vm = vm)
            }
        }
    }
}

// ── Step 1: Period Config ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodConfigStep(
    state: PeriodConfigState,
    vm: TimetableSetupViewModel,
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        SectionLabel("First period starts at")
        OutlinedTextField(
            value         = state.firstPeriodStart.format(timeFmt),
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Start time") },
            trailingIcon  = {
                IconButton(onClick = { showTimePicker = true }) {
                    Icon(Icons.Default.AccessTime, "Pick time")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel("Period duration")
        StepperRow(
            label   = "${state.periodDurationMins} minutes",
            onMinus = { vm.onDurationChange(state.periodDurationMins - 5) },
            onPlus  = { vm.onDurationChange(state.periodDurationMins + 5) },
        )

        SectionLabel("Number of periods per day")
        StepperRow(
            label   = "${state.numberOfPeriods} periods",
            onMinus = { vm.onPeriodCountChange(state.numberOfPeriods - 1) },
            onPlus  = { vm.onPeriodCountChange(state.numberOfPeriods + 1) },
        )

        SectionLabel("Lunch / break")
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth(),
        ) {
            Text("Add a break", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked         = state.breakAfterPeriod != null,
                onCheckedChange = { vm.onBreakAfterPeriodChange(if (it) 4 else null) },
            )
        }

        if (state.breakAfterPeriod != null) {
            Text(
                "After period ${state.breakAfterPeriod}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value         = state.breakAfterPeriod.toFloat(),
                onValueChange = { vm.onBreakAfterPeriodChange(it.toInt()) },
                valueRange    = 1f..(state.numberOfPeriods - 1f).coerceAtLeast(1f),
                steps         = (state.numberOfPeriods - 2).coerceAtLeast(0),
            )

            SectionLabel("Break duration")
            StepperRow(
                label   = "${state.breakDurationMins} minutes",
                onMinus = { vm.onBreakDurationChange(state.breakDurationMins - 5) },
                onPlus  = { vm.onBreakDurationChange(state.breakDurationMins + 5) },
            )
        }

        if (state.preview.isNotEmpty()) {
            SectionLabel("Preview")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier            = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.preview.forEach { slot ->
                        Text(
                            "Period ${slot.slotOrder}  •  ${slot.startTime} – ${slot.endTime}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Button(
            onClick  = vm::confirmPeriodConfig,
            enabled  = state.preview.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Continue to Grid") }

        Spacer(Modifier.height(24.dp))
    }

    if (showTimePicker) {
        TimePickerDialog(
            initial   = state.firstPeriodStart,
            onPicked  = { vm.onFirstPeriodStartChange(it) },
            onDismiss = { showTimePicker = false },
        )
    }
}

// ── Step 2: Grid Fill ─────────────────────────────────────────────────────────

@Composable
private fun GridFillStep(
    state: TimetableSetupState,
    vm: TimetableSetupViewModel,
) {
    val grid = state.gridFill
    var cellDialog    by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var copyDayDialog by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {

        ScrollableTabRow(selectedTabIndex = grid.activeDayTab - 1) {
            days.forEach { (num, label) ->
                Tab(
                    selected = grid.activeDayTab == num,
                    onClick  = { vm.onDayTabChange(num) },
                    text     = { Text(label) },
                )
            }
        }

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { copyDayDialog = grid.activeDayTab }) {
                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy ${days.find { it.first == grid.activeDayTab }?.second} to...")
            }
        }

        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier            = Modifier.weight(1f),
        ) {
            items(grid.slots, key = { it.id }) { slot ->
                val cell = grid.cells[Pair(grid.activeDayTab, slot.id)]
                if (cell?.isSpanContinuation == true) return@items
                SlotRow(
                    slot     = slot,
                    cell     = cell,
                    allSlots = grid.slots,
                    onClick  = { cellDialog = Pair(grid.activeDayTab, slot.id) },
                    onClear  = { vm.clearCell(grid.activeDayTab, slot.id) },
                )
            }
        }

        Surface(tonalElevation = 3.dp) {
            Button(
                onClick  = vm::saveTimetable,
                enabled  = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Save Timetable")
                }
            }
        }
    }

    cellDialog?.let { (day, slotId) ->
        val startIndex = grid.slots.indexOfFirst { it.id == slotId }
        val maxSpan    = if (startIndex >= 0) (grid.slots.size - startIndex).coerceAtLeast(1) else 1
        CellEditDialog(
            subjects  = grid.subjects,
            maxSpan   = maxSpan,
            onConfirm = { subjectId, span ->
                vm.placeSubject(day, slotId, subjectId, span)
                cellDialog = null
            },
            onDismiss = { cellDialog = null },
        )
    }

    copyDayDialog?.let { sourceDay ->
        CopyDayDialog(
            sourceDay = sourceDay,
            days      = days,
            onConfirm = { targets -> vm.copyDayTo(sourceDay, targets); copyDayDialog = null },
            onDismiss = { copyDayDialog = null },
        )
    }
}

// ── Slot Row ──────────────────────────────────────────────────────────────────

@Composable
private fun SlotRow(
    slot:    TimeSlotEntity,
    cell:    TimetableCell?,
    allSlots: List<TimeSlotEntity>,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    val isFilled     = cell?.subjectId != null
    val cellColor    = runCatching {
        if (isFilled) Color(android.graphics.Color.parseColor(cell!!.colorHex ?: "#5C6BC0"))
        else Color.Transparent
    }.getOrDefault(Color.Transparent)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier
            .fillMaxWidth()
            .height(if (isFilled && (cell?.spanSlots ?: 1) > 1) (56 * (cell?.spanSlots ?: 1)).dp else 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isFilled) cellColor.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Column(modifier = Modifier.width(72.dp)) {
            val spanSlots = cell?.spanSlots ?: 1
            val startIdx  = allSlots.indexOfFirst { it.id == slot.id }
            val endTime   = if (spanSlots > 1 && startIdx >= 0 && startIdx + spanSlots - 1 < allSlots.size)
                allSlots[startIdx + spanSlots - 1].endTime
            else slot.endTime
            Text(slot.startTime, style = MaterialTheme.typography.labelMedium)
            Text(endTime, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Box(modifier = Modifier.weight(1f)) {
            if (isFilled) {
                Text(
                    text       = buildString {
                        append(cell!!.subjectName ?: "")
                        if ((cell.spanSlots) > 1) append("  (${cell.spanSlots}-slot lab)")
                    },
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Text(
                    "Tap to assign",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isFilled) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, "Clear",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
            }
        } else {
            Icon(Icons.Default.Add, "Add",
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }
}

// ── Cell Edit Dialog ──────────────────────────────────────────────────────────

@Composable
private fun CellEditDialog(
    subjects:  List<SubjectEntity>,
    maxSpan:   Int = 4,
    onConfirm: (subjectId: Int, span: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var sessionType     by remember { mutableStateOf("Lecture") }
    var labSpan         by remember { mutableStateOf(2.coerceAtMost(maxSpan)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Assign Subject") },
        text    = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (subjects.isEmpty()) {
                    Text(
                        "No subjects yet. Add subjects from Home first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text("Subject", style = MaterialTheme.typography.labelMedium)
                    subjects.forEach { subject ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSubject = subject }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = selectedSubject?.id == subject.id,
                                onClick  = { selectedSubject = subject },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(subject.name)
                        }
                    }
                }

                HorizontalDivider()

                Text("Session type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val allowedTypes = when (selectedSubject?.type) {
                        com.dip.attendify.data.entity.SubjectType.LECTURE -> listOf("Lecture")
                        com.dip.attendify.data.entity.SubjectType.LAB     -> listOf("Lab")
                        else -> listOf("Lecture", "Lab")
                    }
                    // Reset session type if subject changed and current type no longer allowed
                    if (sessionType !in allowedTypes) sessionType = allowedTypes.first()
                    allowedTypes.forEach { type ->
                        FilterChip(
                            selected = sessionType == type,
                            onClick  = { sessionType = type },
                            label    = { Text(type) },
                        )
                    }
                }

                if (sessionType == "Lab") {
                    Text(
                        if (maxSpan < 2) "Only 1 slot remaining — cannot add lab here"
                        else "Lab spans $labSpan slot${if (labSpan > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (maxSpan < 2) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    if (maxSpan >= 2) {
                        Slider(
                            value         = labSpan.toFloat(),
                            onValueChange = { labSpan = it.toInt() },
                            valueRange    = 2f..maxSpan.toFloat(),
                            steps         = (maxSpan - 2).coerceAtLeast(0),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = {
                    selectedSubject?.let {
                        onConfirm(it.id, if (sessionType == "Lab") labSpan else 1)
                    }
                },
                enabled  = selectedSubject != null,
            ) { Text("Assign") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Copy Day Dialog ───────────────────────────────────────────────────────────

@Composable
private fun CopyDayDialog(
    sourceDay: Int,
    days:      List<Pair<Int, String>>,
    onConfirm: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected   = remember { mutableStateOf(setOf<Int>()) }
    val sourceName = days.find { it.first == sourceDay }?.second ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Copy $sourceName to...") },
        text    = {
            Column {
                days.filter { it.first != sourceDay }.forEach { (num, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected.value =
                                    if (num in selected.value) selected.value - num
                                    else selected.value + num
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked         = num in selected.value,
                            onCheckedChange = {
                                selected.value =
                                    if (it) selected.value + num else selected.value - num
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { onConfirm(selected.value) },
                enabled  = selected.value.isNotEmpty(),
            ) { Text("Copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun StepperRow(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = onMinus) { Icon(Icons.Default.Remove, "Decrease") }
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        IconButton(onClick = onPlus)  { Icon(Icons.Default.Add, "Increase") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial:   LocalTime,
    onPicked:  (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour   = initial.hour,
        initialMinute = initial.minute,
        is24Hour      = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Select time") },
        text    = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = {
                onPicked(LocalTime.of(pickerState.hour, pickerState.minute))
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}