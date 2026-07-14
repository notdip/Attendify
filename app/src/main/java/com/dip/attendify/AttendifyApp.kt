package com.dip.attendify

import android.app.Application
import com.dip.attendify.data.repository.AttendanceRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AttendifyApp : Application() {

    @Inject lateinit var attendanceRepo: AttendanceRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val PREFS_NAME               = "attendify_migrations"
        private const val KEY_DEDUP_2_1_1_DONE     = "dedup_2_1_1_done"
    }

    override fun onCreate() {
        super.onCreate()
        runDedup2_1_1IfNeeded()
    }

    // One-time cleanup for duplicate attendance rows caused by the
    // pre-2.1.1 ad-hoc "Add Session" bug (see AttendanceRepository
    // .deduplicateRecords() for the full explanation). Guarded so it
    // only ever runs once per install, even across app restarts.
    private fun runDedup2_1_1IfNeeded() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DEDUP_2_1_1_DONE, false)) return

        appScope.launch {
            attendanceRepo.deduplicateRecords()
            prefs.edit().putBoolean(KEY_DEDUP_2_1_1_DONE, true).apply()
        }
    }
}