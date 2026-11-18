package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BillWithCustomer
import com.example.data.local.CustomerWithStats
import com.example.data.local.PaymentWithDetails
import com.example.data.local.SettingsEntity
import com.example.data.repository.JewelleryRepository
import com.example.domain.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class ReportPeriod(val label: String) {
    DAILY("Daily (Today)"),
    WEEKLY("Weekly (7d)"),
    MONTHLY("Monthly"),
    CUSTOM("Custom Range")
}

data class DailyChartBar(
    val label: String,
    val salesPaise: Long
)

data class CustomerSalesRow(
    val customerId: Long,
    val shopName: String,
    val billCount: Int,
    val totalSalesPaise: Long,
    val outstandingPaise: Long
)

data class ReportsUiState(
    val period: ReportPeriod = ReportPeriod.WEEKLY,
    val customStartMillis: Long = DateUtils.startOfDaysAgoMillis(6),
    val customEndMillis: Long = DateUtils.endOfDayMillis(),
    val periodSalesPaise: Long = 0L,
    val periodReceivedPaise: Long = 0L,
    val totalOutstandingPaise: Long = 0L,
    val periodBillCount: Int = 0,
    val chartBars: List<DailyChartBar> = emptyList(),
    val customerRows: List<CustomerSalesRow> = emptyList(),
    val paymentHistory: List<PaymentWithDetails> = emptyList(),
    val currencySymbol: String = "₹"
)

class ReportsViewModel(
    repository: JewelleryRepository
) : ViewModel() {

    private val periodState = MutableStateFlow(ReportPeriod.WEEKLY)
    private val customRangeState = MutableStateFlow(
        DateUtils.startOfDaysAgoMillis(6) to DateUtils.endOfDayMillis()
    )

    val uiState: StateFlow<ReportsUiState> = combine(
        periodState,
        customRangeState,
        repository.observeAllBills(),
        repository.observeCustomersWithStats(),
        repository.observeAllPayments(),
        repository.observeSettings()
    ) { values ->
        val period = values[0] as ReportPeriod
        @Suppress("UNCHECKED_CAST")
        val customRange = values[1] as Pair<Long, Long>
        @Suppress("UNCHECKED_CAST")
        val allBills = values[2] as List<BillWithCustomer>
        @Suppress("UNCHECKED_CAST")
        val customers = values[3] as List<CustomerWithStats>
        @Suppress("UNCHECKED_CAST")
        val allPayments = values[4] as List<PaymentWithDetails>
        val settings = values[5] as SettingsEntity

        val (startMillis, endMillis) = when (period) {
            ReportPeriod.DAILY -> DateUtils.startOfDayMillis() to DateUtils.endOfDayMillis()
            ReportPeriod.WEEKLY -> DateUtils.startOfDaysAgoMillis(6) to DateUtils.endOfDayMillis()
            ReportPeriod.MONTHLY -> DateUtils.startOfMonthMillis() to DateUtils.endOfDayMillis()
            ReportPeriod.CUSTOM -> customRange
        }

        val periodBills = allBills.filter { it.bill.billDate in startMillis..endMillis }
        val periodPayments = allPayments.filter { it.payment.paymentDate in startMillis..endMillis }

        // Group sales by day for bar chart (up to 7 recent days in range)
        val dayBuckets = (6 downTo 0).map { offset ->
            val dayStart = DateUtils.startOfDaysAgoMillis(offset)
            val dayEnd = DateUtils.endOfDayMillis(dayStart)
            val daySales = allBills
                .filter { it.bill.billDate in dayStart..dayEnd }
                .sumOf { it.bill.finalAmountPaise }
            DailyChartBar(
                label = DateUtils.formatShortDate(dayStart),
                salesPaise = daySales
            )
        }

        val salesByCustomerId = periodBills.groupBy { it.bill.customerId }
        val customerRows = customers.map { c ->
            val cBills = salesByCustomerId[c.customer.id].orEmpty()
            CustomerSalesRow(
                customerId = c.customer.id,
                shopName = c.customer.shopName,
                billCount = cBills.size,
                totalSalesPaise = cBills.sumOf { it.bill.finalAmountPaise },
                outstandingPaise = c.outstandingPaise
            )
        }.sortedByDescending { it.totalSalesPaise.coerceAtLeast(it.outstandingPaise) }

        ReportsUiState(
            period = period,
            customStartMillis = customRange.first,
            customEndMillis = customRange.second,
            periodSalesPaise = periodBills.sumOf { it.bill.finalAmountPaise },
            periodReceivedPaise = periodPayments.sumOf { it.payment.amountPaise },
            totalOutstandingPaise = customers.sumOf { it.outstandingPaise },
            periodBillCount = periodBills.size,
            chartBars = dayBuckets,
            customerRows = customerRows,
            paymentHistory = periodPayments,
            currencySymbol = settings.currencySymbol.ifBlank { "₹" }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportsUiState()
    )

    fun setPeriod(period: ReportPeriod) {
        periodState.value = period
    }

    fun setCustomRange(startMillis: Long, endMillis: Long) {
        customRangeState.value = DateUtils.startOfDayMillis(startMillis) to DateUtils.endOfDayMillis(endMillis)
        periodState.value = ReportPeriod.CUSTOM
    }
}
