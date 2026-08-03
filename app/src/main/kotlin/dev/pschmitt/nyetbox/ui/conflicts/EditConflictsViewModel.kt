package dev.pschmitt.nyetbox.ui.conflicts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.nyetbox.data.db.PendingEditEntity
import dev.pschmitt.nyetbox.data.repository.PendingEditRepository
import dev.pschmitt.nyetbox.ui.generic.ConflictChoice
import dev.pschmitt.nyetbox.ui.generic.ConflictField
import dev.pschmitt.nyetbox.ui.generic.buildConflictFields
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@HiltViewModel
class EditConflictsViewModel
@Inject
constructor(
    private val repository: PendingEditRepository,
    private val json: Json,
) : ViewModel() {
    val conflicts: StateFlow<List<PendingEditEntity>> =
        repository
            .observeConflicts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _resolvedMessage = MutableStateFlow<String?>(null)
    val resolvedMessage: StateFlow<String?> = _resolvedMessage.asStateFlow()

    fun fields(edit: PendingEditEntity): List<ConflictField> =
        buildConflictFields(
            json.decodeFromString(JsonObject.serializer(), edit.baseJson),
            json.decodeFromString(JsonObject.serializer(), edit.localJson),
            json.decodeFromString(JsonObject.serializer(), edit.serverJson ?: "{}"),
        )

    fun resolve(edit: PendingEditEntity, choices: Map<String, ConflictChoice>) {
        if (_isResolving.value) return
        viewModelScope.launch {
            _isResolving.value = true
            repository
                .resolveConflict(edit, choices.filterValues { it == ConflictChoice.LOCAL }.keys)
                .onSuccess { _resolvedMessage.value = "Conflict resolved" }
                .onFailure { _errorMessage.value = it.message ?: "Couldn't resolve conflict" }
            _isResolving.value = false
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    fun resolvedMessageShown() {
        _resolvedMessage.value = null
    }
}
