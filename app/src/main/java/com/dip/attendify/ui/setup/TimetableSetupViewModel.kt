package com.dip.attendify.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dip.attendify.core.SemesterManager
import com.dip.attendify.data.entity.SubjectEntity
import com.dip.attendify.data.entity.TimeSlotEntity
import com.dip.attendify.data.entity.TimetableEntryEntity
import com.dip.attendify.data.repository.SubjectRepository
import com.dip.attendify.data.repository.TimetableRepository
import com.dip.attendify.domain.TimetableGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ── Step ──────────────────────────────────────

enum class TimetableSetupStep { PERIOD_CONFIG, GRID_FILL }

// ── Period config state ───────────────────────

data class PeriodConfigState(
    val firstPeriodStart:   LocalTime          = LocalTime.of(8, 0),
    val periodDurationMins: Int                = 60,
    val numberOfPeriods:    Int                = 8,
    val breakAfterPeriod:   Int?               = 4,
    val breakDurationMins:  Int                = 30,
    val preview:            List<TimeSlotEntity> = emptyList(),
)

// ── Grid cell model ───────────────────────────

data class TimetableCell(
    val day:                Int,
    val slotId:             Int,
    val subjectId:          Int?    = null,
    val subjectName:        String? = null,
    val colorHex:           String? = null,
    val spanSlots:          Int     = 1,
    val isSpanContinuation: Boolean = false,
)

// ── Grid fill state ───────────────────────────

data class GridFillState(
    val slots:        List<TimeSlotEntity>           = emptyList(),
    val subjects:     List<SubjectEntity>            = emptyList(),
    val cells:        Map<Pair<Int, Int>, TimetableCell> = emptyMap(),
    val activeDayTab: Int                            = 1,
)

// ── Combined screen state ─────────────────────

data class TimetableSetupState(
    val step:         TimetableSetupStep = TimetableSetupStep.PERIOD_CONFIG,
    val periodConfig: PeriodConfigState  = PeriodConfigState(),
    val gridFill:     GridFillState      = GridFillState(),
    val isSaving:     Boolean            = false,
    val isDone:       Boolean            = false,
    val error:        String?            = null,
)

@HiltViewModel
class TimetableSetupViewModel @Inject constructor(
    private val semesterManager: SemesterManager,
    private val timetableRepo:   TimetableRepository,
    private val subjectRepo:     SubjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TimetableSetupState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existingSlots = timetableRepo.getAllSlots()
            if (existingSlots.isNotEmpty()) {
                // Parse start time — handle both 12H and 24H stored formats
                val firstSlot = existingSlots.first()
                val startTime = runCatching {
                    java.time.LocalTime.parse(
                        firstSlot.startTime,
                        DateTimeFormatter.ofPattern("hh:mm a")
                    )
                }.getOrElse {
                    runCatching {
                        java.time.LocalTime.parse(
                            firstSlot.startTime,
                            DateTimeFormatter.ofPattern("HH:mm")
                        )
                    }.getOrDefault(java.time.LocalTime.of(8, 0))
                }

                val durationMins = runCatching {
                    val endTime = runCatching {
                        java.time.LocalTime.parse(
                            firstSlot.endTime,
                            DateTimeFormatter.ofPattern("hh:mm a")
                        )
                    }.getOrElse {
                        java.time.LocalTime.parse(
                            firstSlot.endTime,
                            DateTimeFormatter.ofPattern("HH:mm")
                        )
                    }
                    java.time.Duration.between(startTime, endTime).toMinutes().toInt()
                }.getOrDefault(60)

                _state.update { st ->
                    st.copy(
                        periodConfig = st.periodConfig.copy(
                            firstPeriodStart   = startTime,
                            periodDurationMins = durationMins,
                            numberOfPeriods    = existingSlots.size,
                            preview            = existingSlots,
                        )
                    )
                }

                // Load existing timetable entries into grid
                val semester = semesterManager.getActiveSemester()
                if (semester != null) {
                    val subjectMap = subjectRepo.getAll().associateBy { it.id }
                    val cells      = mutableMapOf<Pair<Int, Int>, TimetableCell>()
                    for (day in 1..6) {
                        timetableRepo.getEntriesForDay(semester.id, day).forEach { entry ->
                            val subject  = subjectMap[entry.subjectId] ?: return@forEach
                            val startIdx = existingSlots.indexOfFirst { it.id == entry.startSlotId }
                            val endIdx   = existingSlots.indexOfFirst { it.id == entry.endSlotId }
                            if (startIdx < 0) return@forEach
                            val span = (endIdx - startIdx + 1).coerceAtLeast(1)
                            for (i in 0 until span) {
                                val slotId = existingSlots[startIdx + i].id
                                cells[Pair(day, slotId)] = TimetableCell(
                                    day                = day,
                                    slotId             = slotId,
                                    subjectId          = entry.subjectId,
                                    subjectName        = subject.shortName,
                                    colorHex           = subject.colorHex,
                                    spanSlots          = if (i == 0) span else 1,
                                    isSpanContinuation = i > 0,
                                )
                            }
                        }
                    }
                    _state.update { st ->
                        st.copy(
                            gridFill = st.gridFill.copy(
                                slots = existingSlots,
                                cells = cells,
                            )
                        )
                    }
                }
            } else {
                refreshPreview()
            }
        }

        viewModelScope.launch {
            subjectRepo.observeAll().collect { subjects ->
                _state.update { it.copy(gridFill = it.gridFill.copy(subjects = subjects)) }
            }
        }
    }

    // ── Period config ─────────────────────────

    fun onFirstPeriodStartChange(t: LocalTime) {
        _state.update { it.copy(periodConfig = it.periodConfig.copy(firstPeriodStart = t)) }
        refreshPreview()
    }

    fun onDurationChange(mins: Int) {
        _state.update { it.copy(periodConfig = it.periodConfig.copy(periodDurationMins = mins.coerceIn(30, 180))) }
        refreshPreview()
    }

    fun onPeriodCountChange(n: Int) {
        _state.update { it.copy(periodConfig = it.periodConfig.copy(numberOfPeriods = n.coerceIn(1, 12))) }
        refreshPreview()
    }

    fun onBreakAfterPeriodChange(n: Int?) {
        _state.update { it.copy(periodConfig = it.periodConfig.copy(breakAfterPeriod = n)) }
        refreshPreview()
    }

    fun onBreakDurationChange(mins: Int) {
        _state.update { it.copy(periodConfig = it.periodConfig.copy(breakDurationMins = mins.coerceIn(5, 120))) }
        refreshPreview()
    }

    private fun refreshPreview() {
        val cfg = _state.value.periodConfig
        val preview = TimetableGenerator.generateSlots(
            TimetableGenerator.PeriodConfig(
                firstPeriodStart   = cfg.firstPeriodStart,
                periodDurationMins = cfg.periodDurationMins,
                numberOfPeriods    = cfg.numberOfPeriods,
                breakAfterPeriods  = listOfNotNull(cfg.breakAfterPeriod),
                breakDurationMins  = cfg.breakDurationMins,
            )
        )
        _state.update { it.copy(periodConfig = it.periodConfig.copy(preview = preview)) }
    }

    fun confirmPeriodConfig() {
        viewModelScope.launch {
            val previewSlots   = _state.value.periodConfig.preview
            val existingSlots  = timetableRepo.getAllSlots()
            val existingCells  = _state.value.gridFill.cells

            timetableRepo.clearAllSlots()
            timetableRepo.insertAllSlots(previewSlots)
            val savedSlots = timetableRepo.getAllSlots()

            // Remap existing cells to new slot IDs by matching slotOrder
            // This preserves subject assignments when period config is unchanged
            val oldOrderToNew = existingSlots
                .zip(savedSlots)
                .associate { (old, new) -> old.id to new.id }

            val remappedCells = existingCells
                .mapKeys { (key, _) ->
                    val newSlotId = oldOrderToNew[key.second] ?: key.second
                    Pair(key.first, newSlotId)
                }
                .mapValues { (newKey, cell) ->
                    val newSlotId = oldOrderToNew[cell.slotId] ?: cell.slotId
                    cell.copy(slotId = newSlotId)
                }

            _state.update { st ->
                st.copy(
                    step     = TimetableSetupStep.GRID_FILL,
                    gridFill = st.gridFill.copy(
                        slots = savedSlots,
                        cells = remappedCells,
                    ),
                )
            }
        }
    }

    // ── Grid fill ─────────────────────────────

    fun onDayTabChange(day: Int) =
        _state.update { it.copy(gridFill = it.gridFill.copy(activeDayTab = day)) }

    fun placeSubject(day: Int, startSlotId: Int, subjectId: Int, spanSlots: Int) {
        val subject = _state.value.gridFill.subjects.find { it.id == subjectId } ?: return
        val slots   = _state.value.gridFill.slots

        val startIndex = slots.indexOfFirst { it.id == startSlotId }
        if (startIndex < 0) return

        val coveredSlotIds = slots.drop(startIndex).take(spanSlots).map { it.id }

        _state.update { st ->
            val newCells = st.gridFill.cells.toMutableMap()
            coveredSlotIds.forEach { sid -> newCells.remove(Pair(day, sid)) }
            coveredSlotIds.forEachIndexed { idx, sid ->
                newCells[Pair(day, sid)] = TimetableCell(
                    day                = day,
                    slotId             = sid,
                    subjectId          = subjectId,
                    subjectName        = subject.shortName,
                    colorHex           = subject.colorHex,
                    spanSlots          = if (idx == 0) spanSlots else 1,
                    isSpanContinuation = idx > 0,
                )
            }
            st.copy(gridFill = st.gridFill.copy(cells = newCells))
        }
    }

    fun clearCell(day: Int, slotId: Int) {
        _state.update { st ->
            val newCells  = st.gridFill.cells.toMutableMap()
            val slots     = st.gridFill.slots
            val slotIndex = slots.indexOfFirst { it.id == slotId }
            if (slotIndex < 0) return@update st

            var rootIdx = slotIndex
            while (rootIdx > 0 &&
                newCells[Pair(day, slots[rootIdx].id)]?.isSpanContinuation == true) {
                rootIdx--
            }

            val span = newCells[Pair(day, slots[rootIdx].id)]?.spanSlots ?: 1
            for (i in 0 until span) {
                if (rootIdx + i < slots.size)
                    newCells.remove(Pair(day, slots[rootIdx + i].id))
            }

            st.copy(gridFill = st.gridFill.copy(cells = newCells))
        }
    }

    fun copyDayTo(sourceDay: Int, targetDays: Set<Int>) {
        _state.update { st ->
            val sourceCells = st.gridFill.cells.filterKeys { it.first == sourceDay }
            val newCells    = st.gridFill.cells.toMutableMap()
            targetDays.forEach { targetDay ->
                newCells.keys.filter { it.first == targetDay }.forEach { newCells.remove(it) }
                sourceCells.forEach { (key, cell) ->
                    newCells[Pair(targetDay, key.second)] = cell.copy(day = targetDay)
                }
            }
            st.copy(gridFill = st.gridFill.copy(cells = newCells))
        }
    }

    // ── Save ──────────────────────────────────

    fun saveTimetable() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val semester = semesterManager.getActiveSemester()
                    ?: throw IllegalStateException("No active semester")

                timetableRepo.clearSemester(semester.id)

                val slots   = _state.value.gridFill.slots
                val entries = _state.value.gridFill.cells.values
                    .filter { !it.isSpanContinuation && it.subjectId != null }
                    .map { cell ->
                        val startIdx  = slots.indexOfFirst { it.id == cell.slotId }
                        val endSlotId = if (cell.spanSlots <= 1) cell.slotId
                        else slots[startIdx + cell.spanSlots - 1].id
                        TimetableEntryEntity(
                            semesterId  = semester.id,
                            dayOfWeek   = cell.day,
                            subjectId   = cell.subjectId!!,
                            startSlotId = cell.slotId,
                            endSlotId   = endSlotId,
                        )
                    }

                timetableRepo.insertAll(entries)
                _state.update { it.copy(isSaving = false, isDone = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = "Failed to save: ${e.message}") }
            }
        }
    }
}