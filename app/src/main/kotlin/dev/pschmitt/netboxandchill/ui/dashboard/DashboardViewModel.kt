package dev.pschmitt.netboxandchill.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.db.BookmarkEntity
import dev.pschmitt.netboxandchill.data.db.DashboardStatEntity
import dev.pschmitt.netboxandchill.data.db.ObjectChangeEntity
import dev.pschmitt.netboxandchill.data.repository.DashboardRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(private val repository: DashboardRepository) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val stats: StateFlow<List<DashboardStatEntity>> =
        repository.observeStats().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<BookmarkEntity>> =
        repository.observeBookmarks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val changelog: StateFlow<List<ObjectChangeEntity>> =
        repository.observeChangelog().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refresh().onFailure {
                _errorMessage.value = it.message ?: "Sync failed - showing cached data"
            }
            _isRefreshing.value = false
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }
}
