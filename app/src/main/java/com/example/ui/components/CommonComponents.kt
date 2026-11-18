package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BillWithCustomer
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerWithStats
import com.example.data.local.PaymentMethod
import com.example.data.local.PaymentStatus
import com.example.domain.FinancialMath
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.GreenContainer
import com.example.ui.theme.MutedGreen
import com.example.ui.theme.MutedRed
import com.example.ui.theme.RedContainer
import com.example.ui.theme.WarmAmber

@Composable
fun LuxuryCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    val shape = RoundedCornerShape(14.dp)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            content = content
        )
    }
}

@Composable
fun KpiMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    LuxuryCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            color = accentColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = accentColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.merge(
                    TextStyle(
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Bold
                    )
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    statusCode: String,
    modifier: Modifier = Modifier
) {
    val status = PaymentStatus.fromCode(statusCode)
    val (bg, fg) = when (status) {
        PaymentStatus.PAID -> GreenContainer to MutedGreen
        PaymentStatus.PARTIALLY_PAID -> AmberContainer to WarmAmber
        PaymentStatus.UNPAID -> RedContainer to MutedRed
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    LuxuryCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun CustomerFormDialog(
    existingCustomer: CustomerEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        shopName: String,
        ownerName: String,
        phone: String,
        address: String,
        gstNumber: String,
        openingBalanceInput: String,
        notes: String
    ) -> Unit
) {
    var shopName by remember(existingCustomer) { mutableStateOf(existingCustomer?.shopName ?: "") }
    var ownerName by remember(existingCustomer) { mutableStateOf(existingCustomer?.ownerName ?: "") }
    var phone by remember(existingCustomer) { mutableStateOf(existingCustomer?.phone ?: "") }
    var address by remember(existingCustomer) { mutableStateOf(existingCustomer?.address ?: "") }
    var gstNumber by remember(existingCustomer) { mutableStateOf(existingCustomer?.gstNumber ?: "") }
    var openingBalance by remember(existingCustomer) {
        mutableStateOf(
            if (existingCustomer != null && existingCustomer.openingBalancePaise != 0L) {
                FinancialMath.paiseToPlainRupeesString(existingCustomer.openingBalancePaise)
            } else "0"
        )
    }
    var notes by remember(existingCustomer) { mutableStateOf(existingCustomer?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingCustomer == null) "Add Retailer Customer" else "Edit ${existingCustomer.displayCustomerId}",
                style = MaterialTheme.typography.titleLarge
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
                    value = shopName,
                    onValueChange = { shopName = it; errorMsg = null },
                    label = { Text("Shop Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_shop_name_input")
                )
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it; errorMsg = null },
                    label = { Text("Owner Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_owner_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = null },
                    label = { Text("Mobile Number *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_phone_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gstNumber,
                    onValueChange = { gstNumber = it.uppercase() },
                    label = { Text("GST Number (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openingBalance,
                    onValueChange = { openingBalance = it },
                    label = { Text("Opening Balance (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        shopName.isBlank() -> errorMsg = "Shop Name is required."
                        ownerName.isBlank() -> errorMsg = "Owner Name is required."
                        phone.isBlank() -> errorMsg = "Mobile Number is required."
                        FinancialMath.parseRupeesToPaise(openingBalance, allowNegative = true) == null ->
                            errorMsg = "Enter a valid Opening Balance."
                        else -> {
                            onSave(shopName, ownerName, phone, address, gstNumber, openingBalance, notes)
                        }
                    }
                },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("save_customer_button")
            ) {
                Text("Save Customer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPaymentDialog(
    customers: List<CustomerWithStats>,
    openBills: List<BillWithCustomer>,
    preselectedCustomerId: Long? = null,
    preselectedBillId: Long? = null,
    currencySymbol: String = "₹",
    onDismiss: () -> Unit,
    onConfirm: (
        customerId: Long,
        billId: Long?,
        amountPaise: Long,
        method: PaymentMethod,
        dateMillis: Long,
        notes: String
    ) -> Unit
) {
    var selectedCustomerId by remember(preselectedCustomerId) {
        mutableStateOf(preselectedCustomerId ?: customers.firstOrNull()?.customer?.id)
    }
    var selectedBillId by remember(preselectedBillId) { mutableStateOf(preselectedBillId) }
    var customerExpanded by remember { mutableStateOf(false) }
    var billExpanded by remember { mutableStateOf(false) }
    var methodExpanded by remember { mutableStateOf(false) }

    val matchingBills = remember(selectedCustomerId, openBills) {
        openBills.filter { it.bill.customerId == selectedCustomerId }
    }
    val initialAmount = remember(preselectedBillId, openBills) {
        val bill = openBills.firstOrNull { it.bill.id == preselectedBillId }
        if (bill != null && bill.bill.remainingBalancePaise > 0L) {
            FinancialMath.paiseToPlainRupeesString(bill.bill.remainingBalancePaise)
        } else ""
    }
    var amountInput by remember { mutableStateOf(initialAmount) }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val selectedCustomerObj = customers.firstOrNull { it.customer.id == selectedCustomerId }
    val selectedBillObj = matchingBills.firstOrNull { it.bill.id == selectedBillId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Payment", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = customerExpanded,
                    onExpandedChange = { if (preselectedCustomerId == null) customerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCustomerObj?.let {
                            "${it.customer.shopName} (Bal: ${FinancialMath.formatInr(it.outstandingPaise, currencySymbol)})"
                        } ?: "Select Customer",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Retailer Customer") },
                        trailingIcon = {
                            if (preselectedCustomerId == null) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerExpanded)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = customerExpanded,
                        onDismissRequest = { customerExpanded = false }
                    ) {
                        customers.forEach { c ->
                            DropdownMenuItem(
                                text = {
                                    Text("${c.customer.shopName} • ${FinancialMath.formatInr(c.outstandingPaise, currencySymbol)}")
                                },
                                onClick = {
                                    selectedCustomerId = c.customer.id
                                    selectedBillId = null
                                    customerExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = billExpanded,
                    onExpandedChange = { if (preselectedBillId == null) billExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedBillObj?.let {
                            "${it.bill.billNumber} (Due: ${FinancialMath.formatInr(it.bill.remainingBalancePaise, currencySymbol)})"
                        } ?: "General On-Account Payment",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Link to Bill (Optional)") },
                        trailingIcon = {
                            if (preselectedBillId == null) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = billExpanded)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = billExpanded,
                        onDismissRequest = { billExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("General On-Account Payment") },
                            onClick = {
                                selectedBillId = null
                                billExpanded = false
                            }
                        )
                        matchingBills.forEach { b ->
                            DropdownMenuItem(
                                text = {
                                    Text("${b.bill.billNumber} • Due ${FinancialMath.formatInr(b.bill.remainingBalancePaise, currencySymbol)}")
                                },
                                onClick = {
                                    selectedBillId = b.bill.id
                                    if (amountInput.isBlank()) {
                                        amountInput = FinancialMath.paiseToPlainRupeesString(b.bill.remainingBalancePaise)
                                    }
                                    billExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it; errorMsg = null },
                    label = { Text("Amount Received ($currencySymbol) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("record_payment_amount_input")
                )

                ExposedDropdownMenuBox(
                    expanded = methodExpanded,
                    onExpandedChange = { methodExpanded = it }
                ) {
                    OutlinedTextField(
                        value = method.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Payment Method") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = methodExpanded,
                        onDismissRequest = { methodExpanded = false }
                    ) {
                        PaymentMethod.entries.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.label) },
                                onClick = {
                                    method = m
                                    methodExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Payment Notes") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val custId = selectedCustomerId
                    val paise = FinancialMath.parseRupeesToPaise(amountInput)
                    when {
                        custId == null -> errorMsg = "Select a customer."
                        paise == null || paise <= 0L -> errorMsg = "Enter a valid payment amount (> 0)."
                        else -> onConfirm(custId, selectedBillId, paise, method, System.currentTimeMillis(), notes)
                    }
                },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("confirm_record_payment_button")
            ) {
                Text("Save Payment")
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
fun ConfirmDeleteBillDialog(
    billNumber: String,
    linkedInventoryCount: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (restoreLinkedInventory: Boolean) -> Unit
) {
    var restoreInventory by remember(billNumber, linkedInventoryCount) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete Bill $billNumber?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This finalized bill, its items, and any payments linked directly to this bill will be permanently removed. This cannot be undone."
                )
                if (linkedInventoryCount > 0) {
                    Surface(
                        color = AmberContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = restoreInventory,
                                onCheckedChange = { restoreInventory = it },
                                modifier = Modifier.testTag("restore_inventory_checkbox")
                            )
                            Text(
                                text = "Restore $linkedInventoryCount linked inventory item(s) back to Available",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(restoreInventory) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("confirm_delete_bill_button")
            ) {
                Text("Delete Bill")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Keep Bill")
            }
        }
    )
}
