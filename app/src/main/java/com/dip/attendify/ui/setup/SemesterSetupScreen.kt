package com.dip.attendify.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterSetupScreen(
    onComplete: () -> Unit,
    isEditMode: Boolean = false,
    vm: SemesterSetupViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(isEditMode) {
        if (isEditMode) vm.loadActiveForEdit()
    }

    LaunchedEffect(state.isDone) {
        if (state.isDone) onComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (state.isEditMode) "Edit Semester" else "New Semester") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            SectionLabel("Semester Details")

            OutlinedTextField(
                value           = state.name,
                onValueChange   = vm::onNameChange,
                label           = { Text("Semester name") },
                placeholder     = { Text("e.g. Semester 3") },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier        = Modifier.fillMaxWidth(),
            )

            SectionLabel("Date Range")

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                DatePickerField(
                    label    = "Start date",
                    date     = state.startDate,
                    onPicked = vm::onStartDateChange,
                    modifier = Modifier.weight(1f),
                )
                DatePickerField(
                    label    = "End date",
                    date     = state.endDate,
                    onPicked = vm::onEndDateChange,
                    modifier = Modifier.weight(1f),
                )
            }

            SectionLabel("Attendance Target")

            Text(
                text  = "Minimum required: ${state.targetPercent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value         = state.targetPercent.toFloat(),
                onValueChange = { vm.onTargetChange(it.toInt()) },
                valueRange    = 50f..100f,
                steps         = 49,
            )

            Text(
                text  = "Warn when within ${state.warningBuffer}% of minimum",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value         = state.warningBuffer.toFloat(),
                onValueChange = { vm.onWarningBufferChange(it.toInt()) },
                valueRange    = 1f..20f,
                steps         = 18,
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick  = vm::submit,
                enabled  = state.canProceed && !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (state.isEditMode) "Save Changes" else "Continue to Timetable Setup")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label:    String,
    date:     LocalDate?,
    onPicked: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value         = date?.format(dateFmt) ?: "",
        onValueChange = {},
        readOnly      = true,
        label         = { Text(label) },
        trailingIcon  = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.CalendarToday, "Pick date")
            }
        },
        modifier = modifier,
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.toEpochDay()?.times(86_400_000L)
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton    = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onPicked(LocalDate.ofEpochDay(millis / 86_400_000L))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}