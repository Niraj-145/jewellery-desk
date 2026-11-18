package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BillDetailBundle
import com.example.data.local.BillWithCustomer
import com.example.data.local.PaymentMethod
import com.example.data.local.SettingsEntity
import com.example.data.repository.JewelleryRepository
import com.example.domain.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BillStatusFilter(val label: String, val code: String?) {
    ALL("All", null),
    PAID("Paid", "PAID"),
    PARTIAL("Partial", "PARTIALLY_PAID"),
    UNPAID("Unpaid", "UNPAID")
}

enum class BillDateFilter(val label: String) {
    ALL("All Dates"),
    TODAY("Today"),
    THIS_MONTH("This Month"),
    CUSTOM("Pick Date")
}

data class BillsHistoryUiState(
    val searchQuery: String = "",
    val statusFilter: BillStatusFilter = BillStatusFilter.ALL,
    val dateFilter: BillDateFilter = BillDateFilter.ALL,
    val customDateMillis: Long = DateUtils.startOfDayMillis(),
    val bills: List<BillWithCustomer> = emptyList(),
    val currencySymbol: String = "₹"
)

data class BillDetailUiState(
    val bundle: BillDetailBundle? = null,
    val linkedInventoryCount: Int = 0,
    val settings: SettingsEntity = SettingsEntity()
)

class BillsViewModel(
    private val repository: JewelleryRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val statusFilter = MutableStateFlow(BillStatusFilter.ALL)
    private val dateFilter = MutableStateFlow(BillDateFilter.ALL)
    private val customDate = MutableStateFlow(DateUtils.startOfDayMillis())
    private val activeBillId = MutableStateFlow<Long?>(null)

    val historyUiState: StateFlow<BillsHistoryUiState> = combine(
        searchQuery,
        statusFilter,
        dateFilter,
        customDate,
        repository.observeAllBills(),
        repository.observeSettings()
    ) { values ->
        val query = values[0] as String
        val status = values[1] as BillStatusFilter
        val dFilter = values[2] as BillDateFilter
        val cDate = values[3] as Long
        @Suppress("UNCHECKED_CAST")
        val allBills = values[4] as List<BillWithCustomer>
        val settings = values[5] as SettingsEntity

        val (startMillis, endMillis) = when (dFilter) {
            BillDateFilter.ALL -> 0L to Long.MAX_VALUE
            BillDateFilter.TODAY -> DateUtils.startOfDayMillis() to DateUtils.endOfDayMillis()
            BillDateFilter.THIS_MONTH -> DateUtils.startOfMonthMillis() to DateUtils.endOfDayMillis()
            BillDateFilter.CUSTOM -> DateUtils.startOfDayMillis(cDate) to DateUtils.endOfDayMillis(cDate)
        }

        val filtered = allBills.filter { item ->
            val matchesQuery = if (query.isBlank()) true else {
                val q = query.trim().lowercase()
                item.bill.billNumber.lowercase().contains(q) ||
                    item.customerShopName.lowercase().contains(q) ||
                    item.customerOwnerName.lowercase().contains(q)
            }
            val matchesStatus = status.code == null || item.bill.paymentStatus == status.code
            val matchesDate = item.bill.billDate in startMillis..endMillis
            matchesQuery && matchesStatus && matchesDate
        }

        BillsHistoryUiState(
            searchQuery = query,
            statusFilter = status,
            dateFilter = dFilter,
            customDateMillis = cDate,
            bills = filtered,
            currencySymbol = settings.currencySymbol.ifBlank { "₹" }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BillsHistoryUiState()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val detailUiState: StateFlow<BillDetailUiState> = activeBillId
        .flatMapLatest { id ->
            if (id == null) flowOf(BillDetailUiState())
            else combine(
                repository.observeBillDetail(id),
                repository.observeAllInventory(),
                repository.observeSettings()
            ) { bundle, allInventory, settings ->
                val linkedCount = allInventory.count { it.item.linkedBillId == id }
                BillDetailUiState(
                    bundle = bundle,
                    linkedInventoryCount = linkedCount,
                    settings = settings
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BillDetailUiState()
        )

    fun setSearchQuery(query: String) { searchQuery.value = query }
    fun setStatusFilter(filter: BillStatusFilter) { statusFilter.value = filter }
    fun setDateFilter(filter: BillDateFilter) { dateFilter.value = filter }
    fun setCustomDate(millis: Long) {
        customDate.value = DateUtils.startOfDayMillis(millis)
        dateFilter.value = BillDateFilter.CUSTOM
    }

    fun loadBill(billId: Long) { activeBillId.value = billId }

    fun recordPaymentForBill(
        customerId: Long,
        billId: Long,
        amountPaise: Long,
        method: PaymentMethod,
        dateMillis: Long,
        notes: String
    ) {
        viewModelScope.launch {
            repository.recordPaymentTransaction(
                customerId = customerId,
                billId = billId,
                amountPaise = amountPaise,
                paymentMethod = method,
                paymentDateMillis = dateMillis,
                notes = notes
            )
        }
    }

    fun deleteBill(
        billId: Long,
        restoreLinkedInventory: Boolean = false,
        onDeleted: () -> Unit
    ) {
        viewModelScope.launch {
            repository.deleteBillConfirmed(
                billId = billId,
                restoreLinkedInventoryToAvailable = restoreLinkedInventory
            )
            onDeleted()
        }
    }
}
