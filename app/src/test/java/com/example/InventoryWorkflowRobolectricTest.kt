package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CustomerEntity
import com.example.data.local.InventoryStatus
import com.example.data.local.JewelleryDatabase
import com.example.data.local.PaymentMethod
import com.example.data.remote.SawariyaSethProductService
import com.example.data.repository.DraftBillItemInput
import com.example.data.repository.JewelleryRepository
import com.example.data.repository.SaveInventoryResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InventoryWorkflowRobolectricTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var database: JewelleryDatabase
    private lateinit var repository: JewelleryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(context.cacheDir, "test_inventory_${System.nanoTime()}.db")
        database = openDiskDatabase()
        repository = JewelleryRepository(database, SawariyaSethProductService())
    }

    private fun openDiskDatabase(): JewelleryDatabase =
        Room.databaseBuilder(context, JewelleryDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()

    @After
    fun teardown() {
        if (database.isOpen) database.close()
        dbFile.delete()
        File("${dbFile.absolutePath}-wal").delete()
        File("${dbFile.absolutePath}-shm").delete()
    }

    @Test
    fun `acceptance 1 and 2 - scan QR save to inventory and persist across DB reopen without duplicates`() = runBlocking {
        // Test 1: Save scanned item and reopen database to verify persistence
        val save1 = repository.saveInventoryItem(
            productUrl = "https://sawariyaseth.cc/item/590268/",
            productRefId = null, // auto-extracted as 590268
            itemName = "RKPE0165",
            category = null,
            netWeightUg = 434_000L,
            otherDetails = "Gold: 0.434 g",
            imageUrl = null,
            notes = "Verified tag"
        )
        assertTrue(save1 is SaveInventoryResult.Saved)
        val savedId = (save1 as SaveInventoryResult.Saved).itemId

        // Close and reopen DB to simulate full app restart
        database.close()
        database = openDiskDatabase()
        repository = JewelleryRepository(database, SawariyaSethProductService())

        val persisted = repository.observeInventoryItem(savedId).first()
        assertNotNull(persisted)
        assertEquals("https://sawariyaseth.cc/item/590268", persisted!!.item.productUrl)
        assertEquals("590268", persisted.item.productRefId)
        assertEquals("RKPE0165", persisted.item.itemName)
        assertNull(persisted.item.category) // never invented
        assertEquals(434_000L, persisted.item.netWeightUg)
        assertEquals(InventoryStatus.AVAILABLE.code, persisted.item.status)

        // Test 2: Duplicate QR detection by URL variant or ref ID does not create duplicate
        val dupCheck = repository.findDuplicateInventoryItem("sawariyaseth.cc/item/590268")
        assertNotNull(dupCheck)
        assertEquals(savedId, dupCheck!!.id)

        val saveDup = repository.saveInventoryItem(
            productUrl = "http://www.sawariyaseth.cc/item/590268",
            productRefId = "590268",
            itemName = "Duplicate Attempt",
            category = "Rings",
            netWeightUg = 500_000L,
            otherDetails = "",
            imageUrl = null,
            notes = ""
        )
        assertTrue(saveDup is SaveInventoryResult.Duplicate)
        assertEquals(1, repository.observeAllInventory().first().size)
    }

    @Test
    fun `acceptance 3 to 9 - option A billing, option B single-record save, draft reservations, atomic sold transition, prevent rebilling`() = runBlocking {
        val customerId = repository.saveCustomer(
            CustomerEntity(
                shopName = "Mahalaxmi Jewellers",
                ownerName = "Suresh",
                phone = "9829012345",
                address = "Jaipur"
            )
        )

        // Pre-saved inventory items for Option A
        val inv1 = (repository.saveInventoryItem(
            productUrl = "https://sawariyaseth.cc/item/1001",
            productRefId = "1001",
            itemName = "Gold Ring A",
            category = null,
            netWeightUg = 1_200_000L,
            otherDetails = "",
            imageUrl = null,
            notes = ""
        ) as SaveInventoryResult.Saved).itemId

        val inv2 = (repository.saveInventoryItem(
            productUrl = "https://sawariyaseth.cc/item/1002",
            productRefId = "1002",
            itemName = "Gold Chain B",
            category = null,
            netWeightUg = 2_500_000L,
            otherDetails = "",
            imageUrl = null,
            notes = ""
        ) as SaveInventoryResult.Saved).itemId

        // Test 6: Draft reservation prevents simultaneous addition in another draft
        assertTrue(repository.reserveInventoryForDraft("draft-1", inv1))
        assertFalse(repository.reserveInventoryForDraft("draft-2", inv1))
        // Item remains AVAILABLE in DB while in draft (Test 5)
        assertEquals(InventoryStatus.AVAILABLE.code, repository.observeInventoryItem(inv1).first()!!.item.status)

        // Cancelling draft-1 releases inv1 back to Available (Test 6)
        repository.releaseAllDraftReservations("draft-1")
        assertTrue(repository.reserveInventoryForDraft("draft-2", inv1))
        repository.releaseAllDraftReservations("draft-2")

        // Test 4: Option B "Save to Inventory & Add to Bill" creates exactly one inventory record
        val inv3 = (repository.saveInventoryItem(
            productUrl = "https://sawariyaseth.cc/item/590268",
            productRefId = "590268",
            itemName = "RKPE0165",
            category = null,
            netWeightUg = 434_000L,
            otherDetails = "",
            imageUrl = null,
            notes = ""
        ) as SaveInventoryResult.Saved).itemId

        // Test 7: Finalize bill with inv1, inv2 (Option A) + inv3 (Option B) + 1 unsaved item (null inventoryItemId)
        assertTrue(repository.reserveInventoryForDraft("draft-final", inv1))
        assertTrue(repository.reserveInventoryForDraft("draft-final", inv2))
        assertTrue(repository.reserveInventoryForDraft("draft-final", inv3))

        val billId = repository.saveBillTransaction(
            customerId = customerId,
            billDateMillis = 1700000000000L,
            ratePerGramPaise = 1400000L,
            percentageBps = 7200,
            items = listOf(
                DraftBillItemInput(inventoryItemId = inv1, itemName = "Gold Ring A", productUrl = "https://sawariyaseth.cc/item/1001", netWeightUg = 1_200_000L, notes = ""),
                DraftBillItemInput(inventoryItemId = inv2, itemName = "Gold Chain B", productUrl = "https://sawariyaseth.cc/item/1002", netWeightUg = 2_500_000L, notes = ""),
                DraftBillItemInput(inventoryItemId = inv3, itemName = "RKPE0165", productUrl = "https://sawariyaseth.cc/item/590268", netWeightUg = 434_000L, notes = ""),
                DraftBillItemInput(inventoryItemId = null, itemName = "Loose Pendant", productUrl = "https://sawariyaseth.cc/item/9999", netWeightUg = 500_000L, notes = "")
            ),
            finalAmountPaise = 4670000L,
            amountReceivedPaise = 2000000L,
            paymentMethod = PaymentMethod.CASH,
            paymentDateMillis = 1700000000000L,
            paymentNotes = "",
            billNotes = "",
            draftSessionId = "draft-final"
        )

        // Reservation cleared on save
        assertTrue(repository.reservedInventory.value.isEmpty())
        // Exactly 3 inventory items exist (no duplicate from Option B, no record for unsaved item)
        assertEquals(3, repository.observeAllInventory().first().size)
        assertEquals(0, repository.observeAvailableInventory().first().size)

        // All 3 linked inventory items are SOLD with linkedBillId == billId
        for (id in listOf(inv1, inv2, inv3)) {
            val bundle = repository.observeInventoryItem(id).first()!!
            assertEquals(InventoryStatus.SOLD.code, bundle.item.status)
            assertEquals(billId, bundle.item.linkedBillId)
            assertNotNull(bundle.linkedBillNumber)
        }

        // Test 8: Prevent billing a Sold item again
        var threwSoldException = false
        try {
            repository.saveBillTransaction(
                customerId = customerId,
                billDateMillis = 1700000100000L,
                ratePerGramPaise = 1400000L,
                percentageBps = 7200,
                items = listOf(
                    DraftBillItemInput(inventoryItemId = inv1, itemName = "Gold Ring A", productUrl = "https://sawariyaseth.cc/item/1001", netWeightUg = 1_200_000L, notes = "")
                ),
                finalAmountPaise = 1200000L,
                amountReceivedPaise = 0L,
                paymentMethod = PaymentMethod.CASH,
                paymentDateMillis = 1700000100000L,
                paymentNotes = "",
                billNotes = ""
            )
        } catch (_: IllegalStateException) {
            threwSoldException = true
        }
        assertTrue(threwSoldException)

        // Test 12 (Backup & Restore includes inventory)
        val backupJson = repository.exportBackupJson()
        repository.restoreBackupJson(backupJson)
        assertEquals(3, repository.observeAllInventory().first().size)
    }

    @Test
    fun `acceptance 10 and 11 - sold item protection, authorized correction, and bill deletion restoration rules`() = runBlocking {
        val customerId = repository.saveCustomer(
            CustomerEntity(shopName = "Kalyan Gems", ownerName = "Amit", phone = "9998887776", address = "Surat")
        )
        val invId = (repository.saveInventoryItem(
            productUrl = "https://sawariyaseth.cc/item/555",
            productRefId = "555",
            itemName = "Bangle Pair",
            category = "Bangles",
            netWeightUg = 5_000_000L,
            otherDetails = "",
            imageUrl = null,
            notes = ""
        ) as SaveInventoryResult.Saved).itemId

        val bill1 = repository.saveBillTransaction(
            customerId = customerId,
            billDateMillis = 1700000000000L,
            ratePerGramPaise = 1400000L,
            percentageBps = 7200,
            items = listOf(DraftBillItemInput(inventoryItemId = invId, itemName = "Bangle Pair", productUrl = "https://sawariyaseth.cc/item/555", netWeightUg = 5_000_000L, notes = "")),
            finalAmountPaise = 5040000L,
            amountReceivedPaise = 0L,
            paymentMethod = PaymentMethod.CASH,
            paymentDateMillis = 1700000000000L,
            paymentNotes = "",
            billNotes = ""
        )

        // Direct update without authorizedSoldCorrection throws
        val soldEntity = repository.observeInventoryItem(invId).first()!!.item
        var blockedUnauthorizedEdit = false
        try {
            repository.updateInventoryItem(soldEntity.copy(notes = "Tampered"), authorizedSoldCorrection = false)
        } catch (_: IllegalStateException) {
            blockedUnauthorizedEdit = true
        }
        assertTrue(blockedUnauthorizedEdit)

        // Deleting bill WITHOUT restoration keeps item Sold (unlink billId via ON DELETE SET NULL)
        repository.deleteBillConfirmed(billId = bill1, restoreLinkedInventoryToAvailable = false)
        val afterDeleteNoRestore = repository.observeInventoryItem(invId).first()!!.item
        assertEquals(InventoryStatus.SOLD.code, afterDeleteNoRestore.status)
        assertNull(afterDeleteNoRestore.linkedBillId)

        // Authorized restoration returns item to Available
        repository.restoreSoldItemAuthorized(invId)
        assertEquals(InventoryStatus.AVAILABLE.code, repository.observeInventoryItem(invId).first()!!.item.status)

        // Bill again and delete WITH owner confirmation restoring to Available
        val bill2 = repository.saveBillTransaction(
            customerId = customerId,
            billDateMillis = 1700000200000L,
            ratePerGramPaise = 1400000L,
            percentageBps = 7200,
            items = listOf(DraftBillItemInput(inventoryItemId = invId, itemName = "Bangle Pair", productUrl = "https://sawariyaseth.cc/item/555", netWeightUg = 5_000_000L, notes = "")),
            finalAmountPaise = 5040000L,
            amountReceivedPaise = 0L,
            paymentMethod = PaymentMethod.CASH,
            paymentDateMillis = 1700000200000L,
            paymentNotes = "",
            billNotes = ""
        )
        assertEquals(1, repository.getLinkedInventoryCountForBill(bill2))
        repository.deleteBillConfirmed(billId = bill2, restoreLinkedInventoryToAvailable = true)
        val restored = repository.observeInventoryItem(invId).first()!!.item
        assertEquals(InventoryStatus.AVAILABLE.code, restored.status)
        assertNull(restored.linkedBillId)
    }
}
