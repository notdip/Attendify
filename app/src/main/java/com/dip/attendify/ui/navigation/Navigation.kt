package com.dip.attendify.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.dip.attendify.ui.home.HomeScreen
import com.dip.attendify.ui.subject.SubjectDetailScreen
import com.dip.attendify.ui.tasks.TasksScreen
import com.dip.attendify.ui.schedule.ScheduleScreen
import com.dip.attendify.ui.summary.SemesterSummaryScreen
import com.dip.attendify.ui.mark.MarkScreen
import com.dip.attendify.ui.setup.SemesterSetupScreen
import com.dip.attendify.ui.setup.TimetableSetupScreen
import com.dip.attendify.ui.subjects.SubjectsScreen

// ─────────────────────────────────────────────
// Routes
// ─────────────────────────────────────────────

sealed class Route(val path: String) {
    data object Home     : Route("home")
    data object Mark     : Route("mark")
    data object Schedule : Route("schedule")
    data object Tasks    : Route("tasks")
    data object Subjects : Route("subjects")

    object SubjectDetail {
        const val PATTERN = "subject/{subjectId}"
        fun path(id: Int) = "subject/$id"
    }

    data object SemesterSetup  : Route("semester_setup")
    data object EditSemester   : Route("edit_semester")
    data object Summary        : Route("summary")
    data object TimetableSetup : Route("timetable_setup")
}

// ─────────────────────────────────────────────
// Bottom nav
// ─────────────────────────────────────────────

data class BottomNavItem(
    val route:        String,
    val label:        String,
    val icon:         ImageVector,
    val selectedIcon: ImageVector = icon,
)

val bottomNavItems = listOf(
    BottomNavItem(Route.Home.path,     "Home",     Icons.Outlined.Home,          Icons.Filled.Home),
    BottomNavItem(Route.Mark.path,     "Mark",     Icons.Outlined.EditNote,      Icons.Filled.EditNote),
    BottomNavItem(Route.Schedule.path, "Schedule", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    BottomNavItem(Route.Tasks.path,    "Tasks",    Icons.Outlined.Checklist,     Icons.Filled.Checklist),
)

private val bottomNavRoutes = bottomNavItems.map { it.route }.toSet()

// ─────────────────────────────────────────────
// Nav host
// ─────────────────────────────────────────────

@Composable
fun AttendifyNavHost() {

    val navController    = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute     = currentBackStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick  = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon = {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier         = Modifier
                                        .size(width = 56.dp, height = 32.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (selected)
                                                MaterialTheme.colorScheme.secondaryContainer
                                            else Color.Transparent
                                        ),
                                ) {
                                    Icon(
                                        if (selected) item.selectedIcon else item.icon,
                                        contentDescription = item.label,
                                        tint = if (selected)
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            },
                            label = {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        }
    ) { innerPadding ->

        NavHost(
            navController    = navController,
            startDestination = Route.Home.path,
            modifier         = Modifier.padding(innerPadding),
        ) {

            // ── Bottom nav ─────────────────────────────────────────────────

            composable(Route.Home.path) {
                HomeScreen(
                    onSubjectClick   = { id -> navController.navigate(Route.SubjectDetail.path(id)) },
                    onManageSubjects = { navController.navigate(Route.Subjects.path) },
                    onNewSemester    = { navController.navigate(Route.SemesterSetup.path) },
                    onEditSemester   = { navController.navigate(Route.EditSemester.path) },
                    onSummary        = { navController.navigate(Route.Summary.path) },
                )
            }

            composable(Route.Mark.path) {
                MarkScreen()
            }

            composable(Route.Schedule.path) {
                ScheduleScreen(
                    onEditTimetable = { navController.navigate(Route.TimetableSetup.path) }
                )
            }

            composable(Route.Tasks.path) {
                TasksScreen(
                    onSubjectClick = { id -> navController.navigate(Route.SubjectDetail.path(id)) },
                )
            }

            // ── Drill-downs ────────────────────────────────────────────────

            composable(Route.Subjects.path) {
                SubjectsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route     = Route.SubjectDetail.PATTERN,
                arguments = listOf(navArgument("subjectId") { type = NavType.IntType })
            ) { back ->
                val subjectId = back.arguments?.getInt("subjectId") ?: return@composable
                SubjectDetailScreen(
                    subjectId = subjectId,
                    onBack    = { navController.popBackStack() },
                )
            }

            // ── Setup flows ────────────────────────────────────────────────

            composable(Route.SemesterSetup.path) {
                SemesterSetupScreen(
                    onComplete = { navController.navigate(Route.TimetableSetup.path) }
                )
            }

            composable(Route.EditSemester.path) {
                SemesterSetupScreen(
                    isEditMode = true,
                    onComplete = { navController.popBackStack() },
                )
            }

            composable(Route.TimetableSetup.path) {
                TimetableSetupScreen(
                    onComplete = {
                        navController.navigate(Route.Home.path) {
                            popUpTo(Route.SemesterSetup.path) { inclusive = true }
                        }
                    }
                )
            }

            composable(Route.Summary.path) {
                SemesterSummaryScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}