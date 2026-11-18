package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class PaymentStatus(val code: String, val label: String) {
    PAID("PAID", "Paid"),
    PARTIALLY_PAID("PARTIALLY_PAID", "Partially Paid"),
    UNPAID("UNPAID", "Unpaid");

    companion object {
        fun fromCode(code: String): PaymentStatus =
            entries.firstOrNull { it.code == code } ?: UNPAID
    }
}

enum class PaymentMethod(val code: String, val label: String) {
    CASH("CASH", "Cash"),
    UPI("UPI", "UPI"),
    BANK_TRANSFER("BANK_TRANSFER", "Bank Transfer"),
    OTHER("OTHER", "Other");

    companion object {
        fun fromCode(code: String): PaymentMethod =
            entries.firstOrNull { it.code == code } ?: CASH
    }
}

enum class InventoryStatus(val code: String, val label: String) {
    AVAILABLE("AVAILABLE", "Available"),
    SOLD("SOLD", "Sold");

    companion object {
        fun fromCode(code: String): InventoryStatus =
            entries.firstOrNull { it.code == code } ?: AVAILABLE
    }
}

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["shop_name"]),
        Index(value = ["phone"])
    ]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "shop_name") val shopName: String,
    @ColumnInfo(name = "owner_name") val ownerName: String,
    @ColumnInfo(name = "phone") val phone: String,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "gst_number") val gstNumber: String = "",
    @ColumnInfo(name = "opening_balance_paise") val openingBalancePaise: Long = 0L,
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
) {
    val displayCustomerId: String
        get() = "CUST-${id.toString().padStart(4, '0')}"
}

@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["bill_number"], unique = true),
        Index(value = ["customer_id"]),
        Index(value = ["bill_date"]),
        Index(value = ["payment_status"])
    ]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "bill_number") val billNumber: String,
    @ColumnInfo(name = "customer_id") val customerId: Long,
    @ColumnInfo(name = "bill_date") val billDate: Long,
    @ColumnInfo(name = "rate_per_gram_paise") val ratePerGramPaise: Long,
    @ColumnInfo(name = "percentage_bps") val percentageBps: Int,
    @ColumnInfo(name = "total_weight_ug") val totalWeightUg: Long,
    @ColumnInfo(name = "calculated_amount_paise") val calculatedAmountPaise: Long,
    @ColumnInfo(name = "final_amount_paise") val finalAmountPaise: Long,
    @ColumnInfo(name = "amount_received_paise") val amountReceivedPaise: Long,
    @ColumnInfo(name = "remaining_balance_paise") val remainingBalancePaise: Long,
    @ColumnInfo(name = "payment_status") val paymentStatus: String,
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "inventory_items",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_bill_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["product_url"], unique = true),
        Index(value = ["product_ref_id"], unique = true),
        Index(value = ["status"]),
        Index(value = ["date_added"]),
        Index(value = ["linked_bill_id"])
    ]
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "product_url") val productUrl: String,
    @ColumnInfo(name = "product_ref_id") val productRefId: String? = null,
    @ColumnInfo(name = "item_name") val itemName: String,
    @ColumnInfo(name = "category") val category: String? = null,
    @ColumnInfo(name = "net_weight_ug") val netWeightUg: Long,
    @ColumnInfo(name = "other_details") val otherDetails: String = "",
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    @ColumnInfo(name = "date_added") val dateAdded: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "status") val status: String = InventoryStatus.AVAILABLE.code,
    @ColumnInfo(name = "linked_bill_id") val linkedBillId: Long? = null,
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
) {
    val displayInventoryId: String
        get() = "INV-${id.toString().padStart(4, '0')}"
}

@Entity(
    tableName = "bill_items",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventory_item_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["bill_id"]),
        Index(value = ["inventory_item_id"])
    ]
)
data class BillItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "bill_id") val billId: Long,
    @ColumnInfo(name = "inventory_item_id") val inventoryItemId: Long? = null,
    @ColumnInfo(name = "item_name") val itemName: String,
    @ColumnInfo(name = "product_url") val productUrl: String? = null,
    @ColumnInfo(name = "net_weight_ug") val netWeightUg: Long,
    @ColumnInfo(name = "calculated_amount_paise") val calculatedAmountPaise: Long,
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["customer_id"]),
        Index(value = ["bill_id"]),
        Index(value = ["payment_date"])
    ]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "customer_id") val customerId: Long,
    @ColumnInfo(name = "bill_id") val billId: Long? = null,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "payment_date") val paymentDate: Long,
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "business_name") val businessName: String = "Jewellery Desk Wholesale",
    @ColumnInfo(name = "business_address") val businessAddress: String = "Johari Bazaar, Jaipur, Rajasthan",
    @ColumnInfo(name = "business_phone") val businessPhone: String = "",
    @ColumnInfo(name = "gst_number") val gstNumber: String = "",
    @ColumnInfo(name = "default_rate_paise") val defaultRatePaise: Long = 0L,
    @ColumnInfo(name = "default_percentage_bps") val defaultPercentageBps: Int = 7200,
    @ColumnInfo(name = "currency_symbol") val currencySymbol: String = "₹",
    @ColumnInfo(name = "invoice_footer") val invoiceFooter: String = "Thank you for your wholesale business. Subject to local jurisdiction."
)

data class InventoryItemWithBill(
    @Embedded val item: InventoryItemEntity,
    val linkedBillNumber: String?,
    val linkedCustomerName: String?
)

data class CustomerWithStats(
    @Embedded val customer: CustomerEntity,
    val totalBilledPaise: Long,
    val totalPaidPaise: Long,
    val billCount: Int
) {
    val outstandingPaise: Long
        get() = customer.openingBalancePaise + totalBilledPaise - totalPaidPaise
}

data class BillWithCustomer(
    @Embedded val bill: BillEntity,
    val customerShopName: String,
    val customerOwnerName: String,
    val customerPhone: String,
    val customerAddress: String,
    val itemCount: Int
)

data class BillDetailBundle(
    @Embedded val bill: BillEntity,
    @Relation(parentColumn = "customer_id", entityColumn = "id")
    val customer: CustomerEntity,
    @Relation(parentColumn = "id", entityColumn = "bill_id")
    val items: List<BillItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "bill_id")
    val payments: List<PaymentEntity>
)

data class PaymentWithDetails(
    @Embedded val payment: PaymentEntity,
    val customerShopName: String,
    val customerPhone: String,
    val billNumber: String?
)

data class LedgerEntry(
    val id: String,
    val dateMillis: Long,
    val title: String,
    val subtitle: String,
    val debitPaise: Long,
    val creditPaise: Long,
    val runningBalancePaise: Long,
    val linkedBillId: Long? = null
)
