package dev.pschmitt.netboxandchill.ui.common

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.db.NetBoxModelEntity
import dev.pschmitt.netboxandchill.data.repository.DirectoryRepository
import dev.pschmitt.netboxandchill.data.repository.GenericObjectRepository
import dev.pschmitt.netboxandchill.data.repository.MediaUploadRepository
import dev.pschmitt.netboxandchill.data.repository.DeviceTypePhotoFace
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MediaDocumentTypeOption(val id: Int, val label: String)

enum class MediaUploadKind(val label: String) {
    ImageAttachment("Image attachment"),
    DeviceTypeFront("Device-type front photo"),
    DeviceTypeRear("Device-type rear photo"),
    Document("NetBox document"),
}

data class MediaUploadUiState(
    val isUploading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MediaUploadViewModel
@Inject
constructor(
    private val repository: MediaUploadRepository,
    directoryRepository: DirectoryRepository,
    genericObjectRepository: GenericObjectRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MediaUploadUiState())
    val state: StateFlow<MediaUploadUiState> = _state.asStateFlow()

    private val documentModels =
        directoryRepository
            .observeAll()
            .map { models -> models.filter(::isDocumentModel) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documentEndpointPath: StateFlow<String?> =
        documentModels
            .map { models -> models.firstOrNull { !it.modelKey.contains("type") }?.endpointPath }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val documentTypeOptions: StateFlow<List<MediaDocumentTypeOption>> =
        documentModels
            .flatMapLatest { models ->
                val typeModel = models.firstOrNull { it.modelKey.contains("type") }
                typeModel?.let { genericObjectRepository.observeObjects(it.endpointPath, "") }
                    ?: flowOf(emptyList())
            }
            .map { objects -> objects.map { MediaDocumentTypeOption(it.id, it.display) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun upload(
        kind: MediaUploadKind,
        endpointPath: String,
        objectId: Int,
        uri: Uri,
        filename: String,
        documentEndpointPath: String?,
        documentTypeId: Int?,
        onUploaded: () -> Unit,
    ) {
        if (_state.value.isUploading) return
        viewModelScope.launch {
            _state.value = MediaUploadUiState(isUploading = true)
            val result =
                when (kind) {
                    MediaUploadKind.ImageAttachment ->
                        repository.uploadImageAttachment(endpointPath, objectId, uri, filename)
                    MediaUploadKind.DeviceTypeFront,
                    MediaUploadKind.DeviceTypeRear -> {
                        if (endpointPath != "api/dcim/device-types/") {
                            Result.failure(IllegalArgumentException("Device-type photos require a device type"))
                        } else {
                            repository.uploadDeviceTypePhoto(
                                objectId,
                                if (kind == MediaUploadKind.DeviceTypeFront) DeviceTypePhotoFace.Front
                                else DeviceTypePhotoFace.Rear,
                                uri,
                                filename,
                            )
                        }
                    }
                    MediaUploadKind.Document -> {
                        when {
                            documentEndpointPath == null ->
                                Result.failure(IllegalStateException("NetBox Documents is not available in the cache"))
                            documentTypeId == null ->
                                Result.failure(IllegalArgumentException("Choose a document type"))
                            else ->
                                repository.uploadDocument(
                                    documentEndpointPath,
                                    endpointPath,
                                    objectId,
                                    uri,
                                    filename,
                                    documentTypeId,
                                )
                        }
                    }
                }
            result
                .onSuccess {
                    _state.value = MediaUploadUiState(message = "Uploaded")
                    onUploaded()
                }
                .onFailure { error ->
                    _state.value =
                        MediaUploadUiState(error = error.message ?: "Couldn't upload the file")
                }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    private fun isDocumentModel(model: NetBoxModelEntity): Boolean =
        model.appKey.contains("document", ignoreCase = true) &&
            model.modelKey.contains("document", ignoreCase = true)
}
