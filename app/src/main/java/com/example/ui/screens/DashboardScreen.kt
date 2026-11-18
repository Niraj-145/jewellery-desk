package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.PaymentMethod
import com.example.domain.DateUtils
import com.example.domain.FinancialMath
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.LuxuryCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.MutedGreen
import com.example.ui.theme.MutedRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onCreateNewBill: () -> Unit,
    onOpenInventory: () -> Unit = {},
    onAddCustomer: () -> Unit,
    onRecordPayment: () -> Unit,
    onViewBills: () -> Unit,
    onOpenBillDetail: (Long) -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    showTopSecondaryShortcuts: Boolean = true
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val symbol = state.settings.currencySymbol.ifBlank { "₹" }
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Brand Atelier Banner Header
        item {
            LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_atelier_banner_1794908166006),
                        contentDescription = "Jewellery Desk Wholesale Atelier",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xEB410E19),
                                        Color(0xC25B1726),
                                        Color(0x665B1726)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.settings.businessName.ifBlank { "Jewellery Desk" },
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Wholesale Billing & Ledger",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFEBD9B4)
                                )
                            }
                            if (showTopSecondaryShortcuts) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = onOpenReports,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.BarChart,
                                            contentDescription = "Reports",
                                            tint = Color.White
                                        )
                                    }
                                    IconButton(
                                        onClick = onOpenSettings,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Settings,
                                            contentDescription = "Settings",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onCreateNewBill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = Color(0xFF2B1D07)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("dashboard_new_bill_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create New Bill", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Date Filter Strip
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DashboardDateFilter.entries.forEach { filter ->
                    val label = if (filter == DashboardDateFilter.CUSTOM && state.selectedFilter == filter) {
                        DateUtils.formatShortDate(state.customDateMillis)
                    } else filter.label
                    FilterChip(
                        selected = state.selectedFilter == filter,
                        onClick = {
                            if (filter == DashboardDateFilter.CUSTOM) {
                                showDatePicker = true
                            } else {
                                viewModel.setDateFilter(filter)
                            }
                        },
                        leadingIcon = if (filter == DashboardDateFilter.CUSTOM) {
                            {
                                Icon(
                                    Icons.Outlined.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.height(40.dp)
                    )
                }
            }
        }

        // 5 Summary KPI Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val salesTitle = if (state.selectedFilter == DashboardDateFilter.TODAY) {
                    "Today's Sales"
                } else {
                    "Sales (${state.selectedFilter.label})"
                }
                val billsTitle = if (state.selectedFilter == DashboardDateFilter.TODAY) {
                    "Total Bills Today"
                } else {
                    "Bills (${state.selectedFilter.label})"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricCard(
                        label = salesTitle,
                        value = FinancialMath.formatInr(state.periodSalesPaise, symbol),
                        icon = Icons.Outlined.CurrencyRupee,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        label = billsTitle,
                        value = state.periodBillCount.toString(),
                        icon = Icons.Outlined.Receipt,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onClick = onViewBills,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricCard(
                        label = "Total Outstanding",
                        value = FinancialMath.formatInr(state.totalOutstandingPaise, symbol),
                        icon = Icons.Outlined.AccountBalanceWallet,
                        accentColor = if (state.totalOutstandingPaise > 0) MutedRed else MutedGreen,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        label = "Received Payments",
                        value = FinancialMath.formatInr(state.periodReceivedPaise, symbol),
                        icon = Icons.Outlined.Payments,
                        accentColor = MutedGreen,
                        onClick = onRecordPayment,
                        modifier = Modifier.weight(1f)
                    )
                }

                KpiMetricCard(
                    label = "Total Retailer Customers",
                    value = state.totalCustomers.toString(),
                    subtitle = "Active wholesale retailer accounts",
                    icon = Icons.Outlined.Storefront,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = onAddCustomer,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Quick Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "Quick Actions")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        label = "New Bill",
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        onClick = onCreateNewBill,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        label = "Add Customer",
                        icon = Icons.Outlined.PersonAdd,
                        onClick = onAddCustomer,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        label = "Record Payment",
                        icon = Icons.Outlined.Payments,
                        onClick = onRecordPayment,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        label = "View Bills",
                        icon = Icons.Outlined.Receipt,
                        onClick = onViewBills,
                        modifier = Modifier.weight(1f)
                    )
                }
                QuickActionTile(
                    label = "Jewellery Inventory (Scan & Stock)",
                    icon = Icons.Outlined.Inventory2,
                    onClick = onOpenInventory,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Recent Bills
        item {
            SectionHeader(
                title = "Recent Bills",
                actionLabel = if (state.recentBills.isNotEmpty()) "All Bills" else null,
                onAction = onViewBills
            )
        }

        if (state.recentBills.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "No bills generated yet",
                    message = "Create your first wholesale bill by scanning a jewellery QR code or entering item weight.",
                    actionLabel = "Create First Bill",
                    onAction = onCreateNewBill
                )
            }
        } else {
            items(state.recentBills, key = { "bill_${it.bill.id}" }) { billItem ->
                LuxuryCard(
                    onClick = { onOpenBillDetail(billItem.bill.id) },
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
                                text = billItem.customerShopName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${billItem.bill.billNumber} • ${DateUtils.formatDate(billItem.bill.billDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = FinancialMath.formatInr(billItem.bill.finalAmountPaise, symbol),
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            StatusPill(statusCode = billItem.bill.paymentStatus)
                        }
                    }
                }
            }
        }

        // Recent Payments
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader(
                title = "Recent Payments",
                actionLabel = if (state.recentPayments.isNotEmpty()) "All Payments" else null,
                onAction = onRecordPayment
            )
        }

        if (state.recentPayments.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Outlined.Payments,
                    title = "No payments recorded yet",
                    message = "Payments recorded during billing or against customer balances will appear here."
                )
            }
        } else {
            items(state.recentPayments, key = { "pay_${it.payment.id}" }) { pay ->
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
                                text = pay.customerShopName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${DateUtils.formatDate(pay.payment.paymentDate)} • ${PaymentMethod.fromCode(pay.payment.paymentMethod).label}" +
                                    (pay.billNumber?.let { " • $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "+${FinancialMath.formatInr(pay.payment.amountPaise, symbol)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            color = MutedGreen
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(52.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
