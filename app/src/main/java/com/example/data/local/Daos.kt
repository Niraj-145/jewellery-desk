package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query(
        """
        SELECT c.*,
            COALESCE((SELECT SUM(b.final_amount_paise) FROM bills b WHERE b.customer_id = c.id), 0) AS totalBilledPaise,
            COALESCE((SELECT SUM(p.amount_paise) FROM payments p WHERE p.customer_id = c.id), 0) AS totalPaidPaise,
            (SELECT COUNT(*) FROM bills b WHERE b.customer_id = c.id) AS billCount
        FROM customers c
        ORDER BY c.shop_name COLLATE NOCASE ASC
        """
    )
    fun observeCustomersWithStats(): Flow<List<CustomerWithStats>>

    @Query(
        """
        SELECT c.*,
            COALESCE((SELECT SUM(b.final_amount_paise) FROM bills b WHERE b.customer_id = c.id), 0) AS totalBilledPaise,
            COALESCE((SELECT SUM(p.amount_paise) FROM payments p WHERE p.customer_id = c.id), 0) AS totalPaidPaise,
            (SELECT COUNT(*) FROM bills b WHERE b.customer_id = c.id) AS billCount
        FROM customers c
        WHERE c.id = :customerId
        """
    )
    fun observeCustomerWithStats(customerId: Long): Flow<CustomerWithStats?>

    @Query("SELECT * FROM customers ORDER BY id ASC")
    suspend fun getAllCustomersSnapshot(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :customerId")
    suspend fun deleteCustomerById(customerId: Long)

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()
}

@Dao
interface BillDao {
    @Query(
        """
        SELECT b.*,
            c.shop_name AS customerShopName,
            c.owner_name AS customerOwnerName,
            c.phone AS customerPhone,
            c.address AS customerAddress,
            (SELECT COUNT(*) FROM bill_items bi WHERE bi.bill_id = b.id) AS itemCount
        FROM bills b
        INNER JOIN customers c ON c.id = b.customer_id
        ORDER BY b.bill_date DESC, b.id DESC
        """
    )
    fun observeAllBillsWithCustomer(): Flow<List<BillWithCustomer>>

    @Query(
        """
        SELECT b.*,
            c.shop_name AS customerShopName,
            c.owner_name AS customerOwnerName,
            c.phone AS customerPhone,
            c.address AS customerAddress,
            (SELECT COUNT(*) FROM bill_items bi WHERE bi.bill_id = b.id) AS itemCount
        FROM bills b
        INNER JOIN customers c ON c.id = b.customer_id
        WHERE b.customer_id = :customerId
        ORDER BY b.bill_date DESC, b.id DESC
        """
    )
    fun observeBillsForCustomer(customerId: Long): Flow<List<BillWithCustomer>>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :billId")
    fun observeBillDetail(billId: Long): Flow<BillDetailBundle?>

    @Query("SELECT * FROM bills WHERE id = :billId")
    suspend fun getBillById(billId: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE bill_number = :billNumber LIMIT 1")
    suspend fun getBillByNumber(billNumber: String): BillEntity?

    @Query("SELECT COUNT(*) FROM bills WHERE bill_number LIKE :prefix || '%'")
    suspend fun countBillsWithPrefix(prefix: String): Int

    @Query("SELECT * FROM bills ORDER BY id ASC")
    suspend fun getAllBillsSnapshot(): List<BillEntity>

    @Query("SELECT * FROM bill_items ORDER BY id ASC")
    suspend fun getAllBillItemsSnapshot(): List<BillItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBill(bill: BillEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBills(bills: List<BillEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBillItems(items: List<BillItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillItemsReplace(items: List<BillItemEntity>)

    @Update
    suspend fun updateBill(bill: BillEntity)

    @Query("DELETE FROM bills WHERE id = :billId")
    suspend fun deleteBillById(billId: Long)

    @Query("DELETE FROM bill_items")
    suspend fun deleteAllBillItems()

    @Query("DELETE FROM bills")
    suspend fun deleteAllBills()
}

@Dao
interface InventoryDao {
    @Query(
        """
        SELECT i.*,
            b.bill_number AS linkedBillNumber,
            c.shop_name AS linkedCustomerName
        FROM inventory_items i
        LEFT JOIN bills b ON b.id = i.linked_bill_id
        LEFT JOIN customers c ON c.id = b.customer_id
        ORDER BY i.date_added DESC, i.id DESC
        """
    )
    fun observeAllInventoryWithBill(): Flow<List<InventoryItemWithBill>>

    @Query(
        """
        SELECT i.*,
            b.bill_number AS linkedBillNumber,
            c.shop_name AS linkedCustomerName
        FROM inventory_items i
        LEFT JOIN bills b ON b.id = i.linked_bill_id
        LEFT JOIN customers c ON c.id = b.customer_id
        WHERE i.id = :itemId
        """
    )
    fun observeInventoryItemWithBill(itemId: Long): Flow<InventoryItemWithBill?>

    @Query("SELECT * FROM inventory_items WHERE status = 'AVAILABLE' ORDER BY date_added DESC, id DESC")
    fun observeAvailableInventoryItems(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE id = :itemId")
    suspend fun getById(itemId: Long): InventoryItemEntity?

    @Query(
        """
        SELECT * FROM inventory_items
        WHERE LOWER(product_url) = LOWER(:url)
           OR (:refId IS NOT NULL AND :refId != '' AND LOWER(product_ref_id) = LOWER(:refId))
        LIMIT 1
        """
    )
    suspend fun findDuplicate(url: String, refId: String?): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE linked_bill_id = :billId")
    suspend fun getItemsLinkedToBill(billId: Long): List<InventoryItemEntity>

    @Query("SELECT * FROM inventory_items ORDER BY id ASC")
    suspend fun getAllInventorySnapshot(): List<InventoryItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: InventoryItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemsReplace(items: List<InventoryItemEntity>)

    @Update
    suspend fun updateItem(item: InventoryItemEntity)

    @Query("UPDATE inventory_items SET status = 'SOLD', linked_bill_id = :billId WHERE id IN (:itemIds) AND status = 'AVAILABLE'")
    suspend fun markItemsSold(itemIds: List<Long>, billId: Long): Int

    @Query("UPDATE inventory_items SET status = 'AVAILABLE', linked_bill_id = NULL WHERE linked_bill_id = :billId")
    suspend fun restoreItemsForBill(billId: Long)

    @Query("UPDATE inventory_items SET status = 'AVAILABLE', linked_bill_id = NULL WHERE id = :itemId")
    suspend fun restoreSingleItem(itemId: Long)

    @Query("DELETE FROM inventory_items WHERE id = :itemId AND status = 'AVAILABLE'")
    suspend fun deleteAvailableItem(itemId: Long): Int

    @Query("DELETE FROM inventory_items")
    suspend fun deleteAllInventory()
}

@Dao
interface PaymentDao {
    @Query(
        """
        SELECT p.*,
            c.shop_name AS customerShopName,
            c.phone AS customerPhone,
            b.bill_number AS billNumber
        FROM payments p
        INNER JOIN customers c ON c.id = p.customer_id
        LEFT JOIN bills b ON b.id = p.bill_id
        ORDER BY p.payment_date DESC, p.id DESC
        """
    )
    fun observeAllPaymentsWithDetails(): Flow<List<PaymentWithDetails>>

    @Query(
        """
        SELECT p.*,
            c.shop_name AS customerShopName,
            c.phone AS customerPhone,
            b.bill_number AS billNumber
        FROM payments p
        INNER JOIN customers c ON c.id = p.customer_id
        LEFT JOIN bills b ON b.id = p.bill_id
        WHERE p.customer_id = :customerId
        ORDER BY p.payment_date DESC, p.id DESC
        """
    )
    fun observePaymentsForCustomer(customerId: Long): Flow<List<PaymentWithDetails>>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM payments WHERE bill_id = :billId")
    suspend fun sumPaymentsForBill(billId: Long): Long

    @Query("SELECT * FROM payments ORDER BY id ASC")
    suspend fun getAllPaymentsSnapshot(): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<PaymentEntity>)

    @Query("DELETE FROM payments WHERE bill_id = :billId")
    suspend fun deletePaymentsForBill(billId: Long)

    @Query("DELETE FROM payments")
    suspend fun deleteAllPayments()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettingsOnce(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: SettingsEntity)
}
