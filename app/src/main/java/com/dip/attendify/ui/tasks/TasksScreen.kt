package com.dip.attendify.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBoxValue.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dip.attendify.data.entity.*
import com.dip.attendify.ui.common.GlassCard
import com.dip.attendify.ui.common.NoTasksIllustration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("dd MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onSubjectClick: (Int) -> Unit,
    vm: TasksViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                actions = {
                    if (state.filter != TaskFilter()) {
                        IconButton(onClick = vm::clearFilters) {
                            Icon(Icons.Default.FilterAltOff, "Clear filters")
                        }
                    }
                    IconButton(onClick = vm::toggleShowDone) {
                        Icon(
                            if (state.filter.showDone) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = "Toggle done",
                        )
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

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // ── Summary chips ──────────────────────────────────────────────
            if (state.pendingCount > 0 || state.overdueCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (state.pendingCount > 0) {
                        SummaryChip(
                            label = "${state.pendingCount} pending",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (state.overdueCount > 0) {
                        SummaryChip(
                            label = "${state.overdueCount} overdue",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // ── Filter row ─────────────────────────────────────────────────
            FilterRow(state = state, vm = vm)

            // ── Task list ──────────────────────────────────────────────────
            if (state.filteredTasks.isEmpty()) {
                EmptyTasksState(
                    hasFilters = state.filter != TaskFilter(),
                    modifier   = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(
                        start  = 16.dp,
                        end    = 16.dp,
                        top    = 8.dp,
                        bottom = 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        state.filteredTasks,
                        key = { "${it.task.id}_${it.task.status.name}" },
                    ) { item ->
                        SwipeableTaskCard(
                            item           = item,
                            onSwipeDone    = { vm.markDone(item.task) },
                            onCycleStatus  = { vm.cycleStatus(item.task) },
                            onDelete       = { vm.deleteTask(item.task) },
                            onSubjectClick = { onSubjectClick(item.task.subjectId) },
                        )
                    }
                }
            }
        }
    }
}

// ── Filter row ────────────────────────────────────────────────────────────────

@Composable
private fun FilterRow(state: TasksScreenState, vm: TasksViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        // Subject filter
        state.subjects.forEach { subject ->
            val selected = state.filter.subjectId == subject.id
            FilterChip(
                selected = selected,
                onClick  = { vm.setSubjectFilter(if (selected) null else subject.id) },
                label    = { Text(subject.shortName) },
                leadingIcon = if (selected) {{
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                }} else null,
            )
        }

        // Type filter
        TaskType.entries.forEach { type ->
            val selected = state.filter.type == type
            FilterChip(
                selected = selected,
                onClick  = { vm.setTypeFilter(if (selected) null else type) },
                label    = {
                    Text(type.name.replace("_", " ").lowercase()
                        .replaceFirstChar { it.uppercase() })
                },
            )
        }

        // Priority filter
        TaskPriority.entries.forEach { priority ->
            val selected = state.filter.priority == priority
            FilterChip(
                selected = selected,
                onClick  = { vm.setPriorityFilter(if (selected) null else priority) },
                label    = {
                    Text(priority.name.lowercase().replaceFirstChar { it.uppercase() })
                },
            )
        }
    }
}

// ── Swipeable task card ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskCard(
    item:           TaskItem,
    onSwipeDone:    () -> Unit,
    onCycleStatus:  () -> Unit,
    onDelete:       () -> Unit,
    onSubjectClick: () -> Unit,
) {
    val isDone = item.task.status == TaskStatus.DONE

    // Only swipeable if not already done
    if (!isDone) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == EndToStart) { onSwipeDone(); true } else false
            }
        )
        SwipeToDismissBox(
            state             = dismissState,
            backgroundContent = {
                Box(
                    modifier         = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF4CAF50)),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(end = 20.dp),
                    ) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mark done", color = Color.White,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            enableDismissFromEndToStart = true,
            enableDismissFromStartToEnd = false,
        ) {
            TaskCard(
                item           = item,
                onCycleStatus  = onCycleStatus,
                onDelete       = onDelete,
                onSubjectClick = onSubjectClick,
            )
        }
    } else {
        TaskCard(
            item           = item,
            onCycleStatus  = onCycleStatus,
            onDelete       = onDelete,
            onSubjectClick = onSubjectClick,
        )
    }
}

// ── Task card ─────────────────────────────────────────────────────────────────

@Composable
private fun TaskCard(
    item:           TaskItem,
    onCycleStatus:  () -> Unit,
    onDelete:       () -> Unit,
    onSubjectClick: () -> Unit,
) {
    val task     = item.task
    val isDone   = task.status == TaskStatus.DONE
    val today    = LocalDate.now()
    val isOverdue = !isDone &&
            task.dueDate != null &&
            LocalDate.ofEpochDay(task.dueDate).isBefore(today)

    val subjectColor = runCatching {
        Color(android.graphics.Color.parseColor(item.subjectColor))
    }.getOrDefault(MaterialTheme.colorScheme.primary)

    val priorityColor = when (task.priority) {
        TaskPriority.HIGH   -> MaterialTheme.colorScheme.error
        TaskPriority.MEDIUM -> Color(0xFFFFA726)
        TaskPriority.LOW    -> MaterialTheme.colorScheme.outline
    }

    val statusColor by animateColorAsState(
        targetValue   = when (task.status) {
            TaskStatus.PENDING     -> MaterialTheme.colorScheme.surfaceContainerLow
            TaskStatus.IN_PROGRESS -> Color(0xFFFFA726).copy(alpha = 0.10f)
            TaskStatus.DONE        -> Color(0xFF4CAF50).copy(alpha = 0.08f)
        },
        animationSpec = tween(300),
        label         = "task_status_color",
    )

    var showDelete by remember { mutableStateOf(false) }

    GlassCard(
        shape     = RoundedCornerShape(12.dp),
        baseColor = statusColor,
        modifier  = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(12.dp),
        ) {
            // Status cycle button
            IconButton(
                onClick  = onCycleStatus,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = when (task.status) {
                        TaskStatus.PENDING     -> Icons.Outlined.RadioButtonUnchecked
                        TaskStatus.IN_PROGRESS -> Icons.Outlined.Pending
                        TaskStatus.DONE        -> Icons.Filled.CheckCircle
                    },
                    contentDescription = "Status",
                    tint               = when (task.status) {
                        TaskStatus.PENDING     -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        TaskStatus.IN_PROGRESS -> Color(0xFFFFA726)
                        TaskStatus.DONE        -> Color(0xFF4CAF50)
                    },
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style           = MaterialTheme.typography.bodyMedium,
                    fontWeight      = FontWeight.Medium,
                    maxLines        = 1,
                    overflow        = TextOverflow.Ellipsis,
                    textDecoration  = if (isDone) TextDecoration.LineThrough else null,
                    color           = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    // Subject chip
                    Surface(
                        shape   = RoundedCornerShape(4.dp),
                        color   = subjectColor.copy(alpha = 0.12f),
                        onClick = onSubjectClick,
                    ) {
                        Text(
                            item.subjectName,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = subjectColor,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    // Type chip
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            task.type.name.replace("_", " ").lowercase()
                                .replaceFirstChar { it.uppercase() },
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    // Due date
                    task.dueDate?.let { epochDay ->
                        Text(
                            LocalDate.ofEpochDay(epochDay).format(dateFmt),
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
                Icon(
                    Icons.Default.DeleteOutline, "Delete",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
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

// ── Summary chip ──────────────────────────────────────────────────────────────

@Composable
private fun SummaryChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.18f),
    ) {
        Text(
            label,
            style    = MaterialTheme.typography.labelSmall,
            color    = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyTasksState(hasFilters: Boolean, modifier: Modifier) {
    Column(
        modifier            = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NoTasksIllustration()
        Spacer(Modifier.height(16.dp))
        Text(
            if (hasFilters) "No tasks match your filters"
            else "No tasks yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (hasFilters) "Try clearing some filters"
            else "Add tasks from any subject's detail screen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}