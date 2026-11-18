package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.ui.components.SectionHeader
import com.example.ui.theme.MutedGreen
import com.example.ui.theme.MutedRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onOpenCustomer: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val symbol = state.currencySymbol
    var showRangePicker by remember { mutableStateOf(false) }

    if (showRangePicker) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.customStartMillis,
            initialSelectedEndDateMillis = state.customEndMillis
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = rangeState.selectedStartDateMillis
                        val e = rangeState.selectedEndDateMillis ?: s
                        if (s != null && e != null) {
                            viewModel.setCustomRange(s, e)
                        }
                        showRangePicker = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.height(420.dp))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Business Reports",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Sales, collections, and retailer-wise outstanding summary.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Period Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportPeriod.entries.forEach { period ->
                    val label = if (period == ReportPeriod.CUSTOM && state.period == period) {
                        "${DateUtils.formatShortDate(state.customStartMillis)} – ${DateUtils.formatShortDate(state.customEndMillis)}"
                    } else period.label
                    FilterChip(
                        selected = state.period == period,
                        onClick = {
                            if (period == ReportPeriod.CUSTOM) showRangePicker = true
                            else viewModel.setPeriod(period)
                        },
                        label = { Text(label) },
                        modifier = Modifier.height(40.dp)
                    )
                }
            }
        }

        // Summary KPIs
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricCard(
                        label = "Sales (${state.period.label})",
                        value = FinancialMath.formatInr(state.periodSalesPaise, symbol),
                        subtitle = "${state.periodBillCount} bill(s)",
                        icon = Icons.Outlined.CurrencyRupee,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        label = "Amount Received",
                        value = FinancialMath.formatInr(state.periodReceivedPaise, symbol),
                        icon = Icons.Outlined.Payments,
                        accentColor = MutedGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
                KpiMetricCard(
                    label = "Total Outstanding Across Retailers",
                    value = FinancialMath.formatInr(state.totalOutstandingPaise, symbol),
                    icon = Icons.Outlined.AccountBalanceWallet,
                    accentColor = if (state.totalOutstandingPaise > 0) MutedRed else MutedGreen,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 7-Day Sales Chart
        item {
            LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Last 7 Days Sales Trend",
                        style = MaterialTheme.typography.titleMedium
                    )
                    val maxPaise = state.chartBars.maxOfOrNull { it.salesPaise } ?: 0L
                    if (maxPaise <= 0L) {
                        Text(
                            text = "No sales recorded in the past 7 days.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    } else {
                        val barPrimary = MaterialTheme.colorScheme.primary
                        val barGold = MaterialTheme.colorScheme.secondary
                        val trackColor = MaterialTheme.colorScheme.outlineVariant
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        ) {
                            val count = state.chartBars.size.coerceAtLeast(1)
                            val slotWidth = size.width / count
                            val barWidth = (slotWidth * 0.48f).coerceAtMost(32.dp.toPx())
                            state.chartBars.forEachIndexed { idx, bar ->
                                val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                                drawRoundRect(
                                    color = trackColor,
                                    topLeft = Offset(x, 0f),
                                    size = Size(barWidth, size.height),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                )
                                if (bar.salesPaise > 0L) {
                                    val ratio = (bar.salesPaise.toFloat() / maxPaise.toFloat()).coerceIn(0.06f, 1f)
                                    val barHeight = size.height * ratio
                                    drawRoundRect(
                                        color = if (idx == count - 1) barGold else barPrimary,
                                        topLeft = Offset(x, size.height - barHeight),
                                        size = Size(barWidth, barHeight),
                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                    )
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            state.chartBars.forEach { bar ->
                                Text(
                                    text = bar.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Customer-wise Sales & Outstanding Table
        item {
            SectionHeader(title = "Customer-wise Sales & Outstanding")
        }

        if (state.customerRows.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Outlined.BarChart,
                    title = "No customer data yet",
                    message = "Customer sales and outstanding breakdowns will appear once retailers are added."
                )
            }
        } else {
            items(state.customerRows, key = { it.customerId }) { row ->
                LuxuryCard(
                    onClick = { onOpenCustomer(row.customerId) },
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
                                text = row.shopName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Period Sales: ${FinancialMath.formatInr(row.totalSalesPaise, symbol)} (${row.billCount} bills)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = FinancialMath.formatInr(row.outstandingPaise, symbol),
                                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = if (row.outstandingPaise > 0) MutedRed else MutedGreen
                            )
                            Text(
                                text = "Outstanding",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Payment History Summary
        item {
            SectionHeader(title = "Payments in Period (${state.paymentHistory.size})")
        }
        if (state.paymentHistory.isNotEmpty()) {
            item {
                LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        state.paymentHistory.take(10).forEachIndexed { idx, pay ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pay.customerShopName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${DateUtils.formatShortDate(pay.payment.paymentDate)} • ${PaymentMethod.fromCode(pay.payment.paymentMethod).label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    FinancialMath.formatInr(pay.payment.amountPaise, symbol),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MutedGreen
                                )
                            }
                            if (idx < state.paymentHistory.take(10).lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
