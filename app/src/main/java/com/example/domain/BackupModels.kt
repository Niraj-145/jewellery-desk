package com.example.domain

import kotlinx.serialization.Serializable

@Serializable
data class BackupPayload(
    val version: Int = 2,
    val exportedAt: Long,
    val settings: BackupSettings,
    val customers: List<BackupCustomer>,
    val bills: List<BackupBill>,
    val billItems: List<BackupBillItem>,
    val payments: List<BackupPayment>,
    val inventoryItems: List<BackupInventoryItem> = emptyList()
)

@Serializable
data class BackupSettings(
    val businessName: String,
    val businessAddress: String,
    val businessPhone: String,
    val gstNumber: String,
    val defaultRatePaise: Long,
    val defaultPercentageBps: Int,
    val currencySymbol: String,
    val invoiceFooter: String
)

@Serializable
data class BackupCustomer(
    val id: Long,
    val shopName: String,
    val ownerName: String,
    val phone: String,
    val address: String,
    val gstNumber: String,
    val openingBalancePaise: Long,
    val notes: String,
    val createdAt: Long
)

@Serializable
data class BackupBill(
    val id: Long,
    val billNumber: String,
    val customerId: Long,
    val billDate: Long,
    val ratePerGramPaise: Long,
    val percentageBps: Int,
    val totalWeightUg: Long,
    val calculatedAmountPaise: Long,
    val finalAmountPaise: Long,
    val amountReceivedPaise: Long,
    val remainingBalancePaise: Long,
    val paymentStatus: String,
    val notes: String,
    val createdAt: Long
)

@Serializable
data class BackupBillItem(
    val id: Long,
    val billId: Long,
    val inventoryItemId: Long? = null,
    val itemName: String,
    val productUrl: String?,
    val netWeightUg: Long,
    val calculatedAmountPaise: Long,
    val notes: String,
    val createdAt: Long
)

@Serializable
data class BackupPayment(
    val id: Long,
    val customerId: Long,
    val billId: Long?,
    val amountPaise: Long,
    val paymentMethod: String,
    val paymentDate: Long,
    val notes: String,
    val createdAt: Long
)

@Serializable
data class BackupInventoryItem(
    val id: Long,
    val productUrl: String,
    val productRefId: String? = null,
    val itemName: String,
    val category: String? = null,
    val netWeightUg: Long,
    val otherDetails: String = "",
    val imageUrl: String? = null,
    val dateAdded: Long,
    val status: String,
    val linkedBillId: Long? = null,
    val notes: String = "",
    val createdAt: Long
)
