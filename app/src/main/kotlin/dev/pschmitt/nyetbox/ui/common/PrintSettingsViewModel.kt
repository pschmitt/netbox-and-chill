package dev.pschmitt.nyetbox.ui.common

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.nyetbox.data.repository.PrintSettings
import dev.pschmitt.nyetbox.data.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PrintSettingsViewModel
@Inject
constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<PrintSettings> = settingsRepository.printSettings

    fun update(transform: (PrintSettings) -> PrintSettings) {
        settingsRepository.updatePrintSettings(transform(settingsRepository.printSettings.value))
    }

    fun setDefaultPrinter(name: String, address: String) {
        update { it.copy(defaultPrinterName = name, defaultPrinterAddress = address) }
    }

    fun clearDefaultPrinter() {
        update { it.copy(defaultPrinterName = null, defaultPrinterAddress = null) }
    }
}
