package com.dip.attendify.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dip.attendify.data.entity.AcademicEventEntity
import com.dip.attendify.data.entity.AcademicEventType
import com.dip.attendify.ui.common.GlassCard
import com.dip.attendify.ui.common.GlassVariant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")
private val days     = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed",
    4 to "Thu", 5 to "Fri", 6 to "Sat")
private val dayHeaders = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onEditTimetable: () -> Unit,
    vm: ScheduleViewModel = hiltViewModel(),
) {
    val state    by vm.state.collectAsStateWithLifecycle()
    var activeTab by remember { mutableIntStateOf(0) }   // 0 = Timetable, 1 = Calendar

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule") },
                actions = {
                    if (activeTab == 0) {
                        IconButton(onClick = onEditTimetable) {
                            Icon(Icons.Default.Edit, "Edit timetable")
                        }
                    } else {
                        IconButton(onClick = {
                            vm.openAddEvent(state.calendar.selectedDate ?: LocalDate.now())
                        }) {
                            Icon(Icons.Default.Add, "Add event")
                        }
                    }
                }
            )
        }
    ) { padding ->

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ── Tab row ────────────────────────────────────────────────────
            TabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick  = { activeTab = 0 },
                    text     = { Text("Timetable") },
                    icon     = { Icon(Icons.Outlined.TableChart, null,
                        modifier = Modifier.size(16.dp)) },
                )
                Tab(
                    selected = activeTab == 1,
                    onClick  = { activeTab = 1 },
                    text     = { Text("Calendar") },
                    icon     = { Icon(Icons.Outlined.CalendarMonth, null,
                        modifier = Modifier.size(16.dp)) },
                )
            }

            when (activeTab) {
                0 -> TimetableTab(state = state, vm = vm)
                1 -> CalendarTab(state = state, vm = vm)
            }
        }
    }

    // ── Add event sheet ────────────────────────────────────────────────────
    if (state.calendar.showAddEvent) {
        AddEventSheet(form = state.calendar.eventForm, vm = vm)
    }
}

// ── Timetable tab ─────────────────────────────────────────────────────────────

@Composable
private fun TimetableTab(
    state: ScheduleScreenState,
    vm:    ScheduleViewModel,
) {
    val tt = state.timetable

    if (tt.slots.isEmpty()) {
        EmptyState(
            icon    = Icons.Outlined.TableChart,
            message = "No timetable set up yet",
            sub     = "Tap the edit icon to set up your timetable",
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = state.activeDayTab - 1,
            edgePadding      = 0.dp,
        ) {
            days.forEach { (num, label) ->
                Tab(
                    selected = state.activeDayTab == num,
                    onClick  = { vm.onDayTabChange(num) },
                    text     = { Text(label) },
                )
            }
        }

        LazyColumn(
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier            = Modifier.fillMaxSize(),
        ) {
            items(tt.slots, key = { it.id }) { slot ->
                val cell = tt.grid[Pair(state.activeDayTab, slot.id)]
                if (cell?.isContinuation == true) return@items

                ReadOnlySlotRow(
                    startTime = slot.startTime,
                    endTime   = if ((cell?.spanSlots ?: 1) > 1) {
                        val startIdx = tt.slots.indexOfFirst { it.id == slot.id }
                        val endIdx   = startIdx + (cell?.spanSlots ?: 1) - 1
                        if (endIdx < tt.slots.size) tt.slots[endIdx].endTime else slot.endTime
                    } else slot.endTime,
                    cell      = cell,
                )
            }
        }
    }
}

@Composable
private fun ReadOnlySlotRow(
    startTime: String,
    endTime:   String,
    cell:      GridCell?,
) {
    val isFilled  = cell != null
    val cellColor = if (isFilled) runCatching {
        Color(android.graphics.Color.parseColor(cell!!.colorHex))
    }.getOrDefault(MaterialTheme.colorScheme.primary)
    else Color.Transparent

    val rowBaseColor = if (isFilled) cellColor.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    GlassCard(
        variant   = GlassVariant.Light,
        shape     = RoundedCornerShape(10.dp),
        baseColor = rowBaseColor,
        modifier  = Modifier
            .fillMaxWidth()
            .height(if (isFilled && (cell?.spanSlots ?: 1) > 1)
                (56 * (cell?.spanSlots ?: 1)).dp else 56.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
        ) {
            Column(modifier = Modifier.width(68.dp)) {
                Text(startTime, style = MaterialTheme.typography.labelMedium)
                Text(endTime,   style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (isFilled) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(cellColor)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        cell!!.subjectName,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    if ((cell.spanSlots) > 1) {
                        Text(
                            "${cell.spanSlots}-slot lab",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    "Free",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── Calendar tab ──────────────────────────────────────────────────────────────

@Composable
private fun CalendarTab(
    state: ScheduleScreenState,
    vm:    ScheduleViewModel,
) {
    val cal = state.calendar

    Column(modifier = Modifier.fillMaxSize()) {

        // Month navigation
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = { vm.onMonthChange(cal.displayMonth.minusMonths(1)) }) {
                Icon(Icons.Default.ChevronLeft, "Previous month")
            }
            Text(
                cal.displayMonth.format(monthFmt),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { vm.onMonthChange(cal.displayMonth.plusMonths(1)) }) {
                Icon(Icons.Default.ChevronRight, "Next month")
            }
        }

        // Day-of-week headers
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            dayHeaders.forEach { day ->
                Text(
                    day,
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Calendar grid
        CalendarGrid(
            month      = cal.displayMonth,
            days       = cal.days,
            selected   = cal.selectedDate,
            onSelect   = vm::onDateSelect,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Selected day detail
        cal.selectedDate?.let { date ->
            SelectedDayDetail(
                date          = date,
                events        = cal.selectedDayEvents,
                sessions      = cal.selectedDaySessions,
                onAddEvent    = { vm.openAddEvent(date) },
                onDeleteEvent = vm::deleteEvent,
                modifier      = Modifier.weight(1f),
            )
        } ?: run {
            Box(
                modifier         = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Tap a date to see details",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Calendar grid ─────────────────────────────────────────────────────────────

@Composable
private fun CalendarGrid(
    month:    YearMonth,
    days:     List<DayData>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val firstDayOfWeek = month.atDay(1).dayOfWeek.value  // 1=Mon, 7=Sun
    val totalCells     = firstDayOfWeek - 1 + month.lengthOfMonth()
    val rows           = (totalCells + 6) / 7

    val presentColor = Color(0xFF4CAF50)
    val absentColor  = MaterialTheme.colorScheme.error
    val today        = LocalDate.now()

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayIndex  = cellIndex - (firstDayOfWeek - 1)

                    if (dayIndex < 0 || dayIndex >= days.size) {
                        Box(modifier = Modifier.weight(1f).height(48.dp))
                    } else {
                        val dayData    = days[dayIndex]
                        val isSelected = dayData.date == selected
                        val isToday    = dayData.date == today

                        // Heatmap background color
                        val bgColor = when {
                            dayData.attendancePct == null -> Color.Transparent
                            dayData.attendancePct >= 75f  ->
                                lerp(Color.Transparent, presentColor.copy(alpha = 0.5f),
                                    (dayData.attendancePct - 75f) / 25f)
                            else ->
                                lerp(Color.Transparent, absentColor.copy(alpha = 0.4f),
                                    (75f - dayData.attendancePct) / 75f)
                        }

                        Box(
                            contentAlignment = Alignment.TopCenter,
                            modifier         = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor)
                                .then(
                                    if (isSelected) Modifier.border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(8.dp),
                                    ) else Modifier
                                )
                                .clickable { onSelect(dayData.date) },
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier            = Modifier.padding(top = 4.dp),
                            ) {
                                Text(
                                    "${dayData.date.dayOfMonth}",
                                    style      = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color      = when {
                                        isToday    -> MaterialTheme.colorScheme.primary
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else       -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                // Event dots
                                if (dayData.events.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier              = Modifier.padding(top = 2.dp),
                                    ) {
                                        dayData.events.take(3).forEach { event ->
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(eventColor(event.type)),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Selected day detail ───────────────────────────────────────────────────────

@Composable
private fun SelectedDayDetail(
    date:          LocalDate,
    events:        List<AcademicEventEntity>,
    sessions:      List<com.dip.attendify.ui.schedule.CalendarSession>,
    onAddEvent:    () -> Unit,
    onDeleteEvent: (AcademicEventEntity) -> Unit,
    modifier:      Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth(),
        ) {
            Text(
                date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onAddEvent) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Event")
            }
        }

        // Sessions for this day
        if (sessions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            sessions.forEach { session ->
                val statusColor = when (session.status) {
                    com.dip.attendify.data.entity.AttendanceStatus.PRESENT ->
                        Color(0xFF4CAF50)
                    com.dip.attendify.data.entity.AttendanceStatus.ABSENT ->
                        MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val subjectColor = runCatching {
                    Color(android.graphics.Color.parseColor(session.colorHex))
                }.getOrDefault(statusColor)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(subjectColor)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            session.subjectName,
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${session.startTime} – ${session.endTime}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.12f),
                    ) {
                        Text(
                            session.status.name.lowercase().replaceFirstChar { it.uppercase() },
                            style    = MaterialTheme.typography.labelSmall,
                            color    = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        }

        // Events
        if (events.isEmpty() && sessions.isEmpty()) {
            Text(
                "No sessions or events on this day",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (events.isNotEmpty()) {
            events.forEach { event ->
                EventRow(event = event, onDelete = { onDeleteEvent(event) })
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun EventRow(
    event:    AcademicEventEntity,
    onDelete: () -> Unit,
) {
    val color = eventColor(event.type)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(event.title,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Text(
                event.type.name.replace("_", " ").lowercase()
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.DeleteOutline, "Delete",
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp))
        }
    }
}

// ── Add event sheet ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventSheet(
    form: EventFormState,
    vm:   ScheduleViewModel,
) {
    var showDatePicker    by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    val dateFmt           = DateTimeFormatter.ofPattern("dd MMM yyyy")

    ModalBottomSheet(onDismissRequest = vm::closeAddEvent) {
        Column(
            modifier            = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Add Event", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value         = form.title,
                onValueChange = vm::onEventTitleChange,
                label         = { Text("Event title") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            // Type chips
            Text("Type", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                AcademicEventType.entries.forEach { type ->
                    FilterChip(
                        selected = form.type == type,
                        onClick  = { vm.onEventTypeChange(type) },
                        label    = {
                            Text(type.name.replace("_", " ").lowercase()
                                .replaceFirstChar { it.uppercase() },
                                maxLines = 1)
                        },
                    )
                }
            }

            // Date
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value         = form.date.format(dateFmt),
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Date") },
                    trailingIcon  = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, "Pick date")
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value         = form.endDate?.format(dateFmt) ?: "",
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("End date (opt)") },
                    trailingIcon  = {
                        IconButton(onClick = { showEndDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, "Pick end date")
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value         = form.note,
                onValueChange = vm::onEventNoteChange,
                label         = { Text("Note (optional)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            Button(
                onClick  = vm::saveEvent,
                enabled  = form.canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save Event") }
        }
    }

    if (showDatePicker) {
        DatePickerDialogWrapper(
            initial   = form.date,
            onPicked  = { vm.onEventDateChange(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showEndDatePicker) {
        DatePickerDialogWrapper(
            initial   = form.endDate ?: form.date,
            onPicked  = { vm.onEventEndDateChange(it); showEndDatePicker = false },
            onDismiss = { showEndDatePicker = false },
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun eventColor(type: AcademicEventType): Color = when (type) {
    AcademicEventType.EXAM                -> MaterialTheme.colorScheme.error
    AcademicEventType.SUBMISSION_DEADLINE -> Color(0xFFFFA726)
    AcademicEventType.HOLIDAY             -> Color(0xFF4CAF50)
    AcademicEventType.SEMESTER_BREAK      -> Color(0xFF42A5F5)
    AcademicEventType.OTHER               -> MaterialTheme.colorScheme.outline
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogWrapper(
    initial:   LocalDate,
    onPicked:  (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton    = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    onPicked(LocalDate.ofEpochDay(millis / 86_400_000L))
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = pickerState) }
}

@Composable
private fun EmptyState(
    icon:    androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    sub:     String,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            Icon(icon, null,
                modifier = Modifier.size(52.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Text(message, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}