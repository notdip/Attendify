package com.dip.attendify.ui.summary

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterSummaryScreen(
    onBack: () -> Unit,
    vm: SemesterSummaryViewModel = hiltViewModel(),
) {
    val state   by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text("Semester Summary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, state.shareText)
                            putExtra(Intent.EXTRA_SUBJECT, "Attendify — ${state.semesterName} Summary")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share summary"))
                    }) {
                        Icon(Icons.Default.Share, "Share")
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

            // ── Header ─────────────────────────────────────────────────────
            item {
                Column {
                    Text(
                        state.semesterName,
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        state.semesterDates,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Overall attendance ring ────────────────────────────────────
            item {
                OverallCard(state = state)
            }

            // ── Streak card ────────────────────────────────────────────────
            item {
                StreakCard(
                    current = state.currentStreak,
                    longest = state.longestStreak,
                )
            }

            // ── Tasks card ─────────────────────────────────────────────────
            item {
                TasksSummaryCard(
                    done    = state.totalTasksDone,
                    pending = state.totalTasksPending,
                )
            }

            // ── Subject breakdown ──────────────────────────────────────────
            item {
                Text(
                    "Subject Breakdown",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            items(state.subjectRows, key = { it.subjectName }) { row ->
                SubjectSummaryCard(row = row)
            }
        }
    }
}

// ── Overall card ──────────────────────────────────────────────────────────────

@Composable
private fun OverallCard(state: SemesterSummaryState) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor   = MaterialTheme.colorScheme.surfaceVariant
    val errorColor   = MaterialTheme.colorScheme.error
    val isAtRisk     = state.overallPercent < 75f

    val animatedPct by animateFloatAsState(
        targetValue   = state.overallPercent / 100f,
        animationSpec = tween(900, easing = EaseOutCubic),
        label         = "summary_ring",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(20.dp),
        ) {
            // Ring
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(110.dp)
                    .drawBehind {
                        val stroke  = 12.dp.toPx()
                        val inset   = stroke / 2f
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(trackColor, -90f, 360f, false,
                            Offset(inset, inset), arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(
                            if (isAtRisk) errorColor else primaryColor,
                            -90f, 360f * animatedPct, false,
                            Offset(inset, inset), arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round))
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${state.overallPercent.toInt()}%",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = if (isAtRisk) errorColor
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text("overall",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.width(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatRow(
                    label = "Present",
                    value = "${state.totalPresent}",
                    color = Color(0xFF4CAF50),
                )
                StatRow(
                    label = "Absent",
                    value = "${state.totalAbsent}",
                    color = MaterialTheme.colorScheme.error,
                )
                StatRow(
                    label = "Cancelled",
                    value = "${state.totalCancelled}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(70.dp))
        Text(value,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold)
    }
}

// ── Streak card ───────────────────────────────────────────────────────────────

@Composable
private fun StreakCard(current: Int, longest: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StreakStat(
                icon  = Icons.Default.Whatshot,
                color = Color(0xFFFFA726),
                value = "$current",
                label = "Current streak",
            )
            VerticalDivider(modifier = Modifier.height(60.dp))
            StreakStat(
                icon  = Icons.Outlined.EmojiEvents,
                color = MaterialTheme.colorScheme.primary,
                value = "$longest",
                label = "Best streak",
            )
        }
    }
}

@Composable
private fun StreakStat(
    icon:  androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    value: String,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Text(value,
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Tasks summary card ────────────────────────────────────────────────────────

@Composable
private fun TasksSummaryCard(done: Int, pending: Int) {
    val total     = done + pending
    val doneFrac  = if (total == 0) 0f else done.toFloat() / total

    val animatedFrac by animateFloatAsState(
        targetValue   = doneFrac,
        animationSpec = tween(700, easing = EaseOutCubic),
        label         = "tasks_bar",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Assignment, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tasks",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "$done / $total completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFrac)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF4CAF50))
                )
            }

            if (pending > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "$pending task${if (pending > 1) "s" else ""} still pending",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Subject summary card ──────────────────────────────────────────────────────

@Composable
private fun SubjectSummaryCard(row: SubjectSummaryRow) {
    val subjectColor = runCatching {
        Color(android.graphics.Color.parseColor(row.colorHex))
    }.getOrDefault(MaterialTheme.colorScheme.primary)

    val animatedPct by animateFloatAsState(
        targetValue   = row.percentage / 100f,
        animationSpec = tween(600, easing = EaseOutCubic),
        label         = "summary_bar_${row.subjectName}",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(subjectColor.copy(alpha = 0.15f)),
                ) {
                    Text(row.shortName,
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = subjectColor)
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(row.subjectName,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis)
                    Text(
                        "${row.present}P  •  ${row.absent}A  •  " +
                                "${row.tasksDone} tasks done",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    "${row.percentage.toInt()}%",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = subjectColor,
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedPct)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(subjectColor)
                )
            }
        }
    }
}