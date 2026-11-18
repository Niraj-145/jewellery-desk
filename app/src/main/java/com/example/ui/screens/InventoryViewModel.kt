package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.InventoryItemEntity
import com.example.data.local.InventoryItemWithBill
import com.example.data.local.InventoryStatus
import com.example.data.remote.SawariyaFetchResult
import com.example.data.repository.JewelleryRepository
import com.example.data.repository.SaveInventoryResult
import com.example.domain.DateUtils
import com.example.domain.FinancialMath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InventoryStatusFilter(val label: String) {
    ALL("All"),
    AVAILABLE("Available"),
    SOLD("Sold")
}

enum class InventoryDateFilter(val label: String) {
    ALL("All Dates"),
    TODAY("Today"),
    THIS_MONTH("This Month"),
    CUSTOM("Pick Date")
}

data class InventoryVerifyPrompt(
    val productUrl: String,
    val productRefId: String? = null,
    val itemName: String = "",
    val category: String? = null,
    val weightText: String = "",
    val otherDetails: String = "",
    val imageUrl: String? = null,
    val notes: String = "",
    val infoMessage: String? = null
)

data class InventoryDuplicatePrompt(
    val existingItem: InventoryItemEntity,
    val message: String = "This item is already saved in inventory."
)

private data class InventoryScanState(
    val isFetchingProduct: Boolean = false,
    val isSaving: Boolean = false,
    val scanErrorBanner: String? = null,
    val duplicatePrompt: InventoryDuplicatePrompt? = null,
    val verifyPrompt: InventoryVerifyPrompt? = null,
    val lastSavedItemId: Long? = null
)

data class InventoryListUiState(
    val totalItems: Int = 0,
    val availableItems: Int = 0,
    val soldItems: Int = 0,
    val totalAvailableWeightUg: Long = 0L,
    val items: List<InventoryItemWithBill> = emptyList(),
    val reservedItemIds: Set<Long> = emptySet(),
    val searchQuery: String = "",
    val statusFilter: InventoryStatusFilter = InventoryStatusFilter.ALL,
    val dateFilter: InventoryDateFilter = InventoryDateFilter.ALL,
    val customDateMillis: Long = DateUtils.startOfDayMillis(),
    val isFetchingProduct: Boolean = false,
    val isSaving: Boolean = false,
    val scanErrorBanner: String? = null,
    val duplicatePrompt: InventoryDuplicatePrompt? = null,
    val verifyPrompt: InventoryVerifyPrompt? = null,
    val lastSavedItemId: Long? = null
)

data class InventoryDetailUiState(
    val bundle: InventoryItemWithBill? = null,
    val isReservedInDraft: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class InventoryViewModel(
    private val repository: JewelleryRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val statusFilter = MutableStateFlow(InventoryStatusFilter.ALL)
    private val dateFilter = MutableStateFlow(InventoryDateFilter.ALL)
    private val customDate = MutableStateFlow(DateUtils.startOfDayMillis())
    private val scanState = MutableStateFlow(InventoryScanState())

    private val activeItemId = MutableStateFlow<Long?>(null)
    private val detailFeedback = MutableStateFlow<Pair<String?, String?>>(null to null)

    val listUiState: StateFlow<InventoryListUiState> = combine(
        repository.observeAllInventory(),
        repository.reservedInventory,
        searchQuery,
        statusFilter,
        dateFilter,
        customDate,
        scanState
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allItems = values[0] as List<InventoryItemWithBill>
        @Suppress("UNCHECKED_CAST")
        val reservedMap = values[1] as Map<Long, String>
        val query = values[2] as String
        val status = values[3] as InventoryStatusFilter
        val dFilter = values[4] as InventoryDateFilter
        val cDate = values[5] as Long
        val scan = values[6] as InventoryScanState

        val availableList = allItems.filter { it.item.status == InventoryStatus.AVAILABLE.code }
        val soldList = allItems.filter { it.item.status == InventoryStatus.SOLD.code }

        val (startMillis, endMillis) = when (dFilter) {
            InventoryDateFilter.ALL -> 0L to Long.MAX_VALUE
            InventoryDateFilter.TODAY -> DateUtils.startOfDayMillis() to DateUtils.endOfDayMillis()
            InventoryDateFilter.THIS_MONTH -> DateUtils.startOfMonthMillis() to DateUtils.endOfDayMillis()
            InventoryDateFilter.CUSTOM -> DateUtils.startOfDayMillis(cDate) to DateUtils.endOfDayMillis(cDate)
        }

        val filtered = allItems.filter { entry ->
            val item = entry.item
            val matchesQuery = if (query.isBlank()) true else {
                val q = query.trim().lowercase()
                item.itemName.lowercase().contains(q) ||
                    item.productUrl.lowercase().contains(q) ||
                    (item.productRefId?.lowercase()?.contains(q) == true) ||
                    (item.category?.lowercase()?.contains(q) == true) ||
                    item.displayInventoryId.lowercase().contains(q)
            }
            val matchesStatus = when (status) {
                InventoryStatusFilter.ALL -> true
                InventoryStatusFilter.AVAILABLE -> item.status == InventoryStatus.AVAILABLE.code
                InventoryStatusFilter.SOLD -> item.status == InventoryStatus.SOLD.code
            }
            val matchesDate = item.dateAdded in startMillis..endMillis
            matchesQuery && matchesStatus && matchesDate
        }

        InventoryListUiState(
            totalItems = allItems.size,
            availableItems = availableList.size,
            soldItems = soldList.size,
            totalAvailableWeightUg = availableList.sumOf { it.item.netWeightUg },
            items = filtered,
            reservedItemIds = reservedMap.keys,
            searchQuery = query,
            statusFilter = status,
            dateFilter = dFilter,
            customDateMillis = cDate,
            isFetchingProduct = scan.isFetchingProduct,
            isSaving = scan.isSaving,
            scanErrorBanner = scan.scanErrorBanner,
            duplicatePrompt = scan.duplicatePrompt,
            verifyPrompt = scan.verifyPrompt,
            lastSavedItemId = scan.lastSavedItemId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InventoryListUiState()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val detailUiState: StateFlow<InventoryDetailUiState> = activeItemId
        .flatMapLatest { id ->
            if (id == null) flowOf(InventoryDetailUiState())
            else combine(
                repository.observeInventoryItem(id),
                repository.reservedInventory,
                detailFeedback
            ) { bundle, reservedMap, feedback ->
                InventoryDetailUiState(
                    bundle = bundle,
                    isReservedInDraft = reservedMap.containsKey(id),
                    errorMessage = feedback.first,
                    successMessage = feedback.second
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = InventoryDetailUiState()
        )

    fun setSearchQuery(query: String) { searchQuery.value = query }
    fun setStatusFilter(filter: InventoryStatusFilter) { statusFilter.value = filter }
    fun setDateFilter(filter: InventoryDateFilter) { dateFilter.value = filter }
    fun setCustomDate(millis: Long) {
        customDate.value = DateUtils.startOfDayMillis(millis)
        dateFilter.value = InventoryDateFilter.CUSTOM
    }

    fun onQrScanned(rawContent: String) {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return

        scanState.update {
            it.copy(
                isFetchingProduct = true,
                scanErrorBanner = null,
                duplicatePrompt = null,
                verifyPrompt = null
            )
        }

        viewModelScope.launch {
            // Check duplicate first before fetching network
            val existing = repository.findDuplicateInventoryItem(trimmed)
            if (existing != null) {
                scanState.update {
                    it.copy(
                        isFetchingProduct = false,
                        duplicatePrompt = InventoryDuplicatePrompt(existingItem = existing)
                    )
                }
                return@launch
            }

            when (val result = repository.fetchSawariyaProduct(trimmed)) {
                is SawariyaFetchResult.Success -> {
                    val p = result.product
                    // Re-check canonical URL duplicate in case of redirect or normalization
                    val canonicalDup = repository.findDuplicateInventoryItem(p.productUrl)
                    if (canonicalDup != null) {
                        scanState.update {
                            it.copy(
                                isFetchingProduct = false,
                                duplicatePrompt = InventoryDuplicatePrompt(existingItem = canonicalDup)
                            )
                        }
                    } else {
                        scanState.update {
                            it.copy(
                                isFetchingProduct = false,
                                verifyPrompt = InventoryVerifyPrompt(
                                    productUrl = p.productUrl,
                                    productRefId = p.productRefId,
                                    itemName = p.itemName,
                                    category = p.category,
                                    weightText = p.netWeightDisplay,
                                    otherDetails = p.otherDetails,
                                    imageUrl = p.imageUrl,
                                    notes = p.notes
                                )
                            )
                        }
                    }
                }
                is SawariyaFetchResult.PartialOrFailure -> {
                    val isInvalidUrl = repository.normalizeQrUrl(trimmed) == null
                    if (isInvalidUrl) {
                        scanState.update {
                            it.copy(
                                isFetchingProduct = false,
                                scanErrorBanner = result.reason
                            )
                        }
                    } else {
                        scanState.update {
                            it.copy(
                                isFetchingProduct = false,
                                verifyPrompt = InventoryVerifyPrompt(
                                    productUrl = result.normalizedUrl,
                                    productRefId = result.productRefId,
                                    itemName = result.suggestedName,
                                    category = result.category,
                                    weightText = "",
                                    otherDetails = result.otherDetails,
                                    imageUrl = result.imageUrl,
                                    notes = "",
                                    infoMessage = result.reason
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun saveScannedInventoryItem(
        productUrl: String,
        productRefId: String?,
        itemName: String,
        category: String,
        weightText: String,
        otherDetails: String,
        imageUrl: String?,
        notes: String
    ): Boolean {
        val weightUg = FinancialMath.parseGramsToUg(weightText)
        if (itemName.isBlank() || weightUg == null || weightUg <= 0L || productUrl.isBlank()) {
            return false
        }

        scanState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                when (
                    val res = repository.saveInventoryItem(
                        productUrl = productUrl,
                        productRefId = productRefId,
                        itemName = itemName,
                        category = category.trim().takeIf { it.isNotEmpty() },
                        netWeightUg = weightUg,
                        otherDetails = otherDetails,
                        imageUrl = imageUrl,
                        notes = notes
                    )
                ) {
                    is SaveInventoryResult.Saved -> {
                        scanState.update {
                            it.copy(
                                isSaving = false,
                                verifyPrompt = null,
                                scanErrorBanner = null,
                                lastSavedItemId = res.itemId
                            )
                        }
                    }
                    is SaveInventoryResult.Duplicate -> {
                        scanState.update {
                            it.copy(
                                isSaving = false,
                                verifyPrompt = null,
                                duplicatePrompt = InventoryDuplicatePrompt(existingItem = res.existing)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                scanState.update {
                    it.copy(
                        isSaving = false,
                        scanErrorBanner = e.localizedMessage ?: "Failed to save inventory item."
                    )
                }
            }
        }
        return true
    }

    fun dismissScanPrompts() {
        scanState.update {
            it.copy(
                verifyPrompt = null,
                duplicatePrompt = null,
                scanErrorBanner = null
            )
        }
    }

    fun consumeLastSavedItem() {
        scanState.update { it.copy(lastSavedItemId = null) }
    }

    fun loadItem(itemId: Long) {
        activeItemId.value = itemId
        detailFeedback.value = null to null
    }

    fun clearDetailFeedback() {
        detailFeedback.value = null to null
    }

    fun updateAvailableItem(
        current: InventoryItemEntity,
        itemName: String,
        category: String,
        weightText: String,
        otherDetails: String,
        notes: String
    ): Boolean {
        val weightUg = FinancialMath.parseGramsToUg(weightText)
        if (itemName.isBlank() || weightUg == null || weightUg <= 0L) return false
        viewModelScope.launch {
            try {
                repository.updateInventoryItem(
                    item = current.copy(
                        itemName = itemName.trim(),
                        category = category.trim().takeIf { it.isNotEmpty() },
                        netWeightUg = weightUg,
                        otherDetails = otherDetails.trim(),
                        notes = notes.trim()
                    ),
                    authorizedSoldCorrection = false
                )
                detailFeedback.value = null to "Inventory item updated."
            } catch (e: Exception) {
                detailFeedback.value = (e.localizedMessage ?: "Update failed.") to null
            }
        }
        return true
    }

    fun authorizeAndSaveSoldCorrection(
        current: InventoryItemEntity,
        itemName: String,
        category: String,
        weightText: String,
        otherDetails: String,
        notes: String
    ): Boolean {
        val weightUg = FinancialMath.parseGramsToUg(weightText)
        if (itemName.isBlank() || weightUg == null || weightUg <= 0L) return false
        viewModelScope.launch {
            try {
                repository.updateInventoryItem(
                    item = current.copy(
                        itemName = itemName.trim(),
                        category = category.trim().takeIf { it.isNotEmpty() },
                        netWeightUg = weightUg,
                        otherDetails = otherDetails.trim(),
                        notes = notes.trim()
                    ),
                    authorizedSoldCorrection = true
                )
                detailFeedback.value = null to "Authorized correction saved."
            } catch (e: Exception) {
                detailFeedback.value = (e.localizedMessage ?: "Authorized update failed.") to null
            }
        }
        return true
    }

    fun authorizeAndRestoreSoldToAvailable(itemId: Long) {
        viewModelScope.launch {
            try {
                repository.restoreSoldItemAuthorized(itemId)
                detailFeedback.value = null to "Item restored to Available inventory."
            } catch (e: Exception) {
                detailFeedback.value = (e.localizedMessage ?: "Restore failed.") to null
            }
        }
    }

    fun deleteAvailableItem(itemId: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val deleted = repository.deleteAvailableInventoryItem(itemId)
            if (deleted) {
                onDeleted()
            } else {
                detailFeedback.value = "Only Available items can be deleted." to null
            }
        }
    }
}
