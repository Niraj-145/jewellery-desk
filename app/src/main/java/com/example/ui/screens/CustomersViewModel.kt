package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BillWithCustomer
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerWithStats
import com.example.data.local.LedgerEntry
import com.example.data.local.PaymentMethod
import com.example.data.local.PaymentWithDetails
import com.example.data.local.SettingsEntity
import com.example.data.repository.JewelleryRepository
import com.example.domain.FinancialMath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomersListUiState(
    val searchQuery: String = "",
    val customers: List<CustomerWithStats> = emptyList(),
    val totalOutstandingPaise: Long = 0L,
    val currencySymbol: String = "₹"
)

data class CustomerDetailUiState(
    val customerStats: CustomerWithStats? = null,
    val bills: List<BillWithCustomer> = emptyList(),
    val payments: List<PaymentWithDetails> = emptyList(),
    val ledger: List<LedgerEntry> = emptyList(),
    val currencySymbol: String = "₹"
)

class CustomersViewModel(
    private val repository: JewelleryRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val activeCustomerId = MutableStateFlow<Long?>(null)

    val listUiState: StateFlow<CustomersListUiState> = combine(
        searchQuery,
        repository.observeCustomersWithStats(),
        repository.observeSettings()
    ) { query, list, settings ->
        val filtered = if (query.isBlank()) list else {
            val q = query.trim().lowercase()
            list.filter {
                it.customer.shopName.lowercase().contains(q) ||
                    it.customer.ownerName.lowercase().contains(q) ||
                    it.customer.phone.lowercase().contains(q) ||
                    it.customer.displayCustomerId.lowercase().contains(q)
            }
        }
        CustomersListUiState(
            searchQuery = query,
            customers = filtered,
            totalOutstandingPaise = list.sumOf { it.outstandingPaise },
            currencySymbol = settings.currencySymbol.ifBlank { "₹" }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CustomersListUiState()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val detailUiState: StateFlow<CustomerDetailUiState> = activeCustomerId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(CustomerDetailUiState())
            } else {
                combine(
                    repository.observeCustomerWithStats(id),
                    repository.observeBillsForCustomer(id),
                    repository.observePaymentsForCustomer(id),
                    repository.observeCustomerLedger(id),
                    repository.observeSettings()
                ) { stats, bills, payments, ledger, settings ->
                    CustomerDetailUiState(
                        customerStats = stats,
                        bills = bills,
                        payments = payments,
                        ledger = ledger,
                        currencySymbol = settings.currencySymbol.ifBlank { "₹" }
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CustomerDetailUiState()
        )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun loadCustomer(customerId: Long) {
        activeCustomerId.value = customerId
    }

    fun saveCustomer(
        existing: CustomerEntity?,
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
            val entity = CustomerEntity(
                id = existing?.id ?: 0L,
                shopName = shopName.trim(),
                ownerName = ownerName.trim(),
                phone = phone.trim(),
                address = address.trim(),
                gstNumber = gstNumber.trim(),
                openingBalancePaise = openingPaise,
                notes = notes.trim(),
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            repository.saveCustomer(entity)
        }
    }

    fun recordCustomerPayment(
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
