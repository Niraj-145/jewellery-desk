package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BillWithCustomer
import com.example.data.local.PaymentWithDetails
import com.example.data.local.SettingsEntity
import com.example.data.repository.JewelleryRepository
import com.example.domain.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class DashboardDateFilter(val label: String) {
    TODAY("Today"),
    LAST_7_DAYS("7 Days"),
    THIS_MONTH("This Month"),
    ALL_TIME("All Time"),
    CUSTOM("Custom Date")
}

data class DashboardUiState(
    val selectedFilter: DashboardDateFilter = DashboardDateFilter.TODAY,
    val customDateMillis: Long = DateUtils.startOfDayMillis(),
    val periodSalesPaise: Long = 0L,
    val periodBillCount: Int = 0,
    val totalOutstandingPaise: Long = 0L,
    val periodReceivedPaise: Long = 0L,
    val totalCustomers: Int = 0,
    val recentBills: List<BillWithCustomer> = emptyList(),
    val recentPayments: List<PaymentWithDetails> = emptyList(),
    val settings: SettingsEntity = SettingsEntity()
)

class DashboardViewModel(
    repository: JewelleryRepository
) : ViewModel() {

    private val filterState = MutableStateFlow(DashboardDateFilter.TODAY)
    private val customDateState = MutableStateFlow(DateUtils.startOfDayMillis())

    val uiState: StateFlow<DashboardUiState> = combine(
        filterState,
        customDateState,
        repository.observeCustomersWithStats(),
        repository.observeAllBills(),
        repository.observeAllPayments(),
        repository.observeSettings()
    ) { values ->
        val filter = values[0] as DashboardDateFilter
        val customDate = values[1] as Long
        @Suppress("UNCHECKED_CAST")
        val customers = values[2] as List<com.example.data.local.CustomerWithStats>
        @Suppress("UNCHECKED_CAST")
        val bills = values[3] as List<BillWithCustomer>
        @Suppress("UNCHECKED_CAST")
        val payments = values[4] as List<PaymentWithDetails>
        val settings = values[5] as SettingsEntity

        val (startMillis, endMillis) = when (filter) {
            DashboardDateFilter.TODAY -> DateUtils.startOfDayMillis() to DateUtils.endOfDayMillis()
            DashboardDateFilter.LAST_7_DAYS -> DateUtils.startOfDaysAgoMillis(6) to DateUtils.endOfDayMillis()
            DashboardDateFilter.THIS_MONTH -> DateUtils.startOfMonthMillis() to DateUtils.endOfDayMillis()
            DashboardDateFilter.ALL_TIME -> 0L to Long.MAX_VALUE
            DashboardDateFilter.CUSTOM -> DateUtils.startOfDayMillis(customDate) to DateUtils.endOfDayMillis(customDate)
        }

        val filteredBills = bills.filter { it.bill.billDate in startMillis..endMillis }
        val filteredPayments = payments.filter { it.payment.paymentDate in startMillis..endMillis }

        DashboardUiState(
            selectedFilter = filter,
            customDateMillis = customDate,
            periodSalesPaise = filteredBills.sumOf { it.bill.finalAmountPaise },
            periodBillCount = filteredBills.size,
            totalOutstandingPaise = customers.sumOf { it.outstandingPaise },
            periodReceivedPaise = filteredPayments.sumOf { it.payment.amountPaise },
            totalCustomers = customers.size,
            recentBills = bills.take(5),
            recentPayments = payments.take(5),
            settings = settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun setDateFilter(filter: DashboardDateFilter) {
        filterState.value = filter
    }

    fun setCustomDate(millis: Long) {
        customDateState.value = DateUtils.startOfDayMillis(millis)
        filterState.value = DashboardDateFilter.CUSTOM
    }
}
