package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerWithStats
import com.example.data.local.InventoryItemEntity
import com.example.data.local.InventoryStatus
import com.example.data.local.PaymentMethod
import com.example.data.local.SettingsEntity
import com.example.data.remote.SawariyaFetchResult
import com.example.data.repository.DraftBillItemInput
import com.example.data.repository.JewelleryRepository
import com.example.data.repository.SaveInventoryResult
import com.example.domain.FinancialMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class DraftBillItem(
    val localId: String = UUID.randomUUID().toString(),
    val inventoryItemId: Long? = null,
    val productRefId: String? = null,
    val itemName: String,
    val productUrl: String? = null,
    val netWeightUg: Long,
    val imageUrl: String? = null,
    val notes: String = ""
)

enum class PaymentOption(val label: String) {
    FULLY_PAID("Fully Paid"),
    PARTIALLY_PAID("Partially Paid"),
    UNPAID("Unpaid")
}

data class ManualEntryPrompt(
    val prefilledUrl: String = "",
    val prefilledName: String = "",
    val prefilledWeightText: String = "",
    val prefilledNotes: String = "",
    val infoMessage: String? = null,
    val editingLocalId: String? = null
)

data class NewBillScannedPrompt(
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

data class NewBillDuplicatePrompt(
    val existingItem: InventoryItemEntity,
    val message: String = "This item is already saved in inventory."
)

data class NewBillUiState(
    val customers: List<CustomerWithStats> = emptyList(),
    val selectedCustomer: CustomerWithStats? = null,
    val customerSearchQuery: String = "",
    val availableInventory: List<InventoryItemEntity> = emptyList(),
    val reservedInventoryMap: Map<Long, String> = emptyMap(),
    val items: List<DraftBillItem> = emptyList(),
    val rateInput: String = "",
    val percentageInput: String = "72",
    val finalAmountInput: String = "",
    val finalAmountManuallyEdited: Boolean = false,
    val paymentOption: PaymentOption = PaymentOption.UNPAID,
    val amountReceivedInput: String = "0",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val billDateMillis: Long = System.currentTimeMillis(),
    val paymentDateMillis: Long = System.currentTimeMillis(),
    val paymentNotes: String = "",
    val billNotes: String = "",
    val isFetchingProduct: Boolean = false,
    val qrErrorBanner: String? = null,
    val manualEntryPrompt: ManualEntryPrompt? = null,
    val scannedVerifyPrompt: NewBillScannedPrompt? = null,
    val duplicateInventoryPrompt: NewBillDuplicatePrompt? = null,
    val isSaving: Boolean = false,
    val validationError: String? = null,
    val savedBillId: Long? = null,
    val currencySymbol: String = "₹",
    val settings: SettingsEntity = SettingsEntity()
) {
    val ratePerGramPaise: Long
        get() = FinancialMath.parseRupeesToPaise(rateInput) ?: 0L

    val percentageBps: Int
        get() = FinancialMath.parsePercentageToBps(percentageInput) ?: 0

    val totalWeightUg: Long
        get() = items.sumOf { it.netWeightUg }

    val calculatedAmountPaise: Long
        get() = items.sumOf {
            FinancialMath.calculateItemAmountPaise(
                netWeightUg = it.netWeightUg,
                ratePerGramPaise = ratePerGramPaise,
                percentageBps = percentageBps
            )
        }

    val finalAmountPaise: Long
        get() = FinancialMath.parseRupeesToPaise(finalAmountInput) ?: calculatedAmountPaise

    val amountReceivedPaise: Long
        get() = when (paymentOption) {
            PaymentOption.FULLY_PAID -> finalAmountPaise
            PaymentOption.UNPAID -> 0L
            PaymentOption.PARTIALLY_PAID -> FinancialMath.parseRupeesToPaise(amountReceivedInput) ?: 0L
        }

    val remainingBalancePaise: Long
        get() = FinancialMath.calculateRemainingBalancePaise(finalAmountPaise, amountReceivedPaise)
}

class NewBillViewModel(
    private val repository: JewelleryRepository
) : ViewModel() {

    private var draftSessionId: String = UUID.randomUUID().toString()

    private val _uiState = MutableStateFlow(NewBillUiState())
    val uiState: StateFlow<NewBillUiState> = _uiState.asStateFlow()

    private var defaultsInitialized = false

    init {
        viewModelScope.launch {
            repository.observeCustomersWithStats().collect { list ->
                _uiState.update { state ->
                    val updatedSelected = state.selectedCustomer?.let { sel ->
                        list.firstOrNull { it.customer.id == sel.customer.id }
                    }
                    state.copy(customers = list, selectedCustomer = updatedSelected)
                }
            }
        }
        viewModelScope.launch {
            combine(
                repository.observeAvailableInventory(),
                repository.reservedInventory
            ) { available, reservedMap ->
                available to reservedMap
            }.collect { (available, reservedMap) ->
                _uiState.update { state ->
                    val alreadyInDraftIds = state.items.mapNotNull { it.inventoryItemId }.toSet()
                    val selectable = available.filter { inv ->
                        val owner = reservedMap[inv.id]
                        !alreadyInDraftIds.contains(inv.id) && (owner == null || owner == draftSessionId)
                    }
                    state.copy(
                        availableInventory = selectable,
                        reservedInventoryMap = reservedMap
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.observeSettings().collect { settings ->
                _uiState.update { state ->
                    if (!defaultsInitialized) {
                        defaultsInitialized = true
                        val defaultRateStr = if (settings.defaultRatePaise > 0L) {
                            FinancialMath.paiseToPlainRupeesString(settings.defaultRatePaise)
                        } else ""
                        val defaultPctStr = if (settings.defaultPercentageBps > 0) {
                            FinancialMath.bpsToPlainPercentString(settings.defaultPercentageBps)
                        } else "72"
                        state.copy(
                            settings = settings,
                            currencySymbol = settings.currencySymbol.ifBlank { "₹" },
                            rateInput = defaultRateStr,
                            percentageInput = defaultPctStr
                        )
                    } else {
                        state.copy(
                            settings = settings,
                            currencySymbol = settings.currencySymbol.ifBlank { "₹" }
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        repository.releaseAllDraftReservations(draftSessionId)
        super.onCleared()
    }

    fun preselectCustomer(customerId: Long?) {
        if (customerId == null) return
        val match = _uiState.value.customers.firstOrNull { it.customer.id == customerId }
        if (match != null) {
            _uiState.update { it.copy(selectedCustomer = match, validationError = null) }
        } else {
            viewModelScope.launch {
                repository.observeCustomerWithStats(customerId).collect { found ->
                    if (found != null) {
                        _uiState.update { it.copy(selectedCustomer = found) }
                    }
                }
            }
        }
    }

    fun updateCustomerSearch(query: String) {
        _uiState.update { it.copy(customerSearchQuery = query) }
    }

    fun selectCustomer(customer: CustomerWithStats?) {
        _uiState.update { it.copy(selectedCustomer = customer, validationError = null) }
    }

    fun createAndSelectCustomer(
        shopName: String,
        ownerName: String,
        phone: String,
        address: String,
        gstNumber: String,
        openingBalanceInput: String,
        notes: String
    ) {
        val openingPaise = FinancialMath.parseRupeesToPaise(openingBalanceInput, allowNegative = true) ?: 0L
        viewModelScope.launch {
            val id = repository.saveCustomer(
                CustomerEntity(
                    shopName = shopName.trim(),
                    ownerName = ownerName.trim(),
                    phone = phone.trim(),
                    address = address.trim(),
                    gstNumber = gstNumber.trim(),
                    openingBalancePaise = openingPaise,
                    notes = notes.trim()
                )
            )
            preselectCustomer(id)
        }
    }

    // OPTION A: Select one or multiple Available inventory items and reserve in draft
    fun addInventoryItemsToBill(selectedItems: List<InventoryItemEntity>) {
        if (selectedItems.isEmpty()) return
        viewModelScope.launch {
            val added = mutableListOf<DraftBillItem>()
            var skippedCount = 0
            val existingIds = _uiState.value.items.mapNotNull { it.inventoryItemId }.toMutableSet()

            for (inv in selectedItems) {
                if (existingIds.contains(inv.id) || inv.status != InventoryStatus.AVAILABLE.code) {
                    skippedCount++
                    continue
                }
                val reserved = repository.reserveInventoryForDraft(draftSessionId, inv.id)
                if (reserved) {
                    existingIds.add(inv.id)
                    added.add(
                        DraftBillItem(
                            inventoryItemId = inv.id,
                            productRefId = inv.productRefId,
                            itemName = inv.itemName,
                            productUrl = inv.productUrl,
                            netWeightUg = inv.netWeightUg,
                            imageUrl = inv.imageUrl,
                            notes = inv.notes
                        )
                    )
                } else {
                    skippedCount++
                }
            }

            _uiState.update { state ->
                val nextItems = state.items + added
                val alreadySet = nextItems.mapNotNull { it.inventoryItemId }.toSet()
                val nextSelectable = state.availableInventory.filterNot { alreadySet.contains(it.id) }
                syncCalculatedAndFinal(
                    state.copy(
                        items = nextItems,
                        availableInventory = nextSelectable,
                        qrErrorBanner = if (skippedCount > 0 && added.isEmpty()) {
                            "Selected item(s) are already in an active draft or sold."
                        } else null,
                        validationError = null
                    )
                )
            }
        }
    }

    // OPTION B: Scan QR in billing -> duplicate/sold check, or verify & save/add
    fun onQrScannedOrUrlEntered(rawContent: String) {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return

        _uiState.update {
            it.copy(
                isFetchingProduct = true,
                qrErrorBanner = null,
                validationError = null,
                scannedVerifyPrompt = null,
                duplicateInventoryPrompt = null
            )
        }

        viewModelScope.launch {
            val existing = repository.findDuplicateInventoryItem(trimmed)
            if (existing != null) {
                handleExistingInventoryScan(existing)
                return@launch
            }

            when (val result = repository.fetchSawariyaProduct(trimmed)) {
                is SawariyaFetchResult.Success -> {
                    val p = result.product
                    val canonicalDup = repository.findDuplicateInventoryItem(p.productUrl)
                    if (canonicalDup != null) {
                        handleExistingInventoryScan(canonicalDup)
                    } else {
                        _uiState.update {
                            it.copy(
                                isFetchingProduct = false,
                                scannedVerifyPrompt = NewBillScannedPrompt(
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
                        _uiState.update {
                            it.copy(
                                isFetchingProduct = false,
                                qrErrorBanner = result.reason,
                                manualEntryPrompt = ManualEntryPrompt(
                                    prefilledUrl = result.normalizedUrl,
                                    prefilledName = result.suggestedName,
                                    infoMessage = result.reason
                                )
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isFetchingProduct = false,
                                scannedVerifyPrompt = NewBillScannedPrompt(
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

    private fun handleExistingInventoryScan(existing: InventoryItemEntity) {
        val currentState = _uiState.value
        val alreadyInThisBill = currentState.items.any { it.inventoryItemId == existing.id }
        val reservedOwner = repository.reservedInventory.value[existing.id]

        when {
            existing.status == InventoryStatus.SOLD.code -> {
                _uiState.update {
                    it.copy(
                        isFetchingProduct = false,
                        qrErrorBanner = "Item '${existing.itemName}' is already Sold and cannot be added to another bill."
                    )
                }
            }
            alreadyInThisBill -> {
                _uiState.update {
                    it.copy(
                        isFetchingProduct = false,
                        qrErrorBanner = "Item '${existing.itemName}' is already added to this draft bill."
                    )
                }
            }
            reservedOwner != null && reservedOwner != draftSessionId -> {
                _uiState.update {
                    it.copy(
                        isFetchingProduct = false,
                        qrErrorBanner = "Item '${existing.itemName}' is currently reserved in another active draft bill."
                    )
                }
            }
            else -> {
                _uiState.update {
                    it.copy(
                        isFetchingProduct = false,
                        duplicateInventoryPrompt = NewBillDuplicatePrompt(existingItem = existing)
                    )
                }
            }
        }
    }

    fun addExistingDuplicateToBill(existing: InventoryItemEntity) {
        _uiState.update { it.copy(duplicateInventoryPrompt = null) }
        addInventoryItemsToBill(listOf(existing))
    }

    fun dismissDuplicatePrompt() {
        _uiState.update { it.copy(duplicateInventoryPrompt = null) }
    }

    fun saveScannedToInventoryAndAddToBill(
        productUrl: String,
        productRefId: String?,
        itemName: String,
        category: String,
        netWeightText: String,
        otherDetails: String,
        imageUrl: String?,
        notes: String
    ): Boolean {
        val weightUg = FinancialMath.parseGramsToUg(netWeightText)
        if (itemName.isBlank() || weightUg == null || weightUg <= 0L || productUrl.isBlank()) {
            return false
        }

        viewModelScope.launch {
            when (
                val result = repository.saveInventoryItem(
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
                    repository.reserveInventoryForDraft(draftSessionId, result.itemId)
                    val draftItem = DraftBillItem(
                        inventoryItemId = result.itemId,
                        productRefId = productRefId ?: repository.extractProductRefId(productUrl),
                        itemName = itemName.trim(),
                        productUrl = repository.normalizeQrUrl(productUrl) ?: productUrl.trim(),
                        netWeightUg = weightUg,
                        imageUrl = imageUrl,
                        notes = notes.trim()
                    )
                    _uiState.update { state ->
                        syncCalculatedAndFinal(
                            state.copy(
                                items = state.items + draftItem,
                                scannedVerifyPrompt = null,
                                qrErrorBanner = null,
                                validationError = null
                            )
                        )
                    }
                }
                is SaveInventoryResult.Duplicate -> {
                    _uiState.update { it.copy(scannedVerifyPrompt = null) }
                    handleExistingInventoryScan(result.existing)
                }
            }
        }
        return true
    }

    fun addScannedWithoutSavingToInventory(
        productUrl: String,
        productRefId: String?,
        itemName: String,
        netWeightText: String,
        imageUrl: String?,
        notes: String
    ): Boolean {
        val weightUg = FinancialMath.parseGramsToUg(netWeightText)
        if (itemName.isBlank() || weightUg == null || weightUg <= 0L) {
            return false
        }
        val draftItem = DraftBillItem(
            inventoryItemId = null,
            productRefId = productRefId,
            itemName = itemName.trim(),
            productUrl = productUrl.trim().takeIf { it.isNotEmpty() },
            netWeightUg = weightUg,
            imageUrl = imageUrl,
            notes = notes.trim()
        )
        _uiState.update { state ->
            syncCalculatedAndFinal(
                state.copy(
                    items = state.items + draftItem,
                    scannedVerifyPrompt = null,
                    qrErrorBanner = null,
                    validationError = null
                )
            )
        }
        return true
    }

    fun dismissScannedPrompt() {
        _uiState.update { it.copy(scannedVerifyPrompt = null) }
    }

    fun openManualEntry(itemToEdit: DraftBillItem? = null) {
        _uiState.update {
            it.copy(
                manualEntryPrompt = if (itemToEdit != null) {
                    ManualEntryPrompt(
                        prefilledUrl = itemToEdit.productUrl.orEmpty(),
                        prefilledName = itemToEdit.itemName,
                        prefilledWeightText = FinancialMath.ugToPlainGramsString(itemToEdit.netWeightUg),
                        prefilledNotes = itemToEdit.notes,
                        editingLocalId = itemToEdit.localId
                    )
                } else {
                    ManualEntryPrompt()
                }
            )
        }
    }

    fun dismissManualEntry() {
        _uiState.update { it.copy(manualEntryPrompt = null) }
    }

    fun dismissQrBanner() {
        _uiState.update { it.copy(qrErrorBanner = null) }
    }

    fun saveManualItem(
        itemName: String,
        productUrl: String,
        netWeightText: String,
        notes: String
    ): Boolean {
        val weightUg = FinancialMath.parseGramsToUg(netWeightText)
        if (itemName.isBlank() || weightUg == null || weightUg <= 0L) {
            return false
        }
        val editingId = _uiState.value.manualEntryPrompt?.editingLocalId
        _uiState.update { state ->
            val updatedItems = if (editingId != null) {
                state.items.map { existing ->
                    if (existing.localId == editingId) {
                        existing.copy(
                            itemName = itemName.trim(),
                            productUrl = productUrl.trim().takeIf { it.isNotEmpty() },
                            netWeightUg = weightUg,
                            notes = notes.trim()
                        )
                    } else existing
                }
            } else {
                state.items + DraftBillItem(
                    inventoryItemId = null,
                    itemName = itemName.trim(),
                    productUrl = productUrl.trim().takeIf { it.isNotEmpty() },
                    netWeightUg = weightUg,
                    notes = notes.trim()
                )
            }
            syncCalculatedAndFinal(
                state.copy(
                    items = updatedItems,
                    manualEntryPrompt = null,
                    qrErrorBanner = null,
                    validationError = null
                )
            )
        }
        return true
    }

    fun removeItem(localId: String) {
        val removed = _uiState.value.items.firstOrNull { it.localId == localId }
        if (removed?.inventoryItemId != null) {
            repository.releaseInventoryReservation(draftSessionId, removed.inventoryItemId)
        }
        _uiState.update { state ->
            syncCalculatedAndFinal(
                state.copy(items = state.items.filterNot { it.localId == localId })
            )
        }
    }

    fun cancelDraft() {
        repository.releaseAllDraftReservations(draftSessionId)
        resetAfterNavigation()
    }

    fun updateRate(input: String) {
        _uiState.update { state ->
            syncCalculatedAndFinal(state.copy(rateInput = input, validationError = null))
        }
    }

    fun updatePercentage(input: String) {
        _uiState.update { state ->
            syncCalculatedAndFinal(state.copy(percentageInput = input, validationError = null))
        }
    }

    fun updateFinalAmount(input: String) {
        _uiState.update { state ->
            val updated = state.copy(
                finalAmountInput = input,
                finalAmountManuallyEdited = true,
                validationError = null
            )
            if (updated.paymentOption == PaymentOption.FULLY_PAID) {
                updated.copy(amountReceivedInput = input)
            } else updated
        }
    }

    fun resetFinalAmountToCalculated() {
        _uiState.update { state ->
            val calcStr = FinancialMath.paiseToPlainRupeesString(state.calculatedAmountPaise)
            val next = state.copy(
                finalAmountInput = calcStr,
                finalAmountManuallyEdited = false
            )
            if (next.paymentOption == PaymentOption.FULLY_PAID) {
                next.copy(amountReceivedInput = calcStr)
            } else next
        }
    }

    fun updatePaymentOption(option: PaymentOption) {
        _uiState.update { state ->
            val amountStr = when (option) {
                PaymentOption.FULLY_PAID -> FinancialMath.paiseToPlainRupeesString(state.finalAmountPaise)
                PaymentOption.UNPAID -> "0"
                PaymentOption.PARTIALLY_PAID -> if (state.paymentOption == PaymentOption.PARTIALLY_PAID) {
                    state.amountReceivedInput
                } else ""
            }
            state.copy(
                paymentOption = option,
                amountReceivedInput = amountStr,
                validationError = null
            )
        }
    }

    fun updateAmountReceived(input: String) {
        _uiState.update { it.copy(amountReceivedInput = input, validationError = null) }
    }

    fun updatePaymentMethod(method: PaymentMethod) {
        _uiState.update { it.copy(paymentMethod = method) }
    }

    fun updateBillDate(millis: Long) {
        _uiState.update { it.copy(billDateMillis = millis) }
    }

    fun updatePaymentDate(millis: Long) {
        _uiState.update { it.copy(paymentDateMillis = millis) }
    }

    fun updatePaymentNotes(notes: String) {
        _uiState.update { it.copy(paymentNotes = notes) }
    }

    fun updateBillNotes(notes: String) {
        _uiState.update { it.copy(billNotes = notes) }
    }

    private fun syncCalculatedAndFinal(state: NewBillUiState): NewBillUiState {
        if (state.finalAmountManuallyEdited) return state
        val calcStr = if (state.calculatedAmountPaise > 0L) {
            FinancialMath.paiseToPlainRupeesString(state.calculatedAmountPaise)
        } else ""
        return state.copy(
            finalAmountInput = calcStr,
            amountReceivedInput = if (state.paymentOption == PaymentOption.FULLY_PAID) calcStr else state.amountReceivedInput
        )
    }

    fun saveBill() {
        val current = _uiState.value
        val customer = current.selectedCustomer
        if (customer == null) {
            _uiState.update { it.copy(validationError = "Please select a retailer customer first.") }
            return
        }
        if (current.items.isEmpty()) {
            _uiState.update { it.copy(validationError = "Add at least one jewellery item to the bill.") }
            return
        }
        val ratePaise = FinancialMath.parseRupeesToPaise(current.rateInput)
        if (ratePaise == null || ratePaise <= 0L) {
            _uiState.update { it.copy(validationError = "Enter a valid Today's Rate (₹ per gram).") }
            return
        }
        val pctBps = FinancialMath.parsePercentageToBps(current.percentageInput)
        if (pctBps == null || pctBps <= 0) {
            _uiState.update { it.copy(validationError = "Enter a valid Percentage (> 0).") }
            return
        }
        val finalPaise = FinancialMath.parseRupeesToPaise(current.finalAmountInput)
        if (finalPaise == null || finalPaise < 0L) {
            _uiState.update { it.copy(validationError = "Enter a valid Final Bill Amount.") }
            return
        }
        val receivedPaise = when (current.paymentOption) {
            PaymentOption.FULLY_PAID -> finalPaise
            PaymentOption.UNPAID -> 0L
            PaymentOption.PARTIALLY_PAID -> FinancialMath.parseRupeesToPaise(current.amountReceivedInput)
        }
        if (receivedPaise == null || receivedPaise < 0L) {
            _uiState.update { it.copy(validationError = "Enter a valid Amount Received.") }
            return
        }
        if (receivedPaise > finalPaise) {
            _uiState.update { it.copy(validationError = "Amount received cannot exceed the final bill amount.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            try {
                val billId = repository.saveBillTransaction(
                    customerId = customer.customer.id,
                    billDateMillis = current.billDateMillis,
                    ratePerGramPaise = ratePaise,
                    percentageBps = pctBps,
                    items = current.items.map {
                        DraftBillItemInput(
                            inventoryItemId = it.inventoryItemId,
                            itemName = it.itemName,
                            productUrl = it.productUrl,
                            netWeightUg = it.netWeightUg,
                            notes = it.notes
                        )
                    },
                    finalAmountPaise = finalPaise,
                    amountReceivedPaise = receivedPaise,
                    paymentMethod = current.paymentMethod,
                    paymentDateMillis = current.paymentDateMillis,
                    paymentNotes = current.paymentNotes,
                    billNotes = current.billNotes,
                    draftSessionId = draftSessionId
                )
                _uiState.update { it.copy(isSaving = false, savedBillId = billId) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        validationError = e.localizedMessage ?: "Failed to save bill."
                    )
                }
            }
        }
    }

    fun resetAfterNavigation() {
        repository.releaseAllDraftReservations(draftSessionId)
        draftSessionId = UUID.randomUUID().toString()
        defaultsInitialized = false
        val currentState = _uiState.value
        val settings = currentState.settings
        val defaultRateStr = if (settings.defaultRatePaise > 0L) {
            FinancialMath.paiseToPlainRupeesString(settings.defaultRatePaise)
        } else ""
        val defaultPctStr = if (settings.defaultPercentageBps > 0) {
            FinancialMath.bpsToPlainPercentString(settings.defaultPercentageBps)
        } else "72"
        _uiState.value = NewBillUiState(
            customers = currentState.customers,
            availableInventory = currentState.availableInventory,
            reservedInventoryMap = repository.reservedInventory.value,
            rateInput = defaultRateStr,
            percentageInput = defaultPctStr,
            currencySymbol = settings.currencySymbol.ifBlank { "₹" },
            settings = settings
        )
        defaultsInitialized = true
    }
}
