package com.dip.attendify.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.min

@Composable
fun HomeScreen(
    onSubjectClick:   (Int) -> Unit,
    onManageSubjects: () -> Unit,
    onNewSemester:    () -> Unit,
    onSummary:        () -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    when {
        state.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        state.semester == null -> {
            WelcomeEmptyState(onCreateSemester = onNewSemester)
        }

        else -> {
            HomeContent(
                state            = state,
                onSubjectClick   = onSubjectClick,
                onManageSubjects = onManageSubjects,
                onNewSemester    = onNewSemester,
                onSummary        = onSummary,
            )
        }
    }
}

// ── Welcome / empty state ─────────────────────────────────────────────────────

@Composable
private fun WelcomeEmptyState(onCreateSemester: () -> Unit) {
    Column(
        modifier             = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement  = Arrangement.Center,
        horizontalAlignment  = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.School,
            contentDescription = null,
            modifier           = Modifier.size(72.dp),
            tint               = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Welcome to Attendify",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Set up your semester to start tracking attendance, managing tasks, and staying on top of your academics.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick  = onCreateSemester,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Create Semester")
        }
    }
}

// ── Main content ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state:            HomeScreenState,
    onSubjectClick:   (Int) -> Unit,
    onManageSubjects: () -> Unit,
    onNewSemester:    () -> Unit,
    onSummary:        () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.semester?.name ?: "Home") },
                actions = {
                    IconButton(onClick = onManageSubjects) {
                        Icon(Icons.Outlined.MenuBook, "Manage Subjects")
                    }
                    IconButton(onClick = onSummary) {
                        Icon(Icons.Outlined.Assessment, "Semester Summary")
                    }
                    IconButton(onClick = onNewSemester) {
                        Icon(Icons.Outlined.Add, "New Semester")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding      = PaddingValues(
                start  = 16.dp, end = 16.dp,
                top    = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Overall attendance ring + breakdown ────────────────────────
            item {
                OverallAttendanceCard(
                    percent   = state.overallPercent,
                    present   = state.totalPresent,
                    absent    = state.totalAbsent,
                    cancelled = state.totalCancelled,
                    target    = state.semester?.targetAttendancePercent ?: 75,
                )
            }

            // ── Today's schedule preview ───────────────────────────────────
            if (state.todaySessions.isNotEmpty()) {
                item {
                    TodayScheduleCard(sessions = state.todaySessions)
                }
            }

            // ── Quick stats row ────────────────────────────────────────────
            item {
                QuickStatsRow(
                    pendingTasks = state.pendingTaskCount,
                    streak       = state.streak,
                )
            }

            // ── Subject cards header ───────────────────────────────────────
            item {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Subjects",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onManageSubjects) {
                        Text("Manage")
                    }
                }
            }

            // ── Subject cards ──────────────────────────────────────────────
            if (state.subjectSummaries.isEmpty()) {
                item {
                    EmptySubjectsHint(onManageSubjects = onManageSubjects)
                }
            } else {
                items(state.subjectSummaries, key = { it.subject.id }) { summary ->
                    SubjectAttendanceCard(
                        summary  = summary,
                        target   = state.semester?.targetAttendancePercent ?: 75,
                        onClick  = { onSubjectClick(summary.subject.id) },
                    )
                }
            }
        }
    }
}

// ── Overall attendance card ───────────────────────────────────────────────────

@Composable
private fun OverallAttendanceCard(
    percent:   Float,
    present:   Int,
    absent:    Int,
    cancelled: Int,
    target:    Int,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor   = MaterialTheme.colorScheme.surfaceVariant
    val errorColor   = MaterialTheme.colorScheme.error
    val isAtRisk     = percent < target

    val animatedPercent by animateFloatAsState(
        targetValue   = percent / 100f,
        animationSpec = tween(800, easing = EaseOutCubic),
        label         = "overall_ring",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Ring
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(140.dp)
                    .drawBehind {
                        val stroke     = 14.dp.toPx()
                        val inset      = stroke / 2f
                        val arcSize    = Size(size.width - stroke, size.height - stroke)
                        val startAngle = -90f
                        val sweepFull  = 360f

                        // Track
                        drawArc(
                            color      = trackColor,
                            startAngle = startAngle,
                            sweepAngle = sweepFull,
                            useCenter  = false,
                            topLeft    = Offset(inset, inset),
                            size       = arcSize,
                            style      = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        // Progress
                        drawArc(
                            color      = if (isAtRisk) errorColor else primaryColor,
                            startAngle = startAngle,
                            sweepAngle = sweepFull * animatedPercent,
                            useCenter  = false,
                            topLeft    = Offset(inset, inset),
                            size       = arcSize,
                            style      = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = "${percent.toInt()}%",
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color      = if (isAtRisk) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text  = "overall",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Breakdown row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatPill(label = "Present",   value = present,   color = Color(0xFF4CAF50))
                StatPill(label = "Absent",    value = absent,    color = MaterialTheme.colorScheme.error)
                StatPill(label = "Cancelled", value = cancelled, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // At-risk warning
            if (isAtRisk) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onErrorContainer,
                            modifier           = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Below ${target}% target",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = "$value",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = color,
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Today's schedule card ─────────────────────────────────────────────────────

@Composable
private fun TodayScheduleCard(sessions: List<TodaySession>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Today,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Today's Classes",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(12.dp))

            sessions.forEach { session ->
                val sessionColor = runCatching {
                    Color(android.graphics.Color.parseColor(session.colorHex))
                }.getOrDefault(MaterialTheme.colorScheme.primary)

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
                            .background(sessionColor)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        session.subjectName,
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${session.startTime} – ${session.endTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Quick stats row ───────────────────────────────────────────────────────────

@Composable
private fun QuickStatsRow(pendingTasks: Int, streak: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier              = Modifier.fillMaxWidth(),
    ) {
        QuickStatCard(
            icon    = Icons.Outlined.Assignment,
            label   = "Pending Tasks",
            value   = "$pendingTasks",
            color   = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
        QuickStatCard(
            icon    = Icons.Outlined.Whatshot,
            label   = "Day Streak",
            value   = "$streak",
            color   = Color(0xFFFFA726),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickStatCard(
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    label:    String,
    value:    String,
    color:    Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.12f)),
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    value,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Subject attendance card ───────────────────────────────────────────────────

@Composable
private fun SubjectAttendanceCard(
    summary: SubjectSummary,
    target:  Int,
    onClick: () -> Unit,
) {
    val subjectColor = runCatching {
        Color(android.graphics.Color.parseColor(summary.subject.colorHex))
    }.getOrDefault(MaterialTheme.colorScheme.primary)

    val animatedPercent by animateFloatAsState(
        targetValue   = summary.percentage / 100f,
        animationSpec = tween(600, easing = EaseOutCubic),
        label         = "subject_bar_${summary.subject.id}",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape    = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Top row: color chip + name + percentage
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(subjectColor.copy(alpha = 0.15f)),
                ) {
                    Text(
                        text       = summary.subject.shortName,
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = subjectColor,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        summary.subject.name,
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${summary.present}P  •  ${summary.absent}A",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    "${summary.percentage.toInt()}%",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = when {
                        summary.isAtRisk -> MaterialTheme.colorScheme.error
                        else             -> subjectColor
                    },
                )

                Icon(
                    Icons.Default.ChevronRight, null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedPercent)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (summary.isAtRisk) MaterialTheme.colorScheme.error
                            else subjectColor
                        )
                )
            }

            // Skip / recover hint
            Spacer(Modifier.height(8.dp))
            if (summary.isAtRisk && summary.mustAttend > 0) {
                Text(
                    "Attend ${summary.mustAttend} more to reach ${target}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (summary.canSkip > 0) {
                Text(
                    "Safe to skip ${summary.canSkip} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Lec / Lab split for BOTH-type subjects
            if (summary.lecStats != null && summary.labStats != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SplitChip(
                        label      = "Lec",
                        percentage = summary.lecStats.percentage,
                        isAtRisk   = summary.lecStats.isAtRisk,
                        color      = subjectColor,
                        modifier   = Modifier.weight(1f),
                    )
                    SplitChip(
                        label      = "Lab",
                        percentage = summary.labStats.percentage,
                        isAtRisk   = summary.labStats.isAtRisk,
                        color      = subjectColor,
                        modifier   = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitChip(
    label:      String,
    percentage: Float,
    isAtRisk:   Boolean,
    color:      Color,
    modifier:   Modifier = Modifier,
) {
    val chipColor = if (isAtRisk) MaterialTheme.colorScheme.errorContainer
    else color.copy(alpha = 0.1f)
    val textColor = if (isAtRisk) MaterialTheme.colorScheme.onErrorContainer
    else color
    Surface(shape = RoundedCornerShape(6.dp), color = chipColor, modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
            modifier              = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = textColor)
            Text(
                "${percentage.toInt()}%",
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = textColor,
            )
        }
    }
}

// ── Empty subjects hint ───────────────────────────────────────────────────────

@Composable
private fun EmptySubjectsHint(onManageSubjects: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "No subjects yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onManageSubjects) {
                Text("Add Subjects")
            }
        }
    }
}