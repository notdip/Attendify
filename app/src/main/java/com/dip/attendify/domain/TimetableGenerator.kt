package com.dip.attendify.domain

import com.dip.attendify.data.entity.TimeSlotEntity
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object TimetableGenerator {

    private val fmt = DateTimeFormatter.ofPattern("hh:mm a")

    data class PeriodConfig(
        val firstPeriodStart:   LocalTime,
        val periodDurationMins: Int,
        val numberOfPeriods:    Int,
        val breakAfterPeriods:  List<Int> = emptyList(),
        val breakDurationMins:  Int = 30,
    )

    fun generateSlots(config: PeriodConfig): List<TimeSlotEntity> {
        val slots   = mutableListOf<TimeSlotEntity>()
        var current = config.firstPeriodStart
        var order   = 1

        for (i in 1..config.numberOfPeriods) {
            val end = current.plusMinutes(config.periodDurationMins.toLong())
            slots.add(
                TimeSlotEntity(
                    startTime = current.format(fmt),
                    endTime   = end.format(fmt),
                    slotOrder = order++,
                )
            )
            current = if (i in config.breakAfterPeriods)
                end.plusMinutes(config.breakDurationMins.toLong())
            else
                end
        }

        return slots
    }
}