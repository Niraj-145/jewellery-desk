package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PaymentMethod
import com.example.domain.DateUtils
import com.example.domain.FinancialMath
import com.example.ui.components.CustomerFormDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.LuxuryCard
import com.example.ui.components.RecordPaymentDialog
import com.example.ui.components.StatusPill
import com.example.ui.theme.MutedGreen
import com.example.ui.theme.MutedRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: Long,
    viewModel: CustomersViewModel,
    onBack: () -> Unit,
    onCreateBillForCustomer: (Long) -> Unit,
    onOpenBillDetail: (Long) -> Unit
) {
    LaunchedEffect(customerId) {
        viewModel.loadCustomer(customerId)
    }

    val state by viewModel.detailUiState.collectAsStateWithLifecycle()
    val stats = state.customerStats
    val symbol = state.currencySymbol

    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }

    if (showEditDialog && stats != null) {
        CustomerFormDialog(
            existingCustomer = stats.customer,
            onDismiss = { showEditDialog = false },
            onSave = { shop, owner, phone, addr, gst, opening, notes ->
                viewModel.saveCustomer(stats.customer, shop, owner, phone, addr, gst, opening, notes)
                showEditDialog = false
            }
        )
    }

    if (showPaymentDialog && stats != null) {
        RecordPaymentDialog(
            customers = listOf(stats),
            openBills = state.bills.filter { it.bill.remainingBalancePaise > 0L },
            preselectedCustomerId = stats.customer.id,
            currencySymbol = symbol,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { cId, billId, amount, method, date, notes ->
                viewModel.recordCustomerPayment(cId, billId, amount, method, date, notes)
                showPaymentDialog = false
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stats?.customer?.shopName ?: "Customer Ledger",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (stats != null) {
                            Text(
                                text = "${stats.customer.displayCustomerId} • ${stats.customer.phone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (stats != null) {
                        IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit Customer")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (stats == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                EmptyStateCard(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    title = "Loading customer...",
                    message = "Fetching customer ledger and transaction history."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary Balance Cards
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiMetricCard(
                                label = "Opening Balance",
                                value = FinancialMath.formatInr(stats.customer.openingBalancePaise, symbol),
                                icon = Icons.Outlined.AccountBalanceWallet,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                label = "Current Outstanding",
                                value = FinancialMath.formatInr(stats.outstandingPaise, symbol),
                                icon = Icons.Outlined.AccountBalanceWallet,
                                accentColor = if (stats.outstandingPaise > 0) MutedRed else MutedGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiMetricCard(
                                label = "Bills Generated",
                                value = FinancialMath.formatInr(stats.totalBilledPaise, symbol),
                                subtitle = "${stats.billCount} bill(s)",
                                icon = Icons.Outlined.Receipt,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                label = "Payments Received",
                                value = FinancialMath.formatInr(stats.totalPaidPaise, symbol),
                                icon = Icons.Outlined.Payments,
                                accentColor = MutedGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Customer Info + Action Buttons
                item {
                    LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Owner: ${stats.customer.ownerName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (stats.customer.address.isNotBlank()) {
                                Text(
                                    text = stats.customer.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (stats.customer.gstNumber.isNotBlank()) {
                                Text(
                                    text = "GSTIN: ${stats.customer.gstNumber}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (stats.customer.notes.isNotBlank()) {
                                Text(
                                    text = "Notes: ${stats.customer.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onCreateBillForCustomer(customerId) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("New Bill")
                                }
                                OutlinedButton(
                                    onClick = { showPaymentDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Receive Payment")
                                }
                            }
                        }
                    }
                }

                // Ledger / Bills / Payments Tabs
                item {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Ledger (${state.ledger.size})") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Bills (${state.bills.size})") }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Payments (${state.payments.size})") }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        if (state.ledger.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Outlined.AccountBalanceWallet,
                                    title = "No ledger entries",
                                    message = "Bills (debits) and payments (credits) will appear chronologically here."
                                )
                            }
                        } else {
                            items(state.ledger, key = { it.id }) { entry ->
                                LuxuryCard(
                                    onClick = entry.linkedBillId?.let { bId -> { onOpenBillDetail(bId) } },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                entry.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                DateUtils.formatDate(entry.dateMillis),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            if (entry.debitPaise > 0L) {
                                                Text(
                                                    text = "Dr ${FinancialMath.formatInr(entry.debitPaise, symbol)}",
                                                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                                    color = MutedRed
                                                )
                                            }
                                            if (entry.creditPaise > 0L) {
                                                Text(
                                                    text = "Cr ${FinancialMath.formatInr(entry.creditPaise, symbol)}",
                                                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                                    color = MutedGreen
                                                )
                                            }
                                            Text(
                                                text = "Bal: ${FinancialMath.formatInr(entry.runningBalancePaise, symbol)}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (state.bills.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Outlined.Receipt,
                                    title = "No bills yet",
                                    message = "Create a bill for ${stats.customer.shopName} to track wholesale purchases."
                                )
                            }
                        } else {
                            items(state.bills, key = { it.bill.id }) { b ->
                                LuxuryCard(
                                    onClick = { onOpenBillDetail(b.bill.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(b.bill.billNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${DateUtils.formatDate(b.bill.billDate)} • ${b.itemCount} item(s)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                FinancialMath.formatInr(b.bill.finalAmountPaise, symbol),
                                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
                                            )
                                            StatusPill(statusCode = b.bill.paymentStatus)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        if (state.payments.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Outlined.Payments,
                                    title = "No payments recorded",
                                    message = "Tap Receive Payment above to record a payment from ${stats.customer.shopName}."
                                )
                            }
                        } else {
                            items(state.payments, key = { it.payment.id }) { p ->
                                LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                PaymentMethod.fromCode(p.payment.paymentMethod).label,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                DateUtils.formatDate(p.payment.paymentDate) +
                                                    (p.billNumber?.let { " • $it" } ?: " • On Account"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            "+${FinancialMath.formatInr(p.payment.amountPaise, symbol)}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                            color = MutedGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
