package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BillWithCustomer
import com.example.data.local.CustomerWithStats
import com.example.data.local.PaymentMethod
import com.example.data.local.PaymentWithDetails
import com.example.data.local.SettingsEntity
import com.example.data.repository.JewelleryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaymentsUiState(
    val totalReceivedPaise: Long = 0L,
    val totalOutstandingPaise: Long = 0L,
    val selectedMethodFilter: PaymentMethod? = null,
    val payments: List<PaymentWithDetails> = emptyList(),
    val customers: List<CustomerWithStats> = emptyList(),
    val openBills: List<BillWithCustomer> = emptyList(),
    val currencySymbol: String = "₹"
)

class PaymentsViewModel(
    private val repository: JewelleryRepository
) : ViewModel() {

    private val methodFilter = MutableStateFlow<PaymentMethod?>(null)

    val uiState: StateFlow<PaymentsUiState> = combine(
        methodFilter,
        repository.observeAllPayments(),
        repository.observeCustomersWithStats(),
        repository.observeAllBills(),
        repository.observeSettings()
    ) { filter, payments, customers, bills, settings ->
        val filteredPayments = if (filter == null) payments else {
            payments.filter { it.payment.paymentMethod == filter.code }
        }
        PaymentsUiState(
            totalReceivedPaise = payments.sumOf { it.payment.amountPaise },
            totalOutstandingPaise = customers.sumOf { it.outstandingPaise },
            selectedMethodFilter = filter,
            payments = filteredPayments,
            customers = customers,
            openBills = bills.filter { it.bill.remainingBalancePaise > 0L },
            currencySymbol = settings.currencySymbol.ifBlank { "₹" }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PaymentsUiState()
    )

    fun setMethodFilter(method: PaymentMethod?) {
        methodFilter.value = method
    }

    fun recordPayment(
        customerId: Long,
        billId: Long?,
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
}
