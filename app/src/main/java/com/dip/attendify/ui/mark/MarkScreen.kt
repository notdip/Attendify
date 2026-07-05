package com.dip.attendify.ui.mark

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBoxValue.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dip.attendify.data.entity.AttendanceStatus
import com.dip.attendify.data.entity.SessionType
import com.dip.attendify.data.entity.SubjectEntity
import com.dip.attendify.data.entity.TimeSlotEntity
import com.dip.attendify.ui.common.GlassCard
import com.dip.attendify.ui.common.NoSessionsIllustration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val headerDateFmt  = DateTimeFormatter.ofPattern("d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkScreen(
    vm: MarkViewModel = hiltViewModel(),
) {
    val state    by vm.state.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val slots    by vm.slots.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            DateNavigationBar(
                date           = state.selectedDate,
                semesterStart  = state.semesterStart,
                semesterEnd    = state.semesterEnd,
                onPrevious     = vm::previousDay,
                onNext         = vm::nextDay,
                onPick         = vm::onDateChange,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::openAddSheet) {
                Icon(Icons.Default.Add, "Add session")
            }
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.sessions.isEmpty() -> {
                    EmptyDayState(
                        date     = state.selectedDate,
                        onAdd    = vm::openAddSheet,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    SessionList(
                        sessions  = state.sessions,
                        onMark    = { idx, status -> vm.markSession(idx, status) },
                        onDismiss = { idx -> vm.dismissSession(idx) },
                        onClear   = { idx -> vm.clearAttendance(idx) },
                        onNote    = { idx -> vm.openNoteDialog(idx) },
                    )
                }
            }
        }
    }

    // ── Add session sheet ─────────────────────
    if (state.showAddSheet) {
        AddSessionSheet(
            form      = state.addForm,
            subjects  = subjects,
            slots     = slots,
            vm        = vm,
        )
    }

    // ── Note dialog ───────────────────────────
    state.showNoteDialog?.let { idx ->
        val session = state.sessions.getOrNull(idx)
        NoteDialog(
            initial   = session?.note ?: "",
            onSave    = { note -> vm.saveNote(idx, note) },
            onDismiss = vm::closeNoteDialog,
        )
    }
}

// ── Date navigation bar ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateNavigationBar(
    date:          LocalDate,
    semesterStart: LocalDate?,
    semesterEnd:   LocalDate?,
    onPrevious:    () -> Unit,
    onNext:        () -> Unit,
    onPick:        (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val isToday       = date == LocalDate.now()
    val atStart       = semesterStart != null && !date.isAfter(semesterStart)
    val atEnd         = semesterEnd   != null && !date.isBefore(semesterEnd)

    Surface(tonalElevation = 2.dp) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onPrevious, enabled = !atStart) {
                Icon(Icons.Default.ChevronLeft, "Previous day")
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.clickable { showPicker = true },
            ) {
                Text(
                    text  = if (isToday) "Today" else
                        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text  = date.format(headerDateFmt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick  = onNext,
                enabled  = !atEnd && date.isBefore(LocalDate.now()),
            ) {
                Icon(Icons.Default.ChevronRight, "Next day")
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * 86_400_000L,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = LocalDate.ofEpochDay(utcTimeMillis / 86_400_000L)
                    if (semesterStart != null && d.isBefore(semesterStart)) return false
                    if (semesterEnd   != null && d.isAfter(semesterEnd))    return false
                    return utcTimeMillis <= System.currentTimeMillis()
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton    = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onPick(LocalDate.ofEpochDay(millis / 86_400_000L))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = pickerState) }
    }
}

// ── Session list ──────────────────────────────────────────────────────────────

@Composable
private fun SessionList(
    sessions:  List<MarkSession>,
    onMark:    (Int, AttendanceStatus) -> Unit,
    onDismiss: (Int) -> Unit,
    onClear:   (Int) -> Unit,
    onNote:    (Int) -> Unit,
) {
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(sessions, key = { _, s -> "${s.subjectId}-${s.startSlotId}" }) { idx, session ->
            SessionCard(
                session   = session,
                onPresent = { onMark(idx, AttendanceStatus.PRESENT) },
                onAbsent  = { onMark(idx, AttendanceStatus.ABSENT) },
                onDismiss = { onDismiss(idx) },
                onClear   = { onClear(idx) },
                onNote    = { onNote(idx) },
            )
        }
    }
}

// ── Session card ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionCard(
    session:   MarkSession,
    onPresent: () -> Unit,
    onAbsent:  () -> Unit,
    onDismiss: () -> Unit,
    onClear:   () -> Unit,
    onNote:    () -> Unit,
) {
    val subjectColor = runCatching {
        Color(android.graphics.Color.parseColor(session.subjectColor))
    }.getOrDefault(MaterialTheme.colorScheme.primary)

    val isCancelled = session.status == AttendanceStatus.CANCELLED

    // Swipe-to-dismiss only for timetable sessions that aren't yet marked
    if (session.fromTimetable && session.status == null) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == EndToStart) { onDismiss(); true } else false
            }
        )
        SwipeToDismissBox(
            state            = dismissState,
            backgroundContent = {
                Box(
                    modifier          = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment  = Alignment.CenterEnd,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(end = 20.dp),
                    ) {
                        Icon(
                            Icons.Default.EventBusy, "Cancel class",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Class cancelled",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            },
            enableDismissFromEndToStart = true,
            enableDismissFromStartToEnd = false,
        ) {
            SessionCardContent(
                session      = session,
                subjectColor = subjectColor,
                isCancelled  = false,
                onPresent    = onPresent,
                onAbsent     = onAbsent,
                onClear      = onClear,
                onNote       = onNote,
            )
        }
    } else {
        SessionCardContent(
            session      = session,
            subjectColor = subjectColor,
            isCancelled  = isCancelled,
            onPresent    = onPresent,
            onAbsent     = onAbsent,
            onClear      = onClear,
            onNote       = onNote,
        )
    }
}

@Composable
private fun SessionCardContent(
    session:      MarkSession,
    subjectColor: Color,
    isCancelled:  Boolean,
    onPresent:    () -> Unit,
    onAbsent:     () -> Unit,
    onClear:      () -> Unit,
    onNote:       () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (isCancelled) 0.45f else 1f,
        label       = "cancelled_alpha",
    )

    GlassCard(
        shape    = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ── Top row: color dot + name + time + type badge ──
            Row(verticalAlignment = Alignment.CenterVertically) {

                // Color indicator
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(subjectColor)
                )

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = session.subjectName,
                        style    = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text  = "${session.startTime} – ${session.endTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Session type badge (only for non-regular)
                if (session.sessionType != SessionType.REGULAR) {
                    SessionTypeBadge(session.sessionType)
                    Spacer(Modifier.width(8.dp))
                }

                // Clear + Note icons (visible when session is marked)
                if (session.status != null && !isCancelled) {
                    IconButton(
                        onClick  = onClear,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Replay,
                            contentDescription = "Clear attendance",
                            modifier           = Modifier.size(18.dp),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick  = onNote,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            if (session.note != null) Icons.Filled.Note
                            else Icons.Outlined.Note,
                            contentDescription = "Note",
                            modifier           = Modifier.size(18.dp),
                            tint               = if (session.note != null) subjectColor
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Status row ──────────────────────────
            if (isCancelled) {
                Text(
                    text  = "Class cancelled for today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    StatusButton(
                        label     = "Present",
                        selected  = session.status == AttendanceStatus.PRESENT,
                        color     = Color(0xFF4CAF50),
                        icon      = Icons.Default.Check,
                        onClick   = onPresent,
                        modifier  = Modifier.weight(1f),
                    )
                    StatusButton(
                        label     = "Absent",
                        selected  = session.status == AttendanceStatus.ABSENT,
                        color     = MaterialTheme.colorScheme.error,
                        icon      = Icons.Default.Close,
                        onClick   = onAbsent,
                        modifier  = Modifier.weight(1f),
                    )
                }
            }

            // ── Note preview ────────────────────────
            session.note?.let { note ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text     = note,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Status button ─────────────────────────────────────────────────────────────

@Composable
private fun StatusButton(
    label:    String,
    selected: Boolean,
    color:    Color,
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tactile: selected = solid filled, unselected = outlined ghost
    val containerColor = if (selected) color.copy(alpha = 0.18f)
    else Color.Transparent
    val contentColor   = if (selected) color
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val borderColor    = if (selected) color.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val borderWidth    = if (selected) 1.5.dp else 1.dp

    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(12.dp),
        color    = containerColor,
        modifier = modifier.height(44.dp),
        border   = BorderStroke(borderWidth, borderColor),
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier              = Modifier.fillMaxSize(),
        ) {
            Icon(
                icon, label,
                tint     = contentColor,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style      = MaterialTheme.typography.labelMedium,
                color      = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

// ── Session type badge ────────────────────────────────────────────────────────

@Composable
private fun SessionTypeBadge(type: SessionType) {
    val (label, color) = when (type) {
        SessionType.PROXY     -> "Proxy"  to Color(0xFF9C27B0)
        SessionType.EXTRA     -> "Extra"  to Color(0xFF2196F3)
        SessionType.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.error
        SessionType.REGULAR   -> return
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.18f),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall,
            color    = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ── Add session sheet ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSessionSheet(
    form:     AddSessionForm,
    subjects: List<SubjectEntity>,
    slots:    List<TimeSlotEntity>,
    vm:       MarkViewModel,
) {
    ModalBottomSheet(onDismissRequest = vm::closeAddSheet) {
        Column(
            modifier            = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add Session", style = MaterialTheme.typography.titleMedium)

            // Subject picker
            FormLabel("Subject")
            if (subjects.isEmpty()) {
                Text(
                    "No subjects found. Add subjects from Home first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                DropdownSelector(
                    options       = subjects.map { it.id to it.name },
                    selectedId    = form.subjectId,
                    placeholder   = "Select subject",
                    onSelect      = vm::onAddSubjectChange,
                )
            }

            // Session type
            FormLabel("Session type")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SessionType.PROXY   to "Proxy",
                    SessionType.EXTRA   to "Extra",
                    SessionType.REGULAR to "Regular",
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = form.sessionType == type,
                        onClick  = { vm.onAddSessionTypeChange(type) },
                        label    = { Text(label) },
                    )
                }
            }

            // Slot range
            if (slots.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        FormLabel("Start slot")
                        DropdownSelector(
                            options     = slots.map { it.id to "${it.startTime}" },
                            selectedId  = form.startSlotId,
                            placeholder = "Start",
                            onSelect    = vm::onAddStartSlotChange,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FormLabel("End slot")
                        DropdownSelector(
                            options     = slots
                                .filter { it.slotOrder >= (slots.find { s -> s.id == form.startSlotId }?.slotOrder ?: 0) }
                                .map { it.id to "${it.endTime}" },
                            selectedId  = form.endSlotId,
                            placeholder = "End",
                            onSelect    = vm::onAddEndSlotChange,
                        )
                    }
                }
            }

            // Note
            OutlinedTextField(
                value         = form.note,
                onValueChange = vm::onAddNoteChange,
                label         = { Text("Note (optional)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            Button(
                onClick  = vm::submitAddSession,
                enabled  = form.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Add as Present")
            }
        }
    }
}

// ── Note dialog ───────────────────────────────────────────────────────────────

@Composable
private fun NoteDialog(
    initial:   String,
    onSave:    (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Session Note") },
        text    = {
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                placeholder   = { Text("What was covered, assignments given...") },
                minLines      = 3,
                modifier      = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyDayState(
    date:     LocalDate,
    onAdd:    () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday  = date == LocalDate.now()
    val dayLabel = if (isToday) "today"
    else date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).lowercase()

    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NoSessionsIllustration()
        Text(
            "No classes $dayLabel",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Nothing in the timetable. Add a session if a class happened.",
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onAdd) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add Session")
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun FormLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    options:     List<Pair<Int, String>>,
    selectedId:  Int?,
    placeholder: String,
    onSelect:    (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedId }?.second ?: placeholder

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value         = selectedLabel,
            onValueChange = {},
            readOnly      = true,
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier      = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (id, label) ->
                DropdownMenuItem(
                    text    = { Text(label) },
                    onClick = { onSelect(id); expanded = false },
                )
            }
        }
    }
}