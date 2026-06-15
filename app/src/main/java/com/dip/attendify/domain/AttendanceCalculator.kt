package com.dip.attendify.domain

import com.dip.attendify.data.db.dao.SubjectStatRaw
import com.dip.attendify.data.entity.AttendanceEntity
import com.dip.attendify.data.entity.AttendanceStatus
import com.dip.attendify.data.entity.SemesterEntity
import com.dip.attendify.data.entity.SubjectType
import kotlin.math.ceil
import kotlin.math.max

object AttendanceCalculator {

    // Split counts for BOTH-type subjects
    data class SplitStats(
        val present:    Int,
        val absent:     Int,
        val percentage: Float,
        val isAtRisk:   Boolean,
        val canSkip:    Int,
        val mustAttend: Int,
    )

    data class SubjectStats(
        val subjectId:   Int,
        val present:     Int,
        val absent:      Int,
        val cancelled:   Int,
        val percentage:  Float,
        val isAtRisk:    Boolean,
        val canSkip:     Int,
        val mustAttend:  Int,
        // Only populated for SubjectType.BOTH subjects
        val lecStats:    SplitStats? = null,
        val labStats:    SplitStats? = null,
    )

    data class OverallStats(
        val totalPresent:   Int,
        val totalAbsent:    Int,
        val totalCancelled: Int,
        val overallPercent: Float,
        val subjectStats:   List<SubjectStats>,
    )

    fun compute(
        rawStats: List<SubjectStatRaw>,
        semester: SemesterEntity,
    ): OverallStats {
        val target  = semester.targetAttendancePercent.toFloat()
        val warning = target - semester.warningBufferPercent

        val subjectStats = rawStats.map { raw ->
            val total  = raw.present + raw.absent
            val pct    = if (total == 0) 100f else raw.present.toFloat() / total * 100f
            val atRisk = pct < warning

            val canSkip = max(0, (raw.present * 100f / target - total).toInt())

            val mustAttend = if (pct >= target) 0 else {
                val numerator   = target * total - 100f * raw.present
                val denominator = 100f - target
                if (denominator <= 0f) Int.MAX_VALUE
                else ceil(numerator / denominator).toInt()
            }

            SubjectStats(
                subjectId  = raw.subjectId,
                present    = raw.present,
                absent     = raw.absent,
                cancelled  = raw.cancelled,
                percentage = pct,
                isAtRisk   = atRisk,
                canSkip    = canSkip,
                mustAttend = mustAttend,
            )
        }

        val totalPresent   = rawStats.sumOf { it.present }
        val totalAbsent    = rawStats.sumOf { it.absent }
        val totalCancelled = rawStats.sumOf { it.cancelled }
        val totalClasses   = totalPresent + totalAbsent
        val overallPct     = if (totalClasses == 0) 100f
        else totalPresent.toFloat() / totalClasses * 100f

        return OverallStats(
            totalPresent   = totalPresent,
            totalAbsent    = totalAbsent,
            totalCancelled = totalCancelled,
            overallPercent = overallPct,
            subjectStats   = subjectStats,
        )
    }

    /**
     * Compute split lec/lab stats for a BOTH-type subject.
     * Determines lec vs lab by slot span: startSlotId == endSlotId → lecture, else lab.
     */
    fun computeSplit(
        records:       List<AttendanceEntity>,
        subjectType:   SubjectType,
        targetPercent: Int,
        warningBuffer: Int,
    ): Pair<SplitStats?, SplitStats?> {
        if (subjectType != SubjectType.BOTH) return Pair(null, null)

        val lecRecords = records.filter { it.startSlotId == it.endSlotId &&
                it.status != AttendanceStatus.CANCELLED }
        val labRecords = records.filter { it.startSlotId != it.endSlotId &&
                it.status != AttendanceStatus.CANCELLED }

        return Pair(
            buildSplit(lecRecords, targetPercent, warningBuffer),
            buildSplit(labRecords, targetPercent, warningBuffer),
        )
    }

    private fun buildSplit(
        records:       List<AttendanceEntity>,
        targetPercent: Int,
        warningBuffer: Int,
    ): SplitStats {
        val present = records.count { it.status == AttendanceStatus.PRESENT }
        val absent  = records.count { it.status == AttendanceStatus.ABSENT }
        val total   = present + absent
        val pct     = if (total == 0) 0f else present.toFloat() / total * 100f
        val target  = targetPercent.toFloat()
        val warning = target - warningBuffer
        val canSkip = max(0, (present * 100f / target - total).toInt())
        val mustAttend = if (pct >= target || total == 0) 0 else {
            val num = target * total - 100f * present
            val den = 100f - target
            if (den <= 0f) Int.MAX_VALUE else ceil(num / den).toInt()
        }
        return SplitStats(
            present    = present,
            absent     = absent,
            percentage = pct,
            isAtRisk   = total > 0 && pct < warning,
            canSkip    = canSkip,
            mustAttend = mustAttend,
        )
    }

    fun safeToSkip(present: Int, absent: Int, targetPercent: Int): Int =
        max(0, (present * 100f / targetPercent - (present + absent)).toInt())
}