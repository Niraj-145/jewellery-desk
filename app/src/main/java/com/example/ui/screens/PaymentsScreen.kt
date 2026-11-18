package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PaymentMethod
import com.example.domain.DateUtils
import com.example.domain.FinancialMath
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.LuxuryCard
import com.example.ui.components.RecordPaymentDialog
import com.example.ui.theme.MutedGreen
import com.example.ui.theme.MutedRed

@Composable
fun PaymentsScreen(
    viewModel: PaymentsViewModel,
    onOpenCustomer: (Long) -> Unit,
    onOpenBill: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val symbol = state.currencySymbol
    var showRecordDialog by remember { mutableStateOf(false) }

    if (showRecordDialog) {
        RecordPaymentDialog(
            customers = state.customers,
            openBills = state.openBills,
            currencySymbol = symbol,
            onDismiss = { showRecordDialog = false },
            onConfirm = { cId, bId, amount, method, date, notes ->
                viewModel.recordPayment(cId, bId, amount, method, date, notes)
                showRecordDialog = false
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.customers.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showRecordDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("record_payment_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Record Payment")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Payments & Collections",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricCard(
                        label = "Total Received",
                        value = FinancialMath.formatInr(state.totalReceivedPaise, symbol),
                        icon = Icons.Outlined.Payments,
                        accentColor = MutedGreen,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        label = "Outstanding",
                        value = FinancialMath.formatInr(state.totalOutstandingPaise, symbol),
                        icon = Icons.Outlined.AccountBalanceWallet,
                        accentColor = if (state.totalOutstandingPaise > 0) MutedRed else MutedGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.selectedMethodFilter == null,
                        onClick = { viewModel.setMethodFilter(null) },
                        label = { Text("All Methods") },
                        modifier = Modifier.height(40.dp)
                    )
                    PaymentMethod.entries.forEach { method ->
                        FilterChip(
                            selected = state.selectedMethodFilter == method,
                            onClick = { viewModel.setMethodFilter(method) },
                            label = { Text(method.label) },
                            modifier = Modifier.height(40.dp)
                        )
                    }
                }
            }

            if (state.payments.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Outlined.Payments,
                        title = "No payment transactions",
                        message = if (state.customers.isEmpty()) {
                            "Add a retailer customer first, then record payments against bills or balances."
                        } else {
                            "Tap + to record a payment from a retailer customer."
                        },
                        actionLabel = if (state.customers.isNotEmpty()) "Record Payment" else null,
                        onAction = if (state.customers.isNotEmpty()) {
                            { showRecordDialog = true }
                        } else null
                    )
                }
            } else {
                items(state.payments, key = { it.payment.id }) { p ->
                    LuxuryCard(
                        onClick = {
                            val bId = p.payment.billId
                            if (bId != null) onOpenBill(bId) else onOpenCustomer(p.payment.customerId)
                        },
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
                                Text(
                                    text = p.customerShopName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${DateUtils.formatDate(p.payment.paymentDate)} • ${PaymentMethod.fromCode(p.payment.paymentMethod).label}" +
                                        (p.billNumber?.let { " • $it" } ?: " • On Account"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (p.payment.notes.isNotBlank()) {
                                    Text(
                                        text = p.payment.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = "+${FinancialMath.formatInr(p.payment.amountPaise, symbol)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = MutedGreen
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}
