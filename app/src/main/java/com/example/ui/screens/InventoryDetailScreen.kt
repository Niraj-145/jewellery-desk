package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.InventoryItemEntity
import com.example.data.local.InventoryStatus
import com.example.domain.DateUtils
import com.example.domain.FinancialMath
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LuxuryCard
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.WarmAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryDetailScreen(
    itemId: Long,
    viewModel: InventoryViewModel,
    onBack: () -> Unit,
    onOpenBillDetail: (Long) -> Unit
) {
    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }

    val state by viewModel.detailUiState.collectAsStateWithLifecycle()
    val bundle = state.bundle
    val context = LocalContext.current

    var showEditDialog by remember { mutableStateOf(false) }
    var editAsAuthorizedSoldCorrection by remember { mutableStateOf(false) }
    var showAuthorizedCorrectionPrompt by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.successMessage, state.errorMessage) {
        state.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearDetailFeedback()
        }
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearDetailFeedback()
        }
    }

    if (showEditDialog && bundle != null) {
        EditInventoryItemDialog(
            item = bundle.item,
            isAuthorizedSoldCorrection = editAsAuthorizedSoldCorrection,
            onDismiss = { showEditDialog = false },
            onSave = { name, category, weightText, otherDetails, notes ->
                val ok = if (editAsAuthorizedSoldCorrection) {
                    viewModel.authorizeAndSaveSoldCorrection(bundle.item, name, category, weightText, otherDetails, notes)
                } else {
                    viewModel.updateAvailableItem(bundle.item, name, category, weightText, otherDetails, notes)
                }
                if (ok) showEditDialog = false
                ok
            }
        )
    }

    if (showAuthorizedCorrectionPrompt && bundle != null) {
        AlertDialog(
            onDismissRequest = { showAuthorizedCorrectionPrompt = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.AdminPanelSettings,
                    contentDescription = "Authorized Correction",
                    tint = WarmAmber
                )
            },
            title = { Text("Authorized Sold Item Correction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This item is marked Sold" +
                            (bundle.linkedBillNumber?.let { " and linked to Bill $it" } ?: "") +
                            ". Confirm owner authorization to either edit its stored record or restore it to Available inventory.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            showAuthorizedCorrectionPrompt = false
                            viewModel.authorizeAndRestoreSoldToAvailable(bundle.item.id)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("authorized_restore_available_button")
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore Status to Available")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAuthorizedCorrectionPrompt = false
                        editAsAuthorizedSoldCorrection = true
                        showEditDialog = true
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("authorized_edit_sold_button")
                ) {
                    Text("Edit Sold Record")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAuthorizedCorrectionPrompt = false },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirm && bundle != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Remove ${bundle.item.displayInventoryId}?") },
            text = {
                Text("This Available inventory item will be permanently removed from the stock list.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteAvailableItem(bundle.item.id, onDeleted = onBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Keep")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(bundle?.item?.displayInventoryId ?: "Inventory Item") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (bundle != null) {
                        val isSold = bundle.item.status == InventoryStatus.SOLD.code
                        if (!isSold) {
                            IconButton(
                                onClick = {
                                    editAsAuthorizedSoldCorrection = false
                                    showEditDialog = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("edit_inventory_item_button")
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit Item")
                            }
                            if (!state.isReservedInDraft) {
                                IconButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("delete_inventory_item_button")
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete Item",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (bundle == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                EmptyStateCard(
                    icon = Icons.Outlined.Inventory2,
                    title = "Loading inventory item...",
                    message = "Fetching jewellery item details from database."
                )
            }
        } else {
            val item = bundle.item
            val isSold = item.status == InventoryStatus.SOLD.code

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Summary Card
                item {
                    LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.itemName,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = buildString {
                                            append("Internal ID: ${item.displayInventoryId}")
                                            if (!item.productRefId.isNullOrBlank()) {
                                                append(" • Ref #${item.productRefId}")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                InventoryStatusPill(
                                    statusCode = item.status,
                                    isReservedInDraft = state.isReservedInDraft
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "NET WEIGHT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = FinancialMath.formatWeightGrams(item.netWeightUg),
                                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "DATE ADDED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = DateUtils.formatDateTime(item.dateAdded),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Linked Bill Card (if sold & linked)
                if (item.linkedBillId != null) {
                    item {
                        LuxuryCard(
                            onClick = { onOpenBillDetail(item.linkedBillId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inventory_linked_bill_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ReceiptLong,
                                        contentDescription = "Linked Bill",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = "Linked Bill: ${bundle.linkedBillNumber ?: "#${item.linkedBillId}"}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (!bundle.linkedCustomerName.isNullOrBlank()) {
                                            Text(
                                                text = "Sold to ${bundle.linkedCustomerName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "View Bill",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Full Saved Details Card
                item {
                    LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Saved Product Information",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                            InventoryDetailRow("Product Name", item.itemName)
                            InventoryDetailRow("Internal Item ID", item.displayInventoryId)
                            InventoryDetailRow(
                                "Product Reference ID",
                                item.productRefId?.let { "#$it" } ?: "Not available"
                            )
                            InventoryDetailRow("QR / Product URL", item.productUrl)
                            InventoryDetailRow(
                                "Product Category",
                                item.category?.takeIf { it.isNotBlank() } ?: "Not provided by source"
                            )
                            InventoryDetailRow(
                                "Net Weight",
                                FinancialMath.formatWeightGrams(item.netWeightUg)
                            )
                            InventoryDetailRow(
                                "Current Status",
                                when {
                                    isSold -> "Sold"
                                    state.isReservedInDraft -> "Available (In Active Draft Bill)"
                                    else -> "Available"
                                }
                            )
                            if (item.otherDetails.isNotBlank()) {
                                InventoryDetailRow("Other Source Details", item.otherDetails)
                            }
                            if (item.notes.isNotBlank()) {
                                InventoryDetailRow("Notes", item.notes)
                            }
                        }
                    }
                }

                // Sold Lock & Authorized Correction Card
                if (isSold) {
                    item {
                        Surface(
                            color = AmberContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Lock,
                                        contentDescription = "Locked",
                                        tint = WarmAmber
                                    )
                                    Text(
                                        text = "Sold Item Protected",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "Direct editing and deletion are locked because this jewellery piece has been finalized on a bill. Use Authorized Correction only if the owner needs to fix an error.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                OutlinedButton(
                                    onClick = { showAuthorizedCorrectionPrompt = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("authorized_correction_button")
                                ) {
                                    Icon(
                                        Icons.Outlined.AdminPanelSettings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Authorized Correction")
                                }
                            }
                        }
                    }
                } else {
                    item {
                        OutlinedButton(
                            onClick = {
                                editAsAuthorizedSoldCorrection = false
                                showEditDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Item Information")
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(14.dp)) }
            }
        }
    }
}

@Composable
private fun InventoryDetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EditInventoryItemDialog(
    item: InventoryItemEntity,
    isAuthorizedSoldCorrection: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        category: String,
        weightText: String,
        otherDetails: String,
        notes: String
    ) -> Boolean
) {
    var name by remember(item) { mutableStateOf(item.itemName) }
    var category by remember(item) { mutableStateOf(item.category.orEmpty()) }
    var weight by remember(item) { mutableStateOf(FinancialMath.ugToPlainGramsString(item.netWeightUg)) }
    var otherDetails by remember(item) { mutableStateOf(item.otherDetails) }
    var notes by remember(item) { mutableStateOf(item.notes) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isAuthorizedSoldCorrection) "Authorized Correction (${item.displayInventoryId})"
                else "Edit ${item.displayInventoryId}"
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (Optional)") },
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
                    label = { Text("Notes") },
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
                modifier = Modifier.height(48.dp)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cancel")
            }
        }
    )
}
