package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.BillWithCustomer
import com.example.data.local.CustomerWithStats
import com.example.data.local.PaymentMethod
import com.example.data.pdf.ReceiptPdfGenerator
import com.example.domain.DateUtils
import com.example.domain.FinancialMath
import com.example.ui.components.ConfirmDeleteBillDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LuxuryCard
import com.example.ui.components.RecordPaymentDialog
import com.example.ui.components.StatusPill
import com.example.ui.theme.MutedGreen
import com.example.ui.theme.MutedRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    billId: Long,
    viewModel: BillsViewModel,
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onOpenInventoryItem: (Long) -> Unit = {}
) {
    LaunchedEffect(billId) {
        viewModel.loadBill(billId)
    }

    val state by viewModel.detailUiState.collectAsStateWithLifecycle()
    val bundle = state.bundle
    val settings = state.settings
    val symbol = settings.currencySymbol.ifBlank { "₹" }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && bundle != null) {
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        ReceiptPdfGenerator.generateBillPdfFile(context, bundle, settings)
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            FileInputStream(file).use { input -> input.copyTo(out) }
                        }
                    }
                    Toast.makeText(context, "PDF receipt saved.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Save failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (showPaymentDialog && bundle != null) {
        val cStats = CustomerWithStats(
            customer = bundle.customer,
            totalBilledPaise = bundle.bill.finalAmountPaise,
            totalPaidPaise = bundle.bill.amountReceivedPaise,
            billCount = 1
        )
        val bWithC = BillWithCustomer(
            bill = bundle.bill,
            customerShopName = bundle.customer.shopName,
            customerOwnerName = bundle.customer.ownerName,
            customerPhone = bundle.customer.phone,
            customerAddress = bundle.customer.address,
            itemCount = bundle.items.size
        )
        RecordPaymentDialog(
            customers = listOf(cStats),
            openBills = listOf(bWithC),
            preselectedCustomerId = bundle.customer.id,
            preselectedBillId = bundle.bill.id,
            currencySymbol = symbol,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { cId, bId, amount, method, date, notes ->
                viewModel.recordPaymentForBill(cId, bId ?: bundle.bill.id, amount, method, date, notes)
                showPaymentDialog = false
            }
        )
    }

    if (showDeleteDialog && bundle != null) {
        ConfirmDeleteBillDialog(
            billNumber = bundle.bill.billNumber,
            linkedInventoryCount = state.linkedInventoryCount,
            onDismiss = { showDeleteDialog = false },
            onConfirm = { restoreInventory ->
                showDeleteDialog = false
                viewModel.deleteBill(
                    billId = bundle.bill.id,
                    restoreLinkedInventory = restoreInventory,
                    onDeleted = onBack
                )
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(bundle?.bill?.billNumber ?: "Bill Receipt") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (bundle != null) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_bill_icon_button")
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete Bill",
                                tint = MaterialTheme.colorScheme.error
                            )
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
                    icon = Icons.Outlined.Payments,
                    title = "Loading bill receipt...",
                    message = "Fetching wholesale invoice details."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Action Bar: Download, Print, Share, WhatsApp
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { savePdfLauncher.launch("${bundle.bill.billNumber}.pdf") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("download_pdf_button")
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download PDF")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val pdf = withContext(Dispatchers.IO) {
                                            ReceiptPdfGenerator.generateBillPdfFile(context, bundle, settings)
                                        }
                                        ReceiptPdfGenerator.printBillPdf(context, pdf, bundle.bill.billNumber)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("print_bill_button")
                            ) {
                                Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Print Bill")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val pdf = withContext(Dispatchers.IO) {
                                            ReceiptPdfGenerator.generateBillPdfFile(context, bundle, settings)
                                        }
                                        ReceiptPdfGenerator.sharePdf(context, pdf, bundle.bill.billNumber, "com.whatsapp")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share WhatsApp")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val pdf = withContext(Dispatchers.IO) {
                                            ReceiptPdfGenerator.generateBillPdfFile(context, bundle, settings)
                                        }
                                        ReceiptPdfGenerator.sharePdf(context, pdf, bundle.bill.billNumber)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share PDF")
                            }
                        }
                    }
                }

                // Luxury Invoice Preview Card
                item {
                    LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Business Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = settings.businessName.ifBlank { "Jewellery Desk Wholesale" },
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (settings.businessAddress.isNotBlank()) {
                                        Text(
                                            text = settings.businessAddress,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (settings.businessPhone.isNotBlank()) {
                                        Text(
                                            text = "Mob: ${settings.businessPhone}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (settings.gstNumber.isNotBlank()) {
                                        Text(
                                            text = "GSTIN: ${settings.gstNumber}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = bundle.bill.billNumber,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = DateUtils.formatDate(bundle.bill.billDate),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    StatusPill(statusCode = bundle.bill.paymentStatus)
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.secondary, thickness = 1.5.dp)

                            // Retailer Block
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
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
                                            text = "BILLED TO",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = bundle.customer.shopName,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "${bundle.customer.ownerName} • ${bundle.customer.phone}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onOpenCustomer(bundle.customer.id) },
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text("Ledger")
                                    }
                                }
                            }

                            // Rate & Percentage Banner
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Rate: ${FinancialMath.formatInr(bundle.bill.ratePerGramPaise, symbol)}/g",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Percentage: ${FinancialMath.formatPercentage(bundle.bill.percentageBps)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Wt: ${FinancialMath.formatWeightGrams(bundle.bill.totalWeightUg)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                            // Items Table Header
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "ITEM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1.6f)
                                )
                                Text(
                                    text = "NET WT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(0.8f)
                                )
                                Text(
                                    text = "AMOUNT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            bundle.items.forEachIndexed { idx, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.6f)) {
                                        Text(
                                            text = "${idx + 1}. ${item.itemName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (item.inventoryItemId != null) {
                                            TextButton(
                                                onClick = { onOpenInventoryItem(item.inventoryItemId) },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.height(26.dp)
                                            ) {
                                                Text(
                                                    text = "Inventory #${item.inventoryItemId.toString().padStart(4, '0')} • View",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        if (item.notes.isNotBlank()) {
                                            Text(
                                                text = item.notes,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = FinancialMath.formatWeightGrams(item.netWeightUg),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(0.8f)
                                    )
                                    Text(
                                        text = FinancialMath.formatInr(item.calculatedAmountPaise, symbol),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }

                            // Totals Breakdown
                            ReceiptSummaryRow(
                                label = "Calculated Amount",
                                value = FinancialMath.formatInr(bundle.bill.calculatedAmountPaise, symbol)
                            )
                            ReceiptSummaryRow(
                                label = "Final Bill Amount",
                                value = FinancialMath.formatInr(bundle.bill.finalAmountPaise, symbol),
                                bold = true
                            )
                            ReceiptSummaryRow(
                                label = "Amount Received",
                                value = FinancialMath.formatInr(bundle.bill.amountReceivedPaise, symbol),
                                valueColor = MutedGreen
                            )
                            ReceiptSummaryRow(
                                label = "Remaining Balance",
                                value = FinancialMath.formatInr(bundle.bill.remainingBalancePaise, symbol),
                                bold = true,
                                valueColor = if (bundle.bill.remainingBalancePaise > 0L) MutedRed else MutedGreen
                            )

                            if (bundle.bill.remainingBalancePaise > 0L) {
                                Button(
                                    onClick = { showPaymentDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("record_additional_payment_button")
                                ) {
                                    Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Record Additional Payment")
                                }
                            }

                            if (bundle.payments.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = "Payment History for this Bill",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                bundle.payments.forEach { p ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${DateUtils.formatDate(p.paymentDate)} • ${PaymentMethod.fromCode(p.paymentMethod).label}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = FinancialMath.formatInr(p.amountPaise, symbol),
                                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MutedGreen
                                        )
                                    }
                                }
                            }

                            if (settings.invoiceFooter.isNotBlank()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = settings.invoiceFooter,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ReceiptSummaryRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = (if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                .copy(fontFeatureSettings = "tnum"),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}
