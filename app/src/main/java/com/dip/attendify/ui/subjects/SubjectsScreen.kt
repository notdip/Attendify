package com.dip.attendify.ui.subjects

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dip.attendify.data.entity.SubjectEntity
import com.dip.attendify.data.entity.SubjectType
import com.dip.attendify.data.repository.SubjectRepository
import com.dip.attendify.ui.theme.subjectColorPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class SubjectFormState(
    val name:      String      = "",
    val shortName: String      = "",
    val colorHex:  String      = subjectColorPalette.first(),
    val type:      SubjectType = SubjectType.LECTURE,
) {
    val canSave: Boolean get() = name.isNotBlank() && shortName.isNotBlank()
}

data class SubjectsScreenState(
    val subjects:  List<SubjectEntity> = emptyList(),
    val editingId: Int?                = null,
    val showForm:  Boolean             = false,
    val form:      SubjectFormState    = SubjectFormState(),
)

@HiltViewModel
class SubjectsViewModel @Inject constructor(
    private val repo: SubjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SubjectsScreenState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeAll().collect { subjects ->
                _state.update { it.copy(subjects = subjects) }
            }
        }
    }

    fun openAddForm() {
        _state.update {
            it.copy(
                showForm  = true,
                editingId = null,
                form      = SubjectFormState(
                    colorHex = subjectColorPalette[it.subjects.size % subjectColorPalette.size]
                )
            )
        }
    }

    fun openEditForm(subject: SubjectEntity) {
        _state.update {
            it.copy(
                showForm  = true,
                editingId = subject.id,
                form      = SubjectFormState(
                    name      = subject.name,
                    shortName = subject.shortName,
                    colorHex  = subject.colorHex,
                    type      = subject.type,
                )
            )
        }
    }

    fun dismissForm()                    = _state.update { it.copy(showForm = false) }
    fun onNameChange(v: String)          = _state.update { it.copy(form = it.form.copy(name = v)) }
    fun onShortNameChange(v: String)     = _state.update { it.copy(form = it.form.copy(shortName = v.take(5))) }
    fun onColorChange(v: String)         = _state.update { it.copy(form = it.form.copy(colorHex = v)) }
    fun onTypeChange(v: SubjectType)     = _state.update { it.copy(form = it.form.copy(type = v)) }

    fun save() {
        val s = _state.value
        if (!s.form.canSave) return
        viewModelScope.launch {
            val entity = SubjectEntity(
                id        = s.editingId ?: 0,
                name      = s.form.name.trim(),
                shortName = s.form.shortName.trim().uppercase(),
                colorHex  = s.form.colorHex,
                type      = s.form.type,
            )
            if (s.editingId == null) repo.insert(entity) else repo.update(entity)
            _state.update { it.copy(showForm = false) }
        }
    }

    fun delete(subject: SubjectEntity) {
        viewModelScope.launch { repo.deleteById(subject.id) }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsScreen(
    onBack: () -> Unit,
    vm: SubjectsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text("Manage Subjects") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::openAddForm) {
                Icon(Icons.Default.Add, "Add Subject")
            }
        }
    ) { padding ->
        if (state.subjects.isEmpty()) {
            EmptySubjectsState(
                modifier = Modifier.padding(padding).fillMaxSize(),
                onAdd    = vm::openAddForm,
            )
        } else {
            LazyColumn(
                contentPadding      = PaddingValues(
                    start  = 16.dp, end = 16.dp,
                    top    = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.subjects, key = { it.id }) { subject ->
                    SubjectCard(
                        subject  = subject,
                        onEdit   = { vm.openEditForm(subject) },
                        onDelete = { vm.delete(subject) },
                    )
                }
            }
        }
    }

    if (state.showForm) {
        SubjectFormSheet(
            state     = state.form,
            isEditing = state.editingId != null,
            vm        = vm,
        )
    }
}

// ── Subject Card ──────────────────────────────────────────────────────────────

@Composable
private fun SubjectCard(
    subject:  SubjectEntity,
    onEdit:   () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val subjectColor = runCatching {
        Color(android.graphics.Color.parseColor(subject.colorHex))
    }.getOrDefault(Color.Gray)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(subjectColor.copy(alpha = 0.2f))
                    .border(1.5.dp, subjectColor.copy(alpha = 0.5f), CircleShape),
            ) {
                Text(
                    text       = subject.shortName,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = subjectColor,
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(subject.name,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium)
                Text(
                    subject.type.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.DeleteOutline, "Delete",
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text("Delete ${subject.name}?") },
            text    = { Text("This removes the subject and all its attendance records and tasks. Cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Subject Form Sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectFormSheet(
    state:     SubjectFormState,
    isEditing: Boolean,
    vm:        SubjectsViewModel,
) {
    ModalBottomSheet(onDismissRequest = vm::dismissForm) {
        Column(
            modifier            = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (isEditing) "Edit Subject" else "Add Subject",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value         = state.name,
                onValueChange = vm::onNameChange,
                label         = { Text("Subject name") },
                placeholder   = { Text("e.g. Object Oriented Programming") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value         = state.shortName,
                onValueChange = vm::onShortNameChange,
                label         = { Text("Short name (max 5 chars)") },
                placeholder   = { Text("e.g. OOP") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            Text("Type", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubjectType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick  = { vm.onTypeChange(type) },
                        label    = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            Text("Color", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(subjectColorPalette) { hex ->
                    val color = runCatching {
                        Color(android.graphics.Color.parseColor(hex))
                    }.getOrDefault(Color.Gray)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (hex == state.colorHex) 2.5.dp else 0.dp,
                                color = if (hex == state.colorHex)
                                    MaterialTheme.colorScheme.onSurface
                                else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable { vm.onColorChange(hex) },
                    )
                }
            }

            Button(
                onClick  = vm::save,
                enabled  = state.canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (isEditing) "Save Changes" else "Add Subject")
            }
        }
    }
}

// ── Empty State ───────────────────────────────────────────────────────────────

@Composable
private fun EmptySubjectsState(modifier: Modifier, onAdd: () -> Unit) {
    Column(
        modifier             = modifier,
        verticalArrangement  = Arrangement.Center,
        horizontalAlignment  = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.MenuBook, null,
            modifier = Modifier.size(56.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(16.dp))
        Text("No subjects yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add your subjects before filling the timetable",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAdd) { Text("Add Subject") }
    }
}