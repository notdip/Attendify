package com.dip.attendify.ui.subject

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dip.attendify.data.entity.*
import com.dip.attendify.ui.common.GlassCard
import com.dip.attendify.ui.theme.AtRiskRed
import com.dip.attendify.ui.theme.AtRiskRedContainer
import com.dip.attendify.ui.theme.OnAtRiskRedContainer
import com.dip.attendify.ui.theme.AttendanceGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("dd MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailScreen(
    subjectId: Int,
    onBack:    () -> Unit,
    vm: SubjectDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val subjectColor = runCatching {
        Color(android.graphics.Color.parseColor(state.subject?.colorHex ?: "#5C6BC0"))
    }.getOrDefault(MaterialTheme.colorScheme.primary)

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(state.subject?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = vm::openTaskForm) {
                        Icon(Icons.Default.AddTask, "Add Task")
                    }
                }
            )
        }
    ) { padding ->

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding      = PaddingValues(
                start  = 16.dp, end = 16.dp,
                top    = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Stats header ───────────────────────────────────────────────
            item {
                StatsHeaderCard(
                    state        = state,
                    subjectColor = subjectColor,
                )
            }

            // ── Trend line chart ───────────────────────────────────────────
            if (state.trendPoints.size >= 2) {
                item {
                    SectionLabel("Attendance Trend")
                    Spacer(Modifier.height(8.dp))
                    TrendLineChart(
                        points       = state.trendPoints,
                        target       = state.target.toFloat(),
                        subjectColor = subjectColor,
                    )
                }
            }

            // ── Weekly bar chart ───────────────────────────────────────────
            if (state.weekBars.isNotEmpty()) {
                item {
                    SectionLabel("Weekly Breakdown")
                    Spacer(Modifier.height(8.dp))
                    WeeklyBarChart(
                        bars         = state.weekBars,
                        subjectColor = subjectColor,
                    )
                }
            }

            // ── Tasks section ──────────────────────────────────────────────
            item {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    SectionLabel("Tasks")
                    TextButton(onClick = vm::openTaskForm) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            if (state.tasks.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            "No tasks yet. Add assignments, lab reports, and more.",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(state.tasks, key = { "task_${it.id}" }) { task ->
                    TaskCard(
                        task           = task,
                        onStatusChange = { status -> vm.updateTaskStatus(task, status) },
                        onDelete       = { vm.deleteTask(task) },
                    )
                }
            }

            // ── Session history ────────────────────────────────────────────
            if (state.sessionHistory.isNotEmpty()) {
                item { SectionLabel("Session History") }
                items(state.sessionHistory, key = { "att_${it.id}" }) { record ->
                    SessionHistoryRow(record = record)
                }
            }
        }
    }

    // ── Task form sheet ────────────────────────────────────────────────────
    if (state.showTaskForm) {
        TaskFormSheet(form = state.taskForm, vm = vm)
    }
}

// ── Stats header ──────────────────────────────────────────────────────────────

@Composable
private fun StatsHeaderCard(
    state:        SubjectDetailState,
    subjectColor: Color,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Top row: big percentage + counts
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        "${state.percentage.toInt()}%",
                        style      = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color      = if (state.isAtRisk) AtRiskRed
                        else subjectColor,
                    )
                    Text(
                        "attendance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    CountPill("Present",   state.present,   Color(0xFF4CAF50))
                    CountPill("Absent",    state.absent,    MaterialTheme.colorScheme.error)
                    CountPill("Cancelled", state.cancelled, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Progress bar
            val animatedPct by animateFloatAsState(
                targetValue   = state.percentage / 100f,
                animationSpec = tween(700, easing = EaseOutCubic),
                label         = "subject_detail_bar",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedPct)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (state.isAtRisk) SolidColor(AtRiskRed)
                            else Brush.horizontalGradient(
                                listOf(AttendanceGreen, subjectColor)
                            )
                        )
                )
            }

            Spacer(Modifier.height(12.dp))

            // Skip / recover hint
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (state.isAtRisk)
                    AtRiskRedContainer
                else
                    subjectColor.copy(alpha = 0.1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        if (state.isAtRisk) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint    = if (state.isAtRisk) OnAtRiskRedContainer
                        else subjectColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = if (state.isAtRisk && state.mustAttend > 0)
                            "Attend ${state.mustAttend} more to reach ${state.target}%"
                        else if (state.canSkip > 0)
                            "You can safely skip ${state.canSkip} more class${if (state.canSkip > 1) "es" else ""}"
                        else
                            "Attendance looks good",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.isAtRisk) OnAtRiskRedContainer
                        else subjectColor,
                    )
                }
            }

            // Lec / Lab split for BOTH-type subjects
            if (state.lecStats != null && state.labStats != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    SplitStatCard(
                        label    = "Lectures",
                        stats    = state.lecStats,
                        target   = state.target,
                        color    = subjectColor,
                        modifier = Modifier.weight(1f),
                    )
                    SplitStatCard(
                        label    = "Labs",
                        stats    = state.labStats,
                        target   = state.target,
                        color    = subjectColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitStatCard(
    label:    String,
    stats:    com.dip.attendify.domain.AttendanceCalculator.SplitStats,
    target:   Int,
    color:    Color,
    modifier: Modifier = Modifier,
) {
    val isAtRisk     = stats.isAtRisk
    val bgColor      = if (isAtRisk) AtRiskRedContainer
    else color.copy(alpha = 0.08f)
    val primaryColor = if (isAtRisk) OnAtRiskRedContainer else color

    Surface(shape = RoundedCornerShape(10.dp), color = bgColor, modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${stats.percentage.toInt()}%",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = primaryColor,
            )
            Text(
                "${stats.present}P  •  ${stats.absent}A",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (isAtRisk && stats.mustAttend > 0) {
                Text("Need ${stats.mustAttend} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor)
            } else if (stats.canSkip > 0) {
                Text("Can skip ${stats.canSkip}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CountPill(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Trend line chart ──────────────────────────────────────────────────────────

@Composable
private fun TrendLineChart(
    points:       List<TrendPoint>,
    target:       Float,
    subjectColor: Color,
) {
    val targetColor = MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(16.dp)
                .drawBehind {
                    if (points.size < 2) return@drawBehind

                    val w        = size.width
                    val h        = size.height
                    val minPct   = 0f
                    val maxPct   = 100f
                    val xStep    = w / (points.size - 1).toFloat()

                    fun xOf(i: Int) = i * xStep
                    fun yOf(pct: Float) = h - (pct - minPct) / (maxPct - minPct) * h

                    // Target line
                    val ty = yOf(target)
                    drawLine(
                        color       = targetColor,
                        start       = Offset(0f, ty),
                        end         = Offset(w, ty),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect  = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 4.dp.toPx())
                        ),
                    )

                    // Fill path
                    val fillPath = Path().apply {
                        moveTo(xOf(0), yOf(points[0].percentage))
                        points.forEachIndexed { i, p -> lineTo(xOf(i), yOf(p.percentage)) }
                        lineTo(xOf(points.lastIndex), h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        path  = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(subjectColor.copy(alpha = 0.25f), Color.Transparent)
                        ),
                    )

                    // Line
                    for (i in 0 until points.lastIndex) {
                        val color = if (points[i].percentage < target) AtRiskRed else subjectColor
                        drawLine(
                            color       = color,
                            start       = Offset(xOf(i), yOf(points[i].percentage)),
                            end         = Offset(xOf(i + 1), yOf(points[i + 1].percentage)),
                            strokeWidth = 2.5.dp.toPx(),
                            cap         = StrokeCap.Round,
                        )
                    }

                    // Dots
                    points.forEachIndexed { i, p ->
                        drawCircle(
                            color  = if (p.percentage < target) AtRiskRed else subjectColor,
                            radius = 3.5.dp.toPx(),
                            center = Offset(xOf(i), yOf(p.percentage)),
                        )
                    }
                }
        )

        // X-axis labels — first + last date
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(points.first().date.format(dateFmt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(points.last().date.format(dateFmt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Weekly bar chart ──────────────────────────────────────────────────────────

@Composable
private fun WeeklyBarChart(
    bars:         List<WeekBar>,
    subjectColor: Color,
) {
    val maxVal = bars.maxOf { it.present + it.absent }.coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment     = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            ) {
                bars.forEach { bar ->
                    val totalFrac  = (bar.present + bar.absent).toFloat() / maxVal
                    val presentFrac = if (bar.present + bar.absent == 0) 0f
                    else bar.present.toFloat() / (bar.present + bar.absent)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier            = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(totalFrac)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        ) {
                            // Absent portion (bottom)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.errorContainer)
                            )
                            // Present portion (top overlay)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(presentFrac)
                                    .align(Alignment.TopCenter)
                                    .background(subjectColor)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Week labels
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                bars.forEach { bar ->
                    Text(
                        bar.weekLabel,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(color = subjectColor, label = "Present")
                LegendDot(color = MaterialTheme.colorScheme.errorContainer, label = "Absent")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Task card ─────────────────────────────────────────────────────────────────

@Composable
private fun TaskCard(
    task:          TaskEntity,
    onStatusChange: (TaskStatus) -> Unit,
    onDelete:      () -> Unit,
) {
    var showDelete by remember { mutableStateOf(false) }
    val isDone = task.status == TaskStatus.DONE

    val priorityColor = when (task.priority) {
        TaskPriority.HIGH   -> MaterialTheme.colorScheme.error
        TaskPriority.MEDIUM -> Color(0xFFFFA726)
        TaskPriority.LOW    -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(12.dp),
        ) {
            // Checkbox
            Checkbox(
                checked         = isDone,
                onCheckedChange = {
                    onStatusChange(if (it) TaskStatus.DONE else TaskStatus.PENDING)
                }
            )

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style          = MaterialTheme.typography.bodyMedium,
                    fontWeight     = FontWeight.Medium,
                    maxLines       = 1,
                    overflow       = TextOverflow.Ellipsis,
                    color          = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    // Type chip
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            task.type.name.replace("_", " ").lowercase()
                                .replaceFirstChar { it.uppercase() },
                            style    = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    // Due date
                    task.dueDate?.let { epochDay ->
                        val due = LocalDate.ofEpochDay(epochDay)
                        val isOverdue = due.isBefore(LocalDate.now()) && !isDone
                        Text(
                            due.format(dateFmt),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Priority dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(priorityColor)
            )

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick  = { showDelete = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Default.DeleteOutline, "Delete",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp))
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title   = { Text("Delete task?") },
            text    = { Text("\"${task.title}\" will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDelete = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Task form sheet ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFormSheet(
    form: TaskFormState,
    vm:   SubjectDetailViewModel,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = vm::closeTaskForm) {
        Column(
            modifier            = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Add Task", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value         = form.title,
                onValueChange = vm::onTaskTitleChange,
                label         = { Text("Task title") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            // Type
            Text("Type", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TaskType.entries) { type ->
                    FilterChip(
                        selected = form.type == type,
                        onClick  = { vm.onTaskTypeChange(type) },
                        label    = {
                            Text(type.name.replace("_", " ").lowercase()
                                .replaceFirstChar { it.uppercase() })
                        },
                    )
                }
            }

            // Priority
            Text("Priority", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.forEach { priority ->
                    FilterChip(
                        selected = form.priority == priority,
                        onClick  = { vm.onTaskPriorityChange(priority) },
                        label    = {
                            Text(priority.name.lowercase()
                                .replaceFirstChar { it.uppercase() })
                        },
                    )
                }
            }

            // Due date
            OutlinedTextField(
                value         = form.dueDate?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "",
                onValueChange = {},
                readOnly      = true,
                label         = { Text("Due date (optional)") },
                trailingIcon  = {
                    Row {
                        if (form.dueDate != null) {
                            IconButton(onClick = { vm.onTaskDueDateChange(null) }) {
                                Icon(Icons.Default.Clear, "Clear date",
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, "Pick date")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value         = form.note,
                onValueChange = vm::onTaskNoteChange,
                label         = { Text("Note (optional)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            Button(
                onClick  = vm::saveTask,
                enabled  = form.canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Add Task") }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = form.dueDate?.toEpochDay()?.times(86_400_000L)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton    = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        vm.onTaskDueDateChange(LocalDate.ofEpochDay(millis / 86_400_000L))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = pickerState) }
    }
}

// ── Session history row ───────────────────────────────────────────────────────

@Composable
private fun SessionHistoryRow(record: AttendanceEntity) {
    val date      = LocalDate.ofEpochDay(record.date)
    val statusColor = when (record.status) {
        AttendanceStatus.PRESENT   -> Color(0xFF4CAF50)
        AttendanceStatus.ABSENT    -> MaterialTheme.colorScheme.error
        AttendanceStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())}, ${date.format(dateFmt)}",
                style = MaterialTheme.typography.bodySmall,
            )
            record.note?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = statusColor.copy(alpha = 0.12f),
        ) {
            Text(
                record.status.name.lowercase().replaceFirstChar { it.uppercase() },
                style    = MaterialTheme.typography.labelSmall,
                color    = statusColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}