package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.InventoryItemEntity
import com.example.data.local.InventoryStatus
import com.example.data.local.PaymentMethod
import com.example.domain.DateUtils
import com.example.domain.FinancialMath
import com.example.ui.components.CustomerFormDialog
import com.example.ui.components.LuxuryCard
import com.example.ui.components.QrScannerModal
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.GreenContainer
import com.example.ui.theme.MutedGreen
import com.example.ui.theme.MutedRed
import com.example.ui.theme.WarmAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewBillScreen(
    viewModel: NewBillViewModel,
    preselectedCustomerId: Long? = null,
    onBillSaved: (Long) -> Unit,
    onOpenInventoryItem: (Long) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val symbol = state.currencySymbol

    var showCustomerPicker by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showInventoryPicker by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showMethodMenu by remember { mutableStateOf(false) }

    LaunchedEffect(preselectedCustomerId) {
        if (preselectedCustomerId != null) {
            viewModel.preselectCustomer(preselectedCustomerId)
        }
    }

    LaunchedEffect(state.savedBillId) {
        val id = state.savedBillId
        if (id != null) {
            viewModel.resetAfterNavigation()
            onBillSaved(id)
        }
    }

    if (showQrScanner) {
        QrScannerModal(
            onQrDetected = { raw ->
                showQrScanner = false
                viewModel.onQrScannedOrUrlEntered(raw)
            },
            onDismiss = { showQrScanner = false }
        )
    }

    if (showAddCustomerDialog) {
        CustomerFormDialog(
            onDismiss = { showAddCustomerDialog = false },
            onSave = { shopName, ownerName, phone, address, gst, opening, notes ->
                viewModel.createAndSelectCustomer(shopName, ownerName, phone, address, gst, opening, notes)
                showAddCustomerDialog = false
            }
        )
    }

    val manualPrompt = state.manualEntryPrompt
    if (manualPrompt != null) {
        ManualItemEntryDialog(
            prompt = manualPrompt,
            onDismiss = { viewModel.dismissManualEntry() },
            onSave = { name, url, weight, notes ->
                viewModel.saveManualItem(name, url, weight, notes)
            }
        )
    }

    state.duplicateInventoryPrompt?.let { dup ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicatePrompt,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = "Saved in Inventory",
                    tint = WarmAmber
                )
            },
            title = { Text("Item Already in Inventory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dup.message, style = MaterialTheme.typography.bodyMedium)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = dup.existingItem.itemName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${dup.existingItem.displayInventoryId} • ${FinancialMath.formatWeightGrams(dup.existingItem.netWeightUg)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val id = dup.existingItem.id
                            viewModel.dismissDuplicatePrompt()
                            onOpenInventoryItem(id)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("View Existing Item")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.addExistingDuplicateToBill(dup.existingItem) },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("add_existing_inventory_to_bill_button")
                ) {
                    Text("Add to Bill")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissDuplicatePrompt,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    state.scannedVerifyPrompt?.let { scanned ->
        NewBillScannedVerifyDialog(
            prompt = scanned,
            onDismiss = viewModel::dismissScannedPrompt,
            onSaveToInventoryAndAdd = { name, category, weightText, otherDetails, notes ->
                viewModel.saveScannedToInventoryAndAddToBill(
                    productUrl = scanned.productUrl,
                    productRefId = scanned.productRefId,
                    itemName = name,
                    category = category,
                    netWeightText = weightText,
                    otherDetails = otherDetails,
                    imageUrl = scanned.imageUrl,
                    notes = notes
                )
            },
            onAddWithoutSaving = { name, weightText, notes ->
                viewModel.addScannedWithoutSavingToInventory(
                    productUrl = scanned.productUrl,
                    productRefId = scanned.productRefId,
                    itemName = name,
                    netWeightText = weightText,
                    imageUrl = scanned.imageUrl,
                    notes = notes
                )
            }
        )
    }

    if (showInventoryPicker) {
        InventoryPickerDialog(
            availableItems = state.availableInventory,
            onDismiss = { showInventoryPicker = false },
            onAddSelected = { selected ->
                viewModel.addInventoryItemsToBill(selected)
                showInventoryPicker = false
            }
        )
    }

    if (showCustomerPicker) {
        CustomerSelectorDialog(
            customers = state.customers,
            query = state.customerSearchQuery,
            currencySymbol = symbol,
            onQueryChange = viewModel::updateCustomerSearch,
            onSelect = {
                viewModel.selectCustomer(it)
                showCustomerPicker = false
            },
            onAddNew = {
                showCustomerPicker = false
                showAddCustomerDialog = true
            },
            onDismiss = { showCustomerPicker = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "New Wholesale Bill",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Pick from inventory or scan QR codes, apply rate & %, and finalize.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.items.isNotEmpty() || state.selectedCustomer != null || state.billNotes.isNotBlank()) {
                    TextButton(
                        onClick = viewModel::cancelDraft,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("cancel_draft_button")
                    ) {
                        Text("Cancel Draft", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // STEP 1: SELECT CUSTOMER
        item {
            LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StepTitleRow(stepNumber = "1", title = "Select Retailer Customer")
                    val selected = state.selectedCustomer
                    if (selected != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selected.customer.shopName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${selected.customer.ownerName} • ${selected.customer.phone}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Outstanding: ${FinancialMath.formatInr(selected.outstandingPaise, symbol)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected.outstandingPaise > 0) MutedRed else MutedGreen
                                    )
                                }
                                TextButton(
                                    onClick = { showCustomerPicker = true },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Change")
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showCustomerPicker = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("select_customer_button")
                            ) {
                                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select Retailer")
                            }
                            OutlinedButton(
                                onClick = { showAddCustomerDialog = true },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("bill_add_customer_button")
                            ) {
                                Icon(Icons.Outlined.PersonAdd, contentDescription = "New Customer", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New")
                            }
                        }
                    }
                }
            }
        }

        // STEP 2 & 3: SCAN QR & ITEMS LIST
        item {
            LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StepTitleRow(
                        stepNumber = "2",
                        title = if (state.items.isEmpty()) "Add Jewellery Items" else "Jewellery Items (${state.items.size})"
                    )

                    // Option A: Select from Inventory
                    Button(
                        onClick = { showInventoryPicker = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = Color(0xFF2B1D07)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("select_from_inventory_button")
                    ) {
                        Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Select from Inventory (${state.availableInventory.size} Available)",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Option B: Scan New Item OR Manual Entry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showQrScanner = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp)
                                .testTag("scan_qr_button")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan QR Item", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedButton(
                            onClick = { viewModel.openManualEntry() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("manual_item_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manual Entry", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    if (state.isFetchingProduct) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Text(
                                text = "Retrieving product weight from sawariyaseth.cc...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    state.qrErrorBanner?.let { banner ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.WarningAmber,
                                    contentDescription = "Notice",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = banner,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (state.items.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Items: ${state.items.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Total Weight: ${FinancialMath.formatWeightGrams(state.totalWeightUg)}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Scanned / Added Items List
        itemsIndexed(state.items, key = { _, item -> item.localId }) { index, item ->
            val itemAmountPaise = FinancialMath.calculateItemAmountPaise(
                netWeightUg = item.netWeightUg,
                ratePerGramPaise = state.ratePerGramPaise,
                percentageBps = state.percentageBps
            )
            LuxuryCard(modifier = Modifier.fillMaxWidth()) {
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
                                text = "${index + 1}. ${item.itemName}",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (item.inventoryItemId != null) {
                                Surface(
                                    color = GreenContainer,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "In Inventory",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Net Weight: ${FinancialMath.formatWeightGrams(item.netWeightUg)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!item.productUrl.isNullOrBlank()) {
                            Text(
                                text = item.productUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (itemAmountPaise > 0L) {
                            Text(
                                text = "Calculated: ${FinancialMath.formatInr(itemAmountPaise, symbol)}",
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Row {
                        IconButton(
                            onClick = { viewModel.openManualEntry(item) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit item weight")
                        }
                        IconButton(
                            onClick = { viewModel.removeItem(item.localId) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Remove item",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // STEP 4: RATE, PERCENTAGE & BILL CALCULATION
        item {
            LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StepTitleRow(stepNumber = "3", title = "Rate, Percentage & Calculation")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = state.rateInput,
                            onValueChange = viewModel::updateRate,
                            label = { Text("Today's Rate ($symbol/g)") },
                            placeholder = { Text("14000") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("rate_per_gram_input")
                        )
                        OutlinedTextField(
                            value = state.percentageInput,
                            onValueChange = viewModel::updatePercentage,
                            label = { Text("Percentage (%)") },
                            placeholder = { Text("72") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("percentage_input")
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Net Weight", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    FinancialMath.formatWeightGrams(state.totalWeightUg),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Calculated Amount", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    FinancialMath.formatInr(state.calculatedAmountPaise, symbol),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Formula: Net Weight × Rate × Percentage / 100",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // STEP 5: FINAL BILL AMOUNT (STORED SEPARATELY)
                    OutlinedTextField(
                        value = state.finalAmountInput,
                        onValueChange = viewModel::updateFinalAmount,
                        label = { Text("Final Bill Amount ($symbol)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        trailingIcon = {
                            if (state.finalAmountManuallyEdited) {
                                TextButton(onClick = viewModel::resetFinalAmountToCalculated) {
                                    Text("Reset")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("final_amount_input")
                    )
                    if (state.finalAmountManuallyEdited && state.calculatedAmountPaise != state.finalAmountPaise) {
                        Text(
                            text = "Calculated: ${FinancialMath.formatInr(state.calculatedAmountPaise, symbol)}  •  Adjusted Final: ${FinancialMath.formatInr(state.finalAmountPaise, symbol)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // STEP 6: PAYMENT & SAVE
        item {
            LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StepTitleRow(stepNumber = "4", title = "Payment & Finalize Bill")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaymentOption.entries.forEach { option ->
                            FilterChip(
                                selected = state.paymentOption == option,
                                onClick = { viewModel.updatePaymentOption(option) },
                                label = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            )
                        }
                    }

                    if (state.paymentOption == PaymentOption.PARTIALLY_PAID) {
                        OutlinedTextField(
                            value = state.amountReceivedInput,
                            onValueChange = viewModel::updateAmountReceived,
                            label = { Text("Amount Received ($symbol)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bill_amount_received_input")
                        )
                    }

                    if (state.paymentOption != PaymentOption.UNPAID) {
                        ExposedDropdownMenuBox(
                            expanded = showMethodMenu,
                            onExpandedChange = { showMethodMenu = it }
                        ) {
                            OutlinedTextField(
                                value = state.paymentMethod.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Payment Method") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMethodMenu) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = showMethodMenu,
                                onDismissRequest = { showMethodMenu = false }
                            ) {
                                PaymentMethod.entries.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.label) },
                                        onClick = {
                                            viewModel.updatePaymentMethod(m)
                                            showMethodMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.paymentNotes,
                            onValueChange = viewModel::updatePaymentNotes,
                            label = { Text("Payment Notes (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = state.billNotes,
                        onValueChange = viewModel::updateBillNotes,
                        label = { Text("Bill Notes (Optional)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Amount Received", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            FinancialMath.formatInr(state.amountReceivedPaise, symbol),
                            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                            color = MutedGreen
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Remaining Balance", style = MaterialTheme.typography.titleSmall)
                        Text(
                            FinancialMath.formatInr(state.remainingBalancePaise, symbol),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = if (state.remainingBalancePaise > 0) MutedRed else MutedGreen
                        )
                    }

                    state.validationError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = viewModel::saveBill,
                        enabled = !state.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_bill_button")
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate & Save Bill", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(18.dp)) }
    }
}

@Composable
private fun StepTitleRow(stepNumber: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CustomerSelectorDialog(
    customers: List<com.example.data.local.CustomerWithStats>,
    query: String,
    currencySymbol: String,
    onQueryChange: (String) -> Unit,
    onSelect: (com.example.data.local.CustomerWithStats) -> Unit,
    onAddNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val filtered = remember(customers, query) {
        if (query.isBlank()) customers
        else {
            val q = query.trim().lowercase()
            customers.filter {
                it.customer.shopName.lowercase().contains(q) ||
                    it.customer.ownerName.lowercase().contains(q) ||
                    it.customer.phone.lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Retailer") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search shop, owner, mobile...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = "No matching retailer found. Add a new customer below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filtered, key = { _, c -> c.customer.id }) { _, c ->
                            LuxuryCard(
                                onClick = { onSelect(c) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(c) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(c.customer.shopName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${c.customer.ownerName} • ${c.customer.phone}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = FinancialMath.formatInr(c.outstandingPaise, currencySymbol),
                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                        color = if (c.outstandingPaise > 0) MutedRed else MutedGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAddNew, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Customer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ManualItemEntryDialog(
    prompt: ManualEntryPrompt,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, weight: String, notes: String) -> Boolean
) {
    var name by remember(prompt) { mutableStateOf(prompt.prefilledName) }
    var url by remember(prompt) { mutableStateOf(prompt.prefilledUrl) }
    var weight by remember(prompt) { mutableStateOf(prompt.prefilledWeightText) }
    var notes by remember(prompt) { mutableStateOf(prompt.prefilledNotes) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (prompt.editingLocalId != null) "Edit Jewellery Item" else "Manual Item Entry")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!prompt.infoMessage.isNullOrBlank()) {
                    Text(
                        text = prompt.infoMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text("Item Name / Description *") },
                    placeholder = { Text("e.g., Gold Ring RKPE0165") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_item_name_input")
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
                        .testTag("manual_item_weight_input")
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Product URL (Optional)") },
                    singleLine = true,
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
                    val ok = onSave(name, url, weight, notes)
                    if (!ok) {
                        errorMsg = "Enter both a valid item name and net weight (> 0 g)."
                    }
                },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("save_manual_item_button")
            ) {
                Text(if (prompt.editingLocalId != null) "Update Item" else "Add to Bill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun InventoryPickerDialog(
    availableItems: List<InventoryItemEntity>,
    onDismiss: () -> Unit,
    onAddSelected: (List<InventoryItemEntity>) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }

    val filtered = remember(availableItems, search) {
        if (search.isBlank()) availableItems
        else {
            val q = search.trim().lowercase()
            availableItems.filter {
                it.itemName.lowercase().contains(q) ||
                    it.productUrl.lowercase().contains(q) ||
                    (it.productRefId?.lowercase()?.contains(q) == true) ||
                    it.displayInventoryId.lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select from Available Inventory") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search name, ref ID, INV-...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inventory_picker_search_input")
                )

                if (filtered.isEmpty()) {
                    Text(
                        text = if (availableItems.isEmpty()) {
                            "No available inventory items found. Scan new items in Inventory or use Scan QR Item."
                        } else {
                            "No matching available items found."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filtered, key = { _, it -> it.id }) { _, inv ->
                            val isChecked = selectedIds.contains(inv.id)
                            LuxuryCard(
                                onClick = {
                                    if (isChecked) selectedIds.remove(inv.id) else selectedIds.add(inv.id)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("picker_inventory_item_${inv.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                if (!selectedIds.contains(inv.id)) selectedIds.add(inv.id)
                                            } else {
                                                selectedIds.remove(inv.id)
                                            }
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = inv.itemName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = buildString {
                                                inv.productRefId?.let { append("Ref #$it • ") }
                                                append(inv.displayInventoryId)
                                                append(" • ${DateUtils.formatShortDate(inv.dateAdded)}")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = FinancialMath.formatWeightGrams(inv.netWeightUg),
                                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = availableItems.filter { selectedIds.contains(it.id) }
                    onAddSelected(chosen)
                },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("confirm_add_inventory_to_bill_button")
            ) {
                Text("Add Selected (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NewBillScannedVerifyDialog(
    prompt: NewBillScannedPrompt,
    onDismiss: () -> Unit,
    onSaveToInventoryAndAdd: (
        name: String,
        category: String,
        weightText: String,
        otherDetails: String,
        notes: String
    ) -> Boolean,
    onAddWithoutSaving: (
        name: String,
        weightText: String,
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
        title = { Text("Verify Scanned QR Item") },
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
                            text = prompt.productUrl,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!prompt.productRefId.isNullOrBlank()) {
                            Text(
                                text = "Product Reference ID: #${prompt.productRefId}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text("Item Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it; errorMsg = null },
                    label = { Text("Net Weight (grams) *") },
                    placeholder = { Text("e.g., 0.434") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
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
                    label = { Text("Other Details") },
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

                // Dual explicit choices per spec
                Button(
                    onClick = {
                        val ok = onSaveToInventoryAndAdd(name, category, weight, otherDetails, notes)
                        if (!ok) errorMsg = "Enter a valid Item Name and Net Weight (> 0 g)."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_to_inventory_and_add_button")
                ) {
                    Text("Save to Inventory & Add to Bill")
                }

                OutlinedButton(
                    onClick = {
                        val ok = onAddWithoutSaving(name, weight, notes)
                        if (!ok) errorMsg = "Enter a valid Item Name and Net Weight (> 0 g)."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_without_saving_button")
                ) {
                    Text("Add to Bill Without Saving")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cancel")
            }
        }
    )
}
