package dev.pschmitt.netboxandchill.ui.generic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.repository.FileDownloadRepository
import dev.pschmitt.netboxandchill.data.repository.GenericObjectRepository
import dev.pschmitt.netboxandchill.data.repository.JournalEntryRepository
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import dev.pschmitt.netboxandchill.sync.SyncScheduler
import dev.pschmitt.netboxandchill.ui.navigation.Route
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

// Mirrors NetBoxNavHost's/GlobalSearchRepository's DEVICES_ENDPOINT_PATH constant - kept local
// rather than shared to avoid a broader refactor while other agents are touching those files.
private const val DEVICES_ENDPOINT_PATH = "api/dcim/devices/"

@HiltViewModel
class GenericDetailViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GenericObjectRepository,
    private val settingsRepository: SettingsRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    private val journalEntryRepository: JournalEntryRepository,
    private val syncScheduler: SyncScheduler,
    private val json: Json,
) : ViewModel() {

    val route: Route.Generic = savedStateHandle.toRoute()

    // NBC-10: "Print label" only makes sense for devices (printlabel's --netbox mode prints a
    // device's QR/asset-tag sticker) - other object types don't have a label to (re)print.
    val isPrintableDevice: Boolean = route.endpointPath == DEVICES_ENDPOINT_PATH

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Positive-confirmation Snackbar text - shared by a manual refresh ("Refreshed") and a
    // successful save ("<item> updated!"), both simple "your action worked" acknowledgements.
    private val _refreshedMessage = MutableStateFlow<String?>(null)
    val refreshedMessage: StateFlow<String?> = _refreshedMessage.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _fileToOpen = MutableStateFlow<File?>(null)
    val fileToOpen: StateFlow<File?> = _fileToOpen.asStateFlow()

    private val _journalEntries = MutableStateFlow<List<JournalEntryUi>>(emptyList())
    val journalEntries: StateFlow<List<JournalEntryUi>> = _journalEntries.asStateFlow()

    private val objectFlow = repository.observeObject(route.endpointPath, route.id)

    private val decodedObject: StateFlow<JsonObject?> =
        objectFlow
            .map { entity -> entity?.let { decode(it.json) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val title: StateFlow<String?> =
        objectFlow
            .map { it?.display }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val fields: StateFlow<List<FieldRow>> =
        decodedObject
            .map { it?.let(::buildFieldRows) ?: emptyList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val editableFields: StateFlow<List<EditableField>> =
        decodedObject
            .map { it?.let(::buildEditableFields) ?: emptyList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Web URL mirrors the API path structure with "api/" dropped, e.g. api/dcim/racks/5/ ->
    // <base>/dcim/racks/5/.
    val webUrl: StateFlow<String?> =
        objectFlow
            .combine(settingsRepository.credentials) { entity, credentials ->
                entity to credentials
            }
            .map { (entity, credentials) ->
                if (entity == null || credentials.baseUrl.isBlank()) null
                else
                    "${credentials.baseUrl}/${route.endpointPath.removePrefix("api/")}${entity.id}/"
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Fed into the `printlabel --netbox-url` flag for the "Print label" share action - see
    // PrintLabelIntent.kt.
    val netboxBaseUrl: StateFlow<String?> =
        settingsRepository.credentials
            .map { it.baseUrl.ifBlank { null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        refresh()
        loadJournalEntries()
    }

    fun refresh(showConfirmation: Boolean = false) {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository
                .refreshObject(route.endpointPath, route.id)
                .onSuccess { if (showConfirmation) _refreshedMessage.value = "Refreshed" }
                .onFailure { _errorMessage.value = it.message ?: "Couldn't refresh - showing cached data" }
            _isRefreshing.value = false
        }
    }

    private fun loadJournalEntries() {
        viewModelScope.launch {
            journalEntryRepository.fetchJournalEntries(route.endpointPath, route.id).onSuccess {
                entries ->
                _journalEntries.value = entries.mapNotNull { it.toJournalEntryUi() }
            }
            // Silently no-op on failure - the journal is a secondary panel, not core object data,
            // and content-type resolution can legitimately come up empty for unusual object types.
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    fun refreshedMessageShown() {
        _refreshedMessage.value = null
    }

    fun startEditing() {
        _errorMessage.value = null
        _isEditing.value = true
    }

    fun cancelEditing() {
        _errorMessage.value = null
        _isEditing.value = false
    }

    /** [edits] maps field key -> (kind, edited text), one entry per changed field. */
    fun save(edits: Map<String, Pair<EditFieldKind, String>>) {
        if (edits.isEmpty()) {
            _isEditing.value = false
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            repository
                .updateObject(route.endpointPath, route.id, buildPatchBody(edits))
                .onSuccess {
                    _isEditing.value = false
                    _refreshedMessage.value = "${title.value ?: "Item"} updated!"
                    // Refreshes the wider offline cache (and, if enabled, synced attachments) so
                    // an edit's side effects elsewhere in NetBox aren't only reflected here.
                    syncScheduler.syncNow()
                }
                .onFailure { _errorMessage.value = it.message ?: "Couldn't save changes" }
            _isSaving.value = false
        }
    }

    fun downloadAttachment(url: String, filename: String) {
        if (_isDownloading.value) return
        viewModelScope.launch {
            _isDownloading.value = true
            fileDownloadRepository
                .downloadToCache(url, filename)
                .onSuccess { _fileToOpen.value = it }
                .onFailure { _errorMessage.value = it.message ?: "Couldn't download $filename" }
            _isDownloading.value = false
        }
    }

    fun fileOpened() {
        _fileToOpen.value = null
    }

    private fun decode(rawJson: String): JsonObject? =
        runCatching { json.decodeFromString(JsonObject.serializer(), rawJson) }.getOrNull()
}
