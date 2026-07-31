package dev.pschmitt.netboxandchill.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.sync.SyncStatusRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Backs [SyncStatusIndicator] - a thin ViewModel wrapper so the app-wide indicator can be hosted
 * with `hiltViewModel()` like every other screen-level ViewModel in this codebase, rather than
 * hand-wiring [SyncStatusRepository] into every composable that wants it.
 */
@HiltViewModel
class SyncStatusViewModel @Inject constructor(syncStatusRepository: SyncStatusRepository) :
    ViewModel() {

    val isSyncing: StateFlow<Boolean> =
        syncStatusRepository.isSyncing.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false,
        )
}
