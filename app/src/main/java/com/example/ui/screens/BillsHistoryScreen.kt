package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.example.domain.DateUtils
import com.example.domain.FinancialMath
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LuxuryCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.MutedGreen
import com.example.ui.theme.MutedRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsHistoryScreen(
    viewModel: BillsViewModel,
    onOpenBillDetail: (Long) -> Unit,
    onCreateNewBill: () -> Unit
) {
    val state by viewModel.historyUiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.customDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { viewModel.setCustomDate(it) }
                        showDatePicker = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Bills History",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${state.bills.size} bill(s) matching filters",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Search bill number or retailer name...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bills_search_input")
            )
        }

        // Status filter chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BillStatusFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.statusFilter == filter,
                        onClick = { viewModel.setStatusFilter(filter) },
                        label = { Text(filter.label) },
                        modifier = Modifier.height(40.dp)
                    )
                }
            }
        }

        // Date filter chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BillDateFilter.entries.forEach { filter ->
                    val label = if (filter == BillDateFilter.CUSTOM && state.dateFilter == filter) {
                        DateUtils.formatShortDate(state.customDateMillis)
                    } else filter.label
                    FilterChip(
                        selected = state.dateFilter == filter,
                        onClick = {
                            if (filter == BillDateFilter.CUSTOM) showDatePicker = true
                            else viewModel.setDateFilter(filter)
                        },
                        leadingIcon = if (filter == BillDateFilter.CUSTOM) {
                            { Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text(label) },
                        modifier = Modifier.height(40.dp)
                    )
                }
            }
        }

        if (state.bills.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "No bills found",
                    message = if (state.searchQuery.isBlank() && state.statusFilter == BillStatusFilter.ALL) {
                        "Generated wholesale bills will appear here with instant PDF download and payment tracking."
                    } else {
                        "No bills match the current search or filter criteria."
                    },
                    actionLabel = if (state.searchQuery.isBlank() && state.statusFilter == BillStatusFilter.ALL) {
                        "Create New Bill"
                    } else null,
                    onAction = if (state.searchQuery.isBlank() && state.statusFilter == BillStatusFilter.ALL) {
                        onCreateNewBill
                    } else null
                )
            }
        } else {
            items(state.bills, key = { it.bill.id }) { item ->
                LuxuryCard(
                    onClick = { onOpenBillDetail(item.bill.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.customerShopName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.bill.billNumber} • ${DateUtils.formatDate(item.bill.billDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(statusCode = item.bill.paymentStatus)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.itemCount} item(s) • ${FinancialMath.formatWeightGrams(item.bill.totalWeightUg)} • ${FinancialMath.formatPercentage(item.bill.percentageBps)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = FinancialMath.formatInr(item.bill.finalAmountPaise, state.currencySymbol),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.Bold
                                )
                                if (item.bill.remainingBalancePaise > 0L) {
                                    Text(
                                        text = "Due: ${FinancialMath.formatInr(item.bill.remainingBalancePaise, state.currencySymbol)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        color = MutedRed
                                    )
                                } else {
                                    Text(
                                        text = "Paid in full",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
