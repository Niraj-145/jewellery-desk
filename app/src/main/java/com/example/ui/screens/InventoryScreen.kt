package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.InventoryStatus
import com.example.domain.DateUtils
import com.example.domain.FinancialMath
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.LuxuryCard
import com.example.ui.components.QrScannerModal
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.BurgundyContainer
import com.example.ui.theme.DeepBurgundy
import com.example.ui.theme.GreenContainer
import com.example.ui.theme.MutedGreen
import com.example.ui.theme.WarmAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    onOpenInventoryDetail: (Long) -> Unit
) {
    val state by viewModel.listUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showQrScanner by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.lastSavedItemId) {
        if (state.lastSavedItemId != null) {
            Toast.makeText(context, "Saved to inventory.", Toast.LENGTH_SHORT).show()
            viewModel.consumeLastSavedItem()
        }
    }

    if (showQrScanner) {
        QrScannerModal(
            onQrDetected = { raw ->
                showQrScanner = false
                viewModel.onQrScanned(raw)
            },
            onDismiss = { showQrScanner = false }
        )
    }

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

    state.duplicatePrompt?.let { dup ->
        AlertDialog(
            onDismissRequest = viewModel::dismissScanPrompts,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = "Duplicate Item",
                    tint = WarmAmber
                )
            },
            title = { Text("Duplicate QR Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = dup.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = dup.existingItem.itemName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = buildString {
                                    append(dup.existingItem.displayInventoryId)
                                    dup.existingItem.productRefId?.let { append(" • Ref #$it") }
                                    append(" • ${FinancialMath.formatWeightGrams(dup.existingItem.netWeightUg)}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Status: ${InventoryStatus.fromCode(dup.existingItem.status).label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = dup.existingItem.id
                        viewModel.dismissScanPrompts()
                        onOpenInventoryDetail(id)
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("duplicate_view_existing_button")
                ) {
                    Text("View Existing Item")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = viewModel::dismissScanPrompts,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    state.verifyPrompt?.let { prompt ->
        InventoryVerificationDialog(
            prompt = prompt,
            isSaving = state.isSaving,
            onDismiss = viewModel::dismissScanPrompts,
            onSave = { name, category, weightText, otherDetails, notes ->
                viewModel.saveScannedInventoryItem(
                    productUrl = prompt.productUrl,
                    productRefId = prompt.productRefId,
                    itemName = name,
                    category = category,
                    weightText = weightText,
                    otherDetails = otherDetails,
                    imageUrl = prompt.imageUrl,
                    notes = notes
                )
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Scan New Item CTA
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Jewellery Inventory",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Scan QR tags to save stock & link directly to bills",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showQrScanner = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("inventory_scan_new_item_button")
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan New Item")
                }
            }
        }

        if (state.isFetchingProduct) {
            item {
                LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(
                            text = "Fetching product details from Sawariya Seth...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        state.scanErrorBanner?.let { banner ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.WarningAmber,
                            contentDescription = "Scan Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = banner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::dismissScanPrompts) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        // Inventory Dashboard KPIs
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricCard(
                        label = "Total Items",
                        value = state.totalItems.toString(),
                        icon = Icons.Outlined.Inventory2,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        label = "Available Items",
                        value = state.availableItems.toString(),
                        icon = Icons.Outlined.CheckCircle,
                        accentColor = MutedGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricCard(
                        label = "Sold Items",
                        value = state.soldItems.toString(),
                        icon = Icons.Outlined.Sell,
                        accentColor = DeepBurgundy,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        label = "Available Net Wt",
                        value = FinancialMath.formatWeightGrams(state.totalAvailableWeightUg),
                        icon = Icons.Outlined.Scale,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Search
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Search by item name, ref ID, or QR URL...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inventory_search_input")
            )
        }

        // Status & Date Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InventoryStatusFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.statusFilter == filter,
                            onClick = { viewModel.setStatusFilter(filter) },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("inventory_status_filter_${filter.name.lowercase()}")
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InventoryDateFilter.entries.forEach { filter ->
                        val label = if (filter == InventoryDateFilter.CUSTOM && state.dateFilter == filter) {
                            DateUtils.formatShortDate(state.customDateMillis)
                        } else filter.label
                        FilterChip(
                            selected = state.dateFilter == filter,
                            onClick = {
                                if (filter == InventoryDateFilter.CUSTOM) {
                                    showDatePicker = true
                                } else {
                                    viewModel.setDateFilter(filter)
                                }
                            },
                            leadingIcon = if (filter == InventoryDateFilter.CUSTOM) {
                                {
                                    Icon(
                                        Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            label = { Text(label) },
                            modifier = Modifier.height(38.dp)
                        )
                    }
                }
            }
        }

        // Inventory List
        if (state.items.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Outlined.Inventory2,
                    title = if (state.totalItems == 0) "No inventory items scanned yet" else "No matching items",
                    message = if (state.totalItems == 0) {
                        "Tap Scan New Item to scan a Sawariya Seth QR tag and save it permanently to your stock."
                    } else {
                        "Try clearing your search query or status/date filters."
                    },
                    actionLabel = if (state.totalItems == 0) "Scan First Item" else null,
                    onAction = if (state.totalItems == 0) ({ showQrScanner = true }) else null
                )
            }
        } else {
            items(state.items, key = { it.item.id }) { entry ->
                val item = entry.item
                val isReserved = state.reservedItemIds.contains(item.id)
                LuxuryCard(
                    onClick = { onOpenInventoryDetail(item.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inventory_item_card_${item.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = item.itemName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    if (!item.productRefId.isNullOrBlank()) {
                                        append("Ref #${item.productRefId} • ")
                                    }
                                    append(item.displayInventoryId)
                                    if (!item.category.isNullOrBlank()) {
                                        append(" • ${item.category}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Added ${DateUtils.formatDate(item.dateAdded)}" +
                                    if (!entry.linkedBillNumber.isNullOrBlank()) " • Bill ${entry.linkedBillNumber}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = FinancialMath.formatWeightGrams(item.netWeightUg),
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            InventoryStatusPill(statusCode = item.status, isReservedInDraft = isReserved)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(14.dp)) }
    }
}

@Composable
fun InventoryStatusPill(
    statusCode: String,
    isReservedInDraft: Boolean = false,
    modifier: Modifier = Modifier
) {
    val status = InventoryStatus.fromCode(statusCode)
    val (label, bg, fg) = when {
        status == InventoryStatus.SOLD -> Triple("Sold", BurgundyContainer, DeepBurgundy)
        isReservedInDraft -> Triple("In Draft", AmberContainer, WarmAmber)
        else -> Triple("Available", GreenContainer, MutedGreen)
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun InventoryVerificationDialog(
    prompt: InventoryVerifyPrompt,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        category: String,
        weightText: String,
        otherDetails: String,
        notes: String
    ) -> Boolean
) {
    var name by remember(prompt) { mutableStateOf(prompt.itemName) }
    var category by remember(prompt) { mutableStateOf(prompt.category.orEmpty()) }
    var weight by remember(prompt) { mutableStateOf(prompt.weightText) }
    var otherDetails by remember(prompt) { mutableStateOf(prompt.otherDetails) }
    var notes by remember(prompt) { mutableStateOf(prompt.notes) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify & Save to Inventory") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!prompt.infoMessage.isNullOrBlank()) {
                    Surface(
                        color = AmberContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = prompt.infoMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5D3E0A),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "QR / Product URL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = prompt.productUrl,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!prompt.productRefId.isNullOrBlank()) {
                            Text(
                                text = "Product Reference ID: #${prompt.productRefId}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text("Item Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verify_inventory_name_input")
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it; errorMsg = null },
                    label = { Text("Net Weight (grams) *") },
                    placeholder = { Text("e.g., 0.434") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verify_inventory_weight_input")
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (if available)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = otherDetails,
                    onValueChange = { otherDetails = it },
                    label = { Text("Other Product Details") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ok = onSave(name, category, weight, otherDetails, notes)
                    if (!ok) {
                        errorMsg = "Enter a valid Item Name and Net Weight (> 0 g)."
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("save_to_inventory_button")
            ) {
                Text("Save to Inventory")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cancel")
            }
        }
    )
}
