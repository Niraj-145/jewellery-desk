package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CustomerEntity
import com.example.data.local.JewelleryDatabase
import com.example.data.local.PaymentMethod
import com.example.data.remote.SawariyaSethProductService
import com.example.data.repository.DraftBillItemInput
import com.example.data.repository.JewelleryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BillingRepositoryRobolectricTest {

    private lateinit var database: JewelleryDatabase
    private lateinit var repository: JewelleryRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JewelleryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = JewelleryRepository(database, SawariyaSethProductService())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `complete billing workflow updates customer balance, ledger, and backup restore`() = runBlocking {
        // 1. Create retailer customer with ₹1,000 opening balance
        val customerId = repository.saveCustomer(
            CustomerEntity(
                shopName = "Shree Ganesh Jewellers",
                ownerName = "Ramesh Soni",
                phone = "9876543210",
                address = "Sarafa Bazaar",
                openingBalancePaise = 100000L // ₹1,000.00
            )
        )

        // 2. Save bill with 0.434g @ ₹14,000 @ 72% (calculated ₹4,374.72), adjusted final ₹4,370.00, paid ₹2,000.00
        val billId = repository.saveBillTransaction(
            customerId = customerId,
            billDateMillis = 1700000000000L,
            ratePerGramPaise = 1400000L,
            percentageBps = 7200,
            items = listOf(
                DraftBillItemInput(
                    itemName = "RKPE0165",
                    productUrl = "https://sawariyaseth.cc/item/590268",
                    netWeightUg = 434_000L,
                    notes = "Gold"
                )
            ),
            finalAmountPaise = 437000L,
            amountReceivedPaise = 200000L,
            paymentMethod = PaymentMethod.UPI,
            paymentDateMillis = 1700000000000L,
            paymentNotes = "Advance UPI",
            billNotes = "Rounded final amount"
        )

        val detail = repository.observeBillDetail(billId).first()!!
        assertEquals(437472L, detail.bill.calculatedAmountPaise)
        assertEquals(437000L, detail.bill.finalAmountPaise)
        assertEquals(200000L, detail.bill.amountReceivedPaise)
        assertEquals(237000L, detail.bill.remainingBalancePaise)
        assertEquals("PARTIALLY_PAID", detail.bill.paymentStatus)

        // Customer outstanding: Opening ₹1,000 + Bill ₹4,370 - Paid ₹2,000 = ₹3,370.00 (337,000 paise)
        val statsAfterBill = repository.observeCustomerWithStats(customerId).first()!!
        assertEquals(337000L, statsAfterBill.outstandingPaise)

        // 3. Record remaining payment ₹2,370.00 against bill
        repository.recordPaymentTransaction(
            customerId = customerId,
            billId = billId,
            amountPaise = 237000L,
            paymentMethod = PaymentMethod.CASH,
            paymentDateMillis = 1700000100000L,
            notes = "Final settlement"
        )

        val detailAfterPay = repository.observeBillDetail(billId).first()!!
        assertEquals("PAID", detailAfterPay.bill.paymentStatus)
        assertEquals(0L, detailAfterPay.bill.remainingBalancePaise)

        val statsAfterPay = repository.observeCustomerWithStats(customerId).first()!!
        assertEquals(100000L, statsAfterPay.outstandingPaise) // Only opening ₹1,000 remains

        // 4. Verify Ledger chronological running balances (opening + bill - pay1 - pay2)
        val ledger = repository.observeCustomerLedger(customerId).first()
        assertEquals(4, ledger.size)
        assertEquals(100000L, ledger.first().runningBalancePaise) // Most recent entry balance

        // 5. Verify Backup & Restore roundtrip
        val backupJson = repository.exportBackupJson()
        repository.restoreBackupJson(backupJson)
        val restoredStats = repository.observeCustomerWithStats(customerId).first()!!
        assertEquals("Shree Ganesh Jewellers", restoredStats.customer.shopName)
        assertEquals(100000L, restoredStats.outstandingPaise)
    }
}
