package dev.pschmitt.netboxandchill.ui.generic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.db.DeviceTypeEntity
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.data.repository.DeviceTypeRepository
import dev.pschmitt.netboxandchill.data.repository.FileDownloadRepository
import dev.pschmitt.netboxandchill.data.repository.GenericObjectRepository
import dev.pschmitt.netboxandchill.data.repository.GlobalSearchRepository
import dev.pschmitt.netboxandchill.sync.SyncScheduler
import dev.pschmitt.netboxandchill.sync.SyncStatusRepository
import dev.pschmitt.netboxandchill.ui.navigation.Route
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GenericListViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GenericObjectRepository,
    private val deviceTypeRepository: DeviceTypeRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    private val syncScheduler: SyncScheduler,
    syncStatusRepository: SyncStatusRepository,
) : ViewModel() {

    val route: Route.GenericList = savedStateHandle.toRoute()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val isRefreshing: StateFlow<Boolean> =
        syncStatusRepository.isSyncing.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false,
        )

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val objects: StateFlow<List<NetBoxObjectEntity>> =
        _query
            .flatMapLatest {
                repository.observeObjects(
                    route.endpointPath,
                    it,
                    route.filterKey,
                    route.filterValue,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deviceTypeImages: StateFlow<Map<Int, DeviceTypeEntity>> =
        deviceTypeRepository
            .observeAll()
            .map { types ->
                if (route.endpointPath == GlobalSearchRepository.DEVICE_TYPES_ENDPOINT_PATH) {
                    types.associateBy { it.id }
                } else {
                    emptyMap()
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun refresh() {
        syncScheduler.syncNow()
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    fun localImageFile(url: String, filename: String): File? =
        fileDownloadRepository.persistentFile(url, filename)
}
