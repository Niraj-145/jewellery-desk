package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SettingsEntity
import com.example.data.repository.JewelleryRepository
import com.example.domain.FinancialMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val businessName: String = "",
    val businessAddress: String = "",
    val businessPhone: String = "",
    val gstNumber: String = "",
    val defaultRateInput: String = "",
    val defaultPercentageInput: String = "",
    val currencySymbol: String = "₹",
    val invoiceFooter: String = "",
    val statusMessage: String? = null,
    val isError: Boolean = false
)

class SettingsViewModel(
    private val repository: JewelleryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val s = repository.getSettingsOnce()
            _uiState.update {
                it.copy(
                    businessName = s.businessName,
                    businessAddress = s.businessAddress,
                    businessPhone = s.businessPhone,
                    gstNumber = s.gstNumber,
                    defaultRateInput = if (s.defaultRatePaise > 0L) FinancialMath.paiseToPlainRupeesString(s.defaultRatePaise) else "",
                    defaultPercentageInput = FinancialMath.bpsToPlainPercentString(s.defaultPercentageBps),
                    currencySymbol = s.currencySymbol,
                    invoiceFooter = s.invoiceFooter
                )
            }
        }
    }

    fun updateField(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.update { transform(it).copy(statusMessage = null, isError = false) }
    }

    fun saveSettings() {
        val current = _uiState.value
        val ratePaise = if (current.defaultRateInput.isBlank()) 0L else {
            FinancialMath.parseRupeesToPaise(current.defaultRateInput)
                ?: run {
                    _uiState.update { it.copy(statusMessage = "Invalid default rate.", isError = true) }
                    return
                }
        }
        val pctBps = FinancialMath.parsePercentageToBps(current.defaultPercentageInput)
            ?: run {
                _uiState.update { it.copy(statusMessage = "Invalid default percentage.", isError = true) }
                return
            }

        viewModelScope.launch {
            repository.saveSettings(
                SettingsEntity(
                    id = 1,
                    businessName = current.businessName.trim().ifBlank { "Jewellery Desk Wholesale" },
                    businessAddress = current.businessAddress.trim(),
                    businessPhone = current.businessPhone.trim(),
                    gstNumber = current.gstNumber.trim(),
                    defaultRatePaise = ratePaise,
                    defaultPercentageBps = pctBps,
                    currencySymbol = current.currencySymbol.trim().ifBlank { "₹" },
                    invoiceFooter = current.invoiceFooter.trim()
                )
            )
            _uiState.update { it.copy(statusMessage = "Business settings saved.", isError = false) }
        }
    }

    suspend fun exportBackupJson(): String = repository.exportBackupJson()

    fun restoreBackupJson(rawJson: String) {
        viewModelScope.launch {
            try {
                repository.restoreBackupJson(rawJson)
                val s = repository.getSettingsOnce()
                _uiState.value = SettingsUiState(
                    businessName = s.businessName,
                    businessAddress = s.businessAddress,
                    businessPhone = s.businessPhone,
                    gstNumber = s.gstNumber,
                    defaultRateInput = if (s.defaultRatePaise > 0L) FinancialMath.paiseToPlainRupeesString(s.defaultRatePaise) else "",
                    defaultPercentageInput = FinancialMath.bpsToPlainPercentString(s.defaultPercentageBps),
                    currencySymbol = s.currencySymbol,
                    invoiceFooter = s.invoiceFooter,
                    statusMessage = "Backup restored successfully.",
                    isError = false
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage = "Restore failed: ${e.localizedMessage ?: "Invalid backup file."}",
                        isError = true
                    )
                }
            }
        }
    }

    fun showStatus(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(statusMessage = message, isError = isError) }
    }
}
