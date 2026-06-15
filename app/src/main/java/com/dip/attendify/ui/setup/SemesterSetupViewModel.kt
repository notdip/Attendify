package com.dip.attendify.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dip.attendify.core.SemesterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SemesterSetupState(
    val name:          String     = "",
    val startDate:     LocalDate? = null,
    val endDate:       LocalDate? = null,
    val targetPercent: Int        = 75,
    val warningBuffer: Int        = 5,
    val isLoading:     Boolean    = false,
    val isDone:        Boolean    = false,
    val error:         String?    = null,
) {
    val canProceed: Boolean
        get() = name.isNotBlank()
                && startDate != null
                && endDate != null
                && endDate!!.isAfter(startDate)
}

@HiltViewModel
class SemesterSetupViewModel @Inject constructor(
    private val semesterManager: SemesterManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SemesterSetupState())
    val state = _state.asStateFlow()

    fun onNameChange(v: String)         = _state.update { it.copy(name = v, error = null) }
    fun onStartDateChange(d: LocalDate) = _state.update { it.copy(startDate = d, error = null) }
    fun onEndDateChange(d: LocalDate)   = _state.update { it.copy(endDate = d, error = null) }
    fun onTargetChange(v: Int)          = _state.update { it.copy(targetPercent = v.coerceIn(1, 100)) }
    fun onWarningBufferChange(v: Int)   = _state.update { it.copy(warningBuffer = v.coerceIn(1, 20)) }

    fun submit() {
        val s = _state.value
        if (!s.canProceed) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                semesterManager.createSemester(
                    name          = s.name.trim(),
                    startDate     = s.startDate!!.toEpochDay(),
                    endDate       = s.endDate!!.toEpochDay(),
                    targetPercent = s.targetPercent,
                    warningBuffer = s.warningBuffer,
                )
                _state.update { it.copy(isLoading = false, isDone = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to save semester") }
            }
        }
    }
}