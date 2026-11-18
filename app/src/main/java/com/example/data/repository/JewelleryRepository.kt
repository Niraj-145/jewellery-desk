package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.BillDetailBundle
import com.example.data.local.BillEntity
import com.example.data.local.BillItemEntity
import com.example.data.local.BillWithCustomer
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerWithStats
import com.example.data.local.InventoryItemEntity
import com.example.data.local.InventoryItemWithBill
import com.example.data.local.InventoryStatus
import com.example.data.local.JewelleryDatabase
import com.example.data.local.LedgerEntry
import com.example.data.local.PaymentEntity
import com.example.data.local.PaymentMethod
import com.example.data.local.PaymentWithDetails
import com.example.data.local.SettingsEntity
import com.example.data.remote.SawariyaFetchResult
import com.example.data.remote.SawariyaSethProductService
import com.example.domain.BackupBill
import com.example.domain.BackupBillItem
import com.example.domain.BackupCustomer
import com.example.domain.BackupInventoryItem
import com.example.domain.BackupPayload
import com.example.domain.BackupPayment
import com.example.domain.BackupSettings
import com.example.domain.DateUtils
import com.example.domain.FinancialMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

data class DraftBillItemInput(
    val inventoryItemId: Long? = null,
    val itemName: String,
    val productUrl: String?,
    val netWeightUg: Long,
    val notes: String
)

sealed interface SaveInventoryResult {
    data class Saved(val itemId: Long) : SaveInventoryResult
    data class Duplicate(val existing: InventoryItemEntity) : SaveInventoryResult
}

class JewelleryRepository(
    private val database: JewelleryDatabase,
    private val productService: SawariyaSethProductService
) {
    private val customerDao = database.customerDao()
    private val billDao = database.billDao()
    private val inventoryDao = database.inventoryDao()
    private val paymentDao = database.paymentDao()
    private val settingsDao = database.settingsDao()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // Active draft bill reservations: inventoryItemId -> draftSessionId
    private val _reservedInventory = MutableStateFlow<Map<Long, String>>(emptyMap())
    val reservedInventory: StateFlow<Map<Long, String>> = _reservedInventory.asStateFlow()

    fun observeSettings(): Flow<SettingsEntity> =
        combine(settingsDao.observeSettings()) { arr -> arr[0] ?: SettingsEntity() }

    suspend fun getSettingsOnce(): SettingsEntity {
        return settingsDao.getSettingsOnce() ?: SettingsEntity().also {
            settingsDao.upsertSettings(it)
        }
    }

    suspend fun saveSettings(settings: SettingsEntity) {
        settingsDao.upsertSettings(settings.copy(id = 1))
    }

    fun observeCustomersWithStats(): Flow<List<CustomerWithStats>> =
        customerDao.observeCustomersWithStats()

    fun observeCustomerWithStats(customerId: Long): Flow<CustomerWithStats?> =
        customerDao.observeCustomerWithStats(customerId)

    fun observeAllBills(): Flow<List<BillWithCustomer>> =
        billDao.observeAllBillsWithCustomer()

    fun observeBillsForCustomer(customerId: Long): Flow<List<BillWithCustomer>> =
        billDao.observeBillsForCustomer(customerId)

    fun observeBillDetail(billId: Long): Flow<BillDetailBundle?> =
        billDao.observeBillDetail(billId)

    fun observeAllInventory(): Flow<List<InventoryItemWithBill>> =
        inventoryDao.observeAllInventoryWithBill()

    fun observeInventoryItem(itemId: Long): Flow<InventoryItemWithBill?> =
        inventoryDao.observeInventoryItemWithBill(itemId)

    fun observeAvailableInventory(): Flow<List<InventoryItemEntity>> =
        inventoryDao.observeAvailableInventoryItems()

    fun observeAllPayments(): Flow<List<PaymentWithDetails>> =
        paymentDao.observeAllPaymentsWithDetails()

    fun observePaymentsForCustomer(customerId: Long): Flow<List<PaymentWithDetails>> =
        paymentDao.observePaymentsForCustomer(customerId)

    fun observeCustomerLedger(customerId: Long): Flow<List<LedgerEntry>> =
        combine(
            customerDao.observeCustomerWithStats(customerId),
            billDao.observeBillsForCustomer(customerId),
            paymentDao.observePaymentsForCustomer(customerId)
        ) { customerStats, bills, payments ->
            if (customerStats == null) return@combine emptyList()
            val rawEvents = mutableListOf<LedgerEntry>()
            val opening = customerStats.customer.openingBalancePaise
            if (opening != 0L) {
                rawEvents.add(
                    LedgerEntry(
                        id = "OPEN-${customerStats.customer.id}",
                        dateMillis = customerStats.customer.createdAt,
                        title = "Opening Balance",
                        subtitle = "Brought forward balance",
                        debitPaise = if (opening > 0L) opening else 0L,
                        creditPaise = if (opening < 0L) -opening else 0L,
                        runningBalancePaise = 0L
                    )
                )
            }
            bills.forEach { b ->
                rawEvents.add(
                    LedgerEntry(
                        id = "BILL-${b.bill.id}",
                        dateMillis = b.bill.billDate,
                        title = "Bill ${b.bill.billNumber}",
                        subtitle = "${b.itemCount} item(s) • ${FinancialMath.formatWeightGrams(b.bill.totalWeightUg)}",
                        debitPaise = b.bill.finalAmountPaise,
                        creditPaise = 0L,
                        runningBalancePaise = 0L,
                        linkedBillId = b.bill.id
                    )
                )
            }
            payments.forEach { p ->
                val billSuffix = p.billNumber?.let { " • Against $it" } ?: " • On Account"
                rawEvents.add(
                    LedgerEntry(
                        id = "PAY-${p.payment.id}",
                        dateMillis = p.payment.paymentDate,
                        title = "Payment (${PaymentMethod.fromCode(p.payment.paymentMethod).label})",
                        subtitle = (p.payment.notes.ifBlank { "Payment received" }) + billSuffix,
                        debitPaise = 0L,
                        creditPaise = p.payment.amountPaise,
                        runningBalancePaise = 0L,
                        linkedBillId = p.payment.billId
                    )
                )
            }
            val chronological = rawEvents.sortedBy { it.dateMillis }
            var running = 0L
            val withBalances = chronological.map { entry ->
                running = running + entry.debitPaise - entry.creditPaise
                entry.copy(runningBalancePaise = running)
            }
            withBalances.reversed()
        }

    suspend fun saveCustomer(customer: CustomerEntity): Long {
        return if (customer.id == 0L) {
            customerDao.insertCustomer(customer)
        } else {
            customerDao.updateCustomer(customer)
            customer.id
        }
    }

    fun normalizeQrUrl(rawInput: String): String? =
        productService.normalizeAndValidateUrl(rawInput)

    fun extractProductRefId(url: String): String? =
        productService.extractProductRefId(url)

    suspend fun fetchSawariyaProduct(url: String): SawariyaFetchResult =
        productService.fetchProduct(url)

    suspend fun findDuplicateInventoryItem(rawUrlOrRef: String): InventoryItemEntity? {
        val normalized = productService.normalizeAndValidateUrl(rawUrlOrRef) ?: rawUrlOrRef.trim()
        val refId = productService.extractProductRefId(normalized)
        return inventoryDao.findDuplicate(normalized, refId)
    }

    suspend fun saveInventoryItem(
        productUrl: String,
        productRefId: String?,
        itemName: String,
        category: String?,
        netWeightUg: Long,
        otherDetails: String,
        imageUrl: String?,
        notes: String
    ): SaveInventoryResult {
        require(itemName.isNotBlank()) { "Item name is required." }
        require(netWeightUg > 0L) { "Net weight must be greater than zero." }
        val canonicalUrl = productService.normalizeAndValidateUrl(productUrl) ?: productUrl.trim()
        require(canonicalUrl.isNotBlank()) { "Product URL is required." }
        val resolvedRefId = productRefId?.trim()?.takeIf { it.isNotEmpty() }
            ?: productService.extractProductRefId(canonicalUrl)

        val existing = inventoryDao.findDuplicate(canonicalUrl, resolvedRefId)
        if (existing != null) {
            return SaveInventoryResult.Duplicate(existing)
        }

        val now = System.currentTimeMillis()
        val insertedId = inventoryDao.insertItem(
            InventoryItemEntity(
                productUrl = canonicalUrl,
                productRefId = resolvedRefId,
                itemName = itemName.trim(),
                category = category?.trim()?.takeIf { it.isNotEmpty() },
                netWeightUg = netWeightUg,
                otherDetails = otherDetails.trim(),
                imageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() },
                dateAdded = now,
                status = InventoryStatus.AVAILABLE.code,
                linkedBillId = null,
                notes = notes.trim(),
                createdAt = now
            )
        )
        return SaveInventoryResult.Saved(insertedId)
    }

    suspend fun updateInventoryItem(
        item: InventoryItemEntity,
        authorizedSoldCorrection: Boolean = false
    ) {
        val current = inventoryDao.getById(item.id)
            ?: throw IllegalStateException("Inventory item not found.")
        if (current.status == InventoryStatus.SOLD.code && !authorizedSoldCorrection) {
            throw IllegalStateException("Sold item details cannot be edited without authorized correction.")
        }
        inventoryDao.updateItem(item)
    }

    suspend fun restoreSoldItemAuthorized(itemId: Long) {
        database.withTransaction {
            val item = inventoryDao.getById(itemId)
                ?: throw IllegalStateException("Inventory item not found.")
            if (item.status == InventoryStatus.SOLD.code) {
                inventoryDao.restoreSingleItem(itemId)
            }
        }
    }

    suspend fun deleteAvailableInventoryItem(itemId: Long): Boolean {
        val deleted = inventoryDao.deleteAvailableItem(itemId)
        if (deleted > 0) {
            _reservedInventory.update { it - itemId }
        }
        return deleted > 0
    }

    suspend fun reserveInventoryForDraft(draftSessionId: String, inventoryItemId: Long): Boolean {
        val dbItem = inventoryDao.getById(inventoryItemId) ?: return false
        if (dbItem.status != InventoryStatus.AVAILABLE.code) return false
        var success = false
        _reservedInventory.update { current ->
            val owner = current[inventoryItemId]
            if (owner != null && owner != draftSessionId) {
                success = false
                current
            } else {
                success = true
                current + (inventoryItemId to draftSessionId)
            }
        }
        return success
    }

    fun releaseInventoryReservation(draftSessionId: String, inventoryItemId: Long) {
        _reservedInventory.update { current ->
            if (current[inventoryItemId] == draftSessionId) current - inventoryItemId else current
        }
    }

    fun releaseAllDraftReservations(draftSessionId: String) {
        _reservedInventory.update { current ->
            current.filterValues { it != draftSessionId }
        }
    }

    suspend fun saveBillTransaction(
        customerId: Long,
        billDateMillis: Long,
        ratePerGramPaise: Long,
        percentageBps: Int,
        items: List<DraftBillItemInput>,
        finalAmountPaise: Long,
        amountReceivedPaise: Long,
        paymentMethod: PaymentMethod,
        paymentDateMillis: Long,
        paymentNotes: String,
        billNotes: String,
        draftSessionId: String? = null
    ): Long {
        require(items.isNotEmpty()) { "At least one item is required to generate a bill." }
        require(ratePerGramPaise > 0L) { "Today's rate must be greater than zero." }
        require(percentageBps > 0) { "Percentage must be greater than zero." }
        require(finalAmountPaise >= 0L) { "Final bill amount cannot be negative." }
        require(amountReceivedPaise >= 0L) { "Amount received cannot be negative." }

        val inventoryIds = items.mapNotNull { it.inventoryItemId }
        require(inventoryIds.size == inventoryIds.distinct().size) {
            "The same inventory item cannot appear twice in one bill."
        }

        val savedBillId = database.withTransaction {
            // 1. Validate selected inventory items inside transaction
            for (invId in inventoryIds) {
                val invItem = inventoryDao.getById(invId)
                    ?: throw IllegalStateException("Selected inventory item (#$invId) no longer exists.")
                if (invItem.status != InventoryStatus.AVAILABLE.code) {
                    throw IllegalStateException("Item '${invItem.itemName}' is already Sold and cannot be billed again.")
                }
            }

            // 2. Generate unique bill number
            val prefix = DateUtils.formatBillNumberPrefix(billDateMillis)
            var sequence = billDao.countBillsWithPrefix(prefix) + 1
            var candidateNumber = "$prefix-${sequence.toString().padStart(4, '0')}"
            while (billDao.getBillByNumber(candidateNumber) != null) {
                sequence++
                candidateNumber = "$prefix-${sequence.toString().padStart(4, '0')}"
            }

            // 3. Calculate amounts and create bill
            val itemAmounts = items.map { item ->
                FinancialMath.calculateItemAmountPaise(
                    netWeightUg = item.netWeightUg,
                    ratePerGramPaise = ratePerGramPaise,
                    percentageBps = percentageBps
                )
            }
            val totalWeightUg = items.sumOf { it.netWeightUg }
            val calculatedAmountPaise = itemAmounts.sum()
            val remainingPaise = FinancialMath.calculateRemainingBalancePaise(
                finalAmountPaise = finalAmountPaise,
                amountReceivedPaise = amountReceivedPaise
            )
            val status = FinancialMath.derivePaymentStatus(
                finalAmountPaise = finalAmountPaise,
                amountReceivedPaise = amountReceivedPaise
            )

            val billId = billDao.insertBill(
                BillEntity(
                    billNumber = candidateNumber,
                    customerId = customerId,
                    billDate = billDateMillis,
                    ratePerGramPaise = ratePerGramPaise,
                    percentageBps = percentageBps,
                    totalWeightUg = totalWeightUg,
                    calculatedAmountPaise = calculatedAmountPaise,
                    finalAmountPaise = finalAmountPaise,
                    amountReceivedPaise = amountReceivedPaise,
                    remainingBalancePaise = remainingPaise,
                    paymentStatus = status,
                    notes = billNotes.trim()
                )
            )

            // 4. Save bill items
            val billItems = items.mapIndexed { idx, input ->
                BillItemEntity(
                    billId = billId,
                    inventoryItemId = input.inventoryItemId,
                    itemName = input.itemName.trim(),
                    productUrl = input.productUrl?.trim()?.takeIf { it.isNotEmpty() },
                    netWeightUg = input.netWeightUg,
                    calculatedAmountPaise = itemAmounts[idx],
                    notes = input.notes.trim()
                )
            }
            billDao.insertBillItems(billItems)

            // 5. Mark inventory items Sold and link bill ID
            if (inventoryIds.isNotEmpty()) {
                val updatedRows = inventoryDao.markItemsSold(inventoryIds, billId)
                if (updatedRows != inventoryIds.size) {
                    throw IllegalStateException("One or more inventory items became unavailable during checkout.")
                }
            }

            // 6. Record initial payment if any
            if (amountReceivedPaise > 0L) {
                paymentDao.insertPayment(
                    PaymentEntity(
                        customerId = customerId,
                        billId = billId,
                        amountPaise = amountReceivedPaise,
                        paymentMethod = paymentMethod.code,
                        paymentDate = paymentDateMillis,
                        notes = paymentNotes.trim().ifBlank { "Initial payment for $candidateNumber" }
                    )
                )
            }
            billId
        }

        if (draftSessionId != null) {
            releaseAllDraftReservations(draftSessionId)
        } else if (inventoryIds.isNotEmpty()) {
            _reservedInventory.update { it - inventoryIds.toSet() }
        }
        return savedBillId
    }

    suspend fun recordPaymentTransaction(
        customerId: Long,
        billId: Long?,
        amountPaise: Long,
        paymentMethod: PaymentMethod,
        paymentDateMillis: Long,
        notes: String
    ) {
        require(amountPaise > 0L) { "Payment amount must be greater than zero." }
        database.withTransaction {
            paymentDao.insertPayment(
                PaymentEntity(
                    customerId = customerId,
                    billId = billId,
                    amountPaise = amountPaise,
                    paymentMethod = paymentMethod.code,
                    paymentDate = paymentDateMillis,
                    notes = notes.trim()
                )
            )
            if (billId != null) {
                val bill = billDao.getBillById(billId)
                if (bill != null) {
                    val totalPaidForBill = paymentDao.sumPaymentsForBill(billId)
                    val remaining = FinancialMath.calculateRemainingBalancePaise(
                        bill.finalAmountPaise,
                        totalPaidForBill
                    )
                    val status = FinancialMath.derivePaymentStatus(
                        bill.finalAmountPaise,
                        totalPaidForBill
                    )
                    billDao.updateBill(
                        bill.copy(
                            amountReceivedPaise = totalPaidForBill,
                            remainingBalancePaise = remaining,
                            paymentStatus = status
                        )
                    )
                }
            }
        }
    }

    suspend fun getLinkedInventoryCountForBill(billId: Long): Int =
        inventoryDao.getItemsLinkedToBill(billId).size

    suspend fun deleteBillConfirmed(
        billId: Long,
        restoreLinkedInventoryToAvailable: Boolean = false
    ) {
        database.withTransaction {
            if (restoreLinkedInventoryToAvailable) {
                inventoryDao.restoreItemsForBill(billId)
            }
            paymentDao.deletePaymentsForBill(billId)
            billDao.deleteBillById(billId)
        }
    }

    suspend fun exportBackupJson(): String {
        val settings = getSettingsOnce()
        val customers = customerDao.getAllCustomersSnapshot()
        val bills = billDao.getAllBillsSnapshot()
        val items = billDao.getAllBillItemsSnapshot()
        val payments = paymentDao.getAllPaymentsSnapshot()
        val inventory = inventoryDao.getAllInventorySnapshot()

        val payload = BackupPayload(
            version = 2,
            exportedAt = System.currentTimeMillis(),
            settings = BackupSettings(
                businessName = settings.businessName,
                businessAddress = settings.businessAddress,
                businessPhone = settings.businessPhone,
                gstNumber = settings.gstNumber,
                defaultRatePaise = settings.defaultRatePaise,
                defaultPercentageBps = settings.defaultPercentageBps,
                currencySymbol = settings.currencySymbol,
                invoiceFooter = settings.invoiceFooter
            ),
            customers = customers.map {
                BackupCustomer(
                    id = it.id,
                    shopName = it.shopName,
                    ownerName = it.ownerName,
                    phone = it.phone,
                    address = it.address,
                    gstNumber = it.gstNumber,
                    openingBalancePaise = it.openingBalancePaise,
                    notes = it.notes,
                    createdAt = it.createdAt
                )
            },
            bills = bills.map {
                BackupBill(
                    id = it.id,
                    billNumber = it.billNumber,
                    customerId = it.customerId,
                    billDate = it.billDate,
                    ratePerGramPaise = it.ratePerGramPaise,
                    percentageBps = it.percentageBps,
                    totalWeightUg = it.totalWeightUg,
                    calculatedAmountPaise = it.calculatedAmountPaise,
                    finalAmountPaise = it.finalAmountPaise,
                    amountReceivedPaise = it.amountReceivedPaise,
                    remainingBalancePaise = it.remainingBalancePaise,
                    paymentStatus = it.paymentStatus,
                    notes = it.notes,
                    createdAt = it.createdAt
                )
            },
            billItems = items.map {
                BackupBillItem(
                    id = it.id,
                    billId = it.billId,
                    inventoryItemId = it.inventoryItemId,
                    itemName = it.itemName,
                    productUrl = it.productUrl,
                    netWeightUg = it.netWeightUg,
                    calculatedAmountPaise = it.calculatedAmountPaise,
                    notes = it.notes,
                    createdAt = it.createdAt
                )
            },
            payments = payments.map {
                BackupPayment(
                    id = it.id,
                    customerId = it.customerId,
                    billId = it.billId,
                    amountPaise = it.amountPaise,
                    paymentMethod = it.paymentMethod,
                    paymentDate = it.paymentDate,
                    notes = it.notes,
                    createdAt = it.createdAt
                )
            },
            inventoryItems = inventory.map {
                BackupInventoryItem(
                    id = it.id,
                    productUrl = it.productUrl,
                    productRefId = it.productRefId,
                    itemName = it.itemName,
                    category = it.category,
                    netWeightUg = it.netWeightUg,
                    otherDetails = it.otherDetails,
                    imageUrl = it.imageUrl,
                    dateAdded = it.dateAdded,
                    status = it.status,
                    linkedBillId = it.linkedBillId,
                    notes = it.notes,
                    createdAt = it.createdAt
                )
            }
        )
        return json.encodeToString(BackupPayload.serializer(), payload)
    }

    suspend fun restoreBackupJson(rawJson: String) {
        val payload = json.decodeFromString(BackupPayload.serializer(), rawJson)
        database.withTransaction {
            paymentDao.deleteAllPayments()
            billDao.deleteAllBillItems()
            inventoryDao.deleteAllInventory()
            billDao.deleteAllBills()
            customerDao.deleteAllCustomers()

            settingsDao.upsertSettings(
                SettingsEntity(
                    id = 1,
                    businessName = payload.settings.businessName,
                    businessAddress = payload.settings.businessAddress,
                    businessPhone = payload.settings.businessPhone,
                    gstNumber = payload.settings.gstNumber,
                    defaultRatePaise = payload.settings.defaultRatePaise,
                    defaultPercentageBps = payload.settings.defaultPercentageBps,
                    currencySymbol = payload.settings.currencySymbol,
                    invoiceFooter = payload.settings.invoiceFooter
                )
            )
            customerDao.insertCustomers(
                payload.customers.map {
                    CustomerEntity(
                        id = it.id,
                        shopName = it.shopName,
                        ownerName = it.ownerName,
                        phone = it.phone,
                        address = it.address,
                        gstNumber = it.gstNumber,
                        openingBalancePaise = it.openingBalancePaise,
                        notes = it.notes,
                        createdAt = it.createdAt
                    )
                }
            )
            billDao.insertBills(
                payload.bills.map {
                    BillEntity(
                        id = it.id,
                        billNumber = it.billNumber,
                        customerId = it.customerId,
                        billDate = it.billDate,
                        ratePerGramPaise = it.ratePerGramPaise,
                        percentageBps = it.percentageBps,
                        totalWeightUg = it.totalWeightUg,
                        calculatedAmountPaise = it.calculatedAmountPaise,
                        finalAmountPaise = it.finalAmountPaise,
                        amountReceivedPaise = it.amountReceivedPaise,
                        remainingBalancePaise = it.remainingBalancePaise,
                        paymentStatus = it.paymentStatus,
                        notes = it.notes,
                        createdAt = it.createdAt
                    )
                }
            )
            inventoryDao.insertItemsReplace(
                payload.inventoryItems.map {
                    InventoryItemEntity(
                        id = it.id,
                        productUrl = it.productUrl,
                        productRefId = it.productRefId,
                        itemName = it.itemName,
                        category = it.category,
                        netWeightUg = it.netWeightUg,
                        otherDetails = it.otherDetails,
                        imageUrl = it.imageUrl,
                        dateAdded = it.dateAdded,
                        status = it.status,
                        linkedBillId = it.linkedBillId,
                        notes = it.notes,
                        createdAt = it.createdAt
                    )
                }
            )
            billDao.insertBillItemsReplace(
                payload.billItems.map {
                    BillItemEntity(
                        id = it.id,
                        billId = it.billId,
                        inventoryItemId = it.inventoryItemId,
                        itemName = it.itemName,
                        productUrl = it.productUrl,
                        netWeightUg = it.netWeightUg,
                        calculatedAmountPaise = it.calculatedAmountPaise,
                        notes = it.notes,
                        createdAt = it.createdAt
                    )
                }
            )
            paymentDao.insertPayments(
                payload.payments.map {
                    PaymentEntity(
                        id = it.id,
                        customerId = it.customerId,
                        billId = it.billId,
                        amountPaise = it.amountPaise,
                        paymentMethod = it.paymentMethod,
                        paymentDate = it.paymentDate,
                        notes = it.notes,
                        createdAt = it.createdAt
                    )
                }
            )
        }
        _reservedInventory.value = emptyMap()
    }
}
