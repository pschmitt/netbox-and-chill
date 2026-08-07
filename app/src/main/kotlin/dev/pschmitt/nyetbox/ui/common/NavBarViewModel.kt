package dev.pschmitt.nyetbox.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.nyetbox.data.repository.NavBarItem
import dev.pschmitt.nyetbox.data.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Backs [NetBoxBottomBar] - a thin ViewModel wrapper so the shared bottom bar/rail can read the
 * user's configured slots with `hiltViewModel()` like every other screen-level ViewModel, rather
 * than threading [SettingsRepository] into every screen that hosts the bar.
 */
@HiltViewModel
class NavBarViewModel @Inject constructor(settingsRepository: SettingsRepository) : ViewModel() {

    val items: StateFlow<List<NavBarItem>> =
        settingsRepository.navBarItems.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList(),
        )
}
