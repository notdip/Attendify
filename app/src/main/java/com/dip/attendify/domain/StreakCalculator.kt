package com.dip.attendify.domain

import com.dip.attendify.data.entity.AttendanceEntity
import com.dip.attendify.data.entity.AttendanceStatus
import java.time.LocalDate

object StreakCalculator {

    data class StreakResult(
        val currentStreak:  Int,
        val longestStreak:  Int,
        val lastActiveDate: LocalDate?,
    )

    /**
     * A streak day = any day with at least one PRESENT record.
     * Cancelled-only days are ignored — don't count, don't break.
     * Any day with at least one ABSENT and zero PRESENT breaks the streak.
     * Weekends are skipped — Fri→Mon counts as consecutive.
     */
    fun compute(records: List<AttendanceEntity>): StreakResult {
        if (records.isEmpty()) return StreakResult(0, 0, null)

        val activeDays = records
            .groupBy { it.date }
            .mapValues { (_, recs) ->
                val nonCancelled = recs.filter { it.status != AttendanceStatus.CANCELLED }
                when {
                    nonCancelled.isEmpty() -> DayStatus.CANCELLED
                    nonCancelled.any { it.status == AttendanceStatus.PRESENT } -> DayStatus.PRESENT
                    else -> DayStatus.ABSENT
                }
            }
            .filterValues { it != DayStatus.CANCELLED }
            .entries
            .sortedBy { it.key }

        if (activeDays.isEmpty()) return StreakResult(0, 0, null)

        var longestStreak = 0
        var currentRun    = 0
        var prevEpochDay  = -2L

        activeDays.forEach { (epochDay, status) ->
            val date     = LocalDate.ofEpochDay(epochDay)
            val prevDate = if (prevEpochDay >= 0) LocalDate.ofEpochDay(prevEpochDay) else null
            val isConsecutive = prevDate != null && isNextSchoolDay(prevDate, date)

            if (status == DayStatus.PRESENT) {
                currentRun = if (isConsecutive) currentRun + 1 else 1
                if (currentRun > longestStreak) longestStreak = currentRun
            } else {
                currentRun = 0
            }
            prevEpochDay = epochDay
        }

        val lastDay   = activeDays.last()
        val lastDate  = LocalDate.ofEpochDay(lastDay.key)
        val today     = LocalDate.now()
        val streakLive = lastDay.value == DayStatus.PRESENT && !isStreakExpired(lastDate, today)

        return StreakResult(
            currentStreak  = if (streakLive) currentRun else 0,
            longestStreak  = longestStreak,
            lastActiveDate = lastDate,
        )
    }

    private fun isNextSchoolDay(prev: LocalDate, next: LocalDate): Boolean {
        var cursor = prev.plusDays(1)
        repeat(4) {
            if (cursor.dayOfWeek.value <= 5) return cursor == next
            cursor = cursor.plusDays(1)
        }
        return false
    }

    private fun isStreakExpired(lastDate: LocalDate, today: LocalDate): Boolean {
        if (lastDate >= today) return false
        var cursor = lastDate.plusDays(1)
        var schoolDaysPassed = 0
        while (!cursor.isAfter(today)) {
            if (cursor.dayOfWeek.value <= 5) schoolDaysPassed++
            if (schoolDaysPassed > 1) return true
            cursor = cursor.plusDays(1)
        }
        return false
    }

    private enum class DayStatus { PRESENT, ABSENT, CANCELLED }
}