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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.data.local.CustomerEntity
import com.example.domain.FinancialMath
import com.example.ui.components.CustomerFormDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LuxuryCard
import com.example.ui.theme.MutedGreen
import com.example.ui.theme.MutedRed

@Composable
fun CustomersScreen(
    viewModel: CustomersViewModel,
    onOpenCustomerDetail: (Long) -> Unit
) {
    val state by viewModel.listUiState.collectAsStateWithLifecycle()
    var showFormFor by remember { mutableStateOf<Pair<Boolean, CustomerEntity?>>(false to null) }

    if (showFormFor.first) {
        CustomerFormDialog(
            existingCustomer = showFormFor.second,
            onDismiss = { showFormFor = false to null },
            onSave = { shop, owner, phone, addr, gst, opening, notes ->
                viewModel.saveCustomer(showFormFor.second, shop, owner, phone, addr, gst, opening, notes)
                showFormFor = false to null
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showFormFor = true to null },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_customer_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Customer")
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
                Column {
                    Text(
                        text = "Retailer Customers",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${state.customers.size} accounts • Total Outstanding: ${FinancialMath.formatInr(state.totalOutstandingPaise, state.currencySymbol)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Search shop name, owner, mobile, or ID...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customers_search_input")
                )
            }

            if (state.customers.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Outlined.Storefront,
                        title = if (state.searchQuery.isBlank()) "No retailer customers yet" else "No matching retailers",
                        message = if (state.searchQuery.isBlank()) {
                            "Add your regular jewellery retailers to start generating bills and tracking ledgers."
                        } else {
                            "Try searching by another shop name, owner, or mobile number."
                        },
                        actionLabel = if (state.searchQuery.isBlank()) "Add First Customer" else null,
                        onAction = if (state.searchQuery.isBlank()) {
                            { showFormFor = true to null }
                        } else null
                    )
                }
            } else {
                items(state.customers, key = { it.customer.id }) { item ->
                    LuxuryCard(
                        onClick = { onOpenCustomerDetail(item.customer.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = item.customer.shopName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${item.customer.displayCustomerId} • ${item.customer.ownerName} • ${item.customer.phone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.billCount} bill(s) • Billed ${FinancialMath.formatInr(item.totalBilledPaise, state.currencySymbol)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = FinancialMath.formatInr(item.outstandingPaise, state.currencySymbol),
                                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.outstandingPaise > 0L) MutedRed else MutedGreen
                                    )
                                    Text(
                                        text = if (item.outstandingPaise > 0L) "Due" else "Settled",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { showFormFor = true to item.customer },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit ${item.customer.shopName}")
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}
