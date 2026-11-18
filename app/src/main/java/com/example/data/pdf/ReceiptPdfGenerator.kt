package com.example.data.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.local.BillDetailBundle
import com.example.data.local.PaymentMethod
import com.example.data.local.PaymentStatus
import com.example.data.local.SettingsEntity
import com.example.domain.DateUtils
import com.example.domain.FinancialMath
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ReceiptPdfGenerator {

    private const val PAGE_WIDTH = 595 // A4 points (72 dpi)
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    fun generateBillPdfFile(
        context: Context,
        bundle: BillDetailBundle,
        settings: SettingsEntity
    ): File {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val safeName = bundle.bill.billNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "$safeName.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        drawReceipt(page.canvas, bundle, settings)
        document.finishPage(page)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    fun sharePdf(context: Context, pdfFile: File, billNumber: String, packageNameFilter: String? = null) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Wholesale Bill $billNumber")
            putExtra(Intent.EXTRA_TEXT, "Please find attached jewellery wholesale bill $billNumber.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (packageNameFilter != null) {
                setPackage(packageNameFilter)
            }
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Share Bill $billNumber"))
        } catch (_: Exception) {
            if (packageNameFilter != null) {
                sharePdf(context, pdfFile, billNumber, null)
            }
        }
    }

    fun printBillPdf(context: Context, pdfFile: File, billNumber: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$billNumber.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }
        printManager.print("Bill_$billNumber", adapter, null)
    }

    private fun drawReceipt(
        canvas: Canvas,
        bundle: BillDetailBundle,
        settings: SettingsEntity
    ) {
        val burgundy = Color.rgb(91, 23, 38)
        val gold = Color.rgb(184, 149, 85)
        val charcoal = Color.rgb(41, 39, 37)
        val muted = Color.rgb(119, 115, 110)
        val ivoryHeader = Color.rgb(248, 246, 241)
        val border = Color.rgb(231, 225, 216)

        val headerBgPaint = Paint().apply { color = ivoryHeader }
        val borderPaint = Paint().apply {
            color = border
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val goldLinePaint = Paint().apply {
            color = gold
            strokeWidth = 2f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = burgundy
            textSize = 20f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 9.5f
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = burgundy
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = charcoal
            textSize = 10f
        }
        val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = charcoal
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rightPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightBoldPaint = Paint(bodyBoldPaint).apply { textAlign = Paint.Align.RIGHT }

        var y = MARGIN
        val contentWidth = PAGE_WIDTH - 2 * MARGIN

        // Header Banner
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 76f, headerBgPaint)
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 76f, borderPaint)
        canvas.drawText(settings.businessName.ifBlank { "Jewellery Desk Wholesale" }, MARGIN + 14f, y + 26f, titlePaint)
        if (settings.businessAddress.isNotBlank()) {
            canvas.drawText(settings.businessAddress, MARGIN + 14f, y + 43f, subtitlePaint)
        }
        val phoneGst = buildString {
            if (settings.businessPhone.isNotBlank()) append("Mob: ${settings.businessPhone}")
            if (settings.gstNumber.isNotBlank()) {
                if (isNotEmpty()) append("   |   ")
                append("GSTIN: ${settings.gstNumber}")
            }
        }
        if (phoneGst.isNotBlank()) {
            canvas.drawText(phoneGst, MARGIN + 14f, y + 58f, subtitlePaint)
        }

        val invoiceTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = burgundy
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("WHOLESALE BILL", PAGE_WIDTH - MARGIN - 14f, y + 26f, invoiceTagPaint)
        canvas.drawText(bundle.bill.billNumber, PAGE_WIDTH - MARGIN - 14f, y + 43f, rightBoldPaint)
        canvas.drawText("Date: ${DateUtils.formatDate(bundle.bill.billDate)}", PAGE_WIDTH - MARGIN - 14f, y + 58f, rightPaint)

        y += 88f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, goldLinePaint)
        y += 18f

        // Retailer & Calculation Metadata Two-Column Block
        canvas.drawText("BILLED TO RETAILER", MARGIN, y, sectionTitlePaint)
        canvas.drawText("BILL PARAMETERS", MARGIN + contentWidth * 0.56f, y, sectionTitlePaint)
        y += 15f
        canvas.drawText(bundle.customer.shopName, MARGIN, y, bodyBoldPaint)
        val symbol = settings.currencySymbol.ifBlank { "₹" }
        canvas.drawText("Today's Rate: ${FinancialMath.formatInr(bundle.bill.ratePerGramPaise, symbol)} / g", MARGIN + contentWidth * 0.56f, y, bodyPaint)
        y += 14f
        canvas.drawText("Owner: ${bundle.customer.ownerName}", MARGIN, y, bodyPaint)
        canvas.drawText("Percentage: ${FinancialMath.formatPercentage(bundle.bill.percentageBps)}", MARGIN + contentWidth * 0.56f, y, bodyPaint)
        y += 14f
        canvas.drawText("Mobile: ${bundle.customer.phone}", MARGIN, y, bodyPaint)
        canvas.drawText("Total Net Wt: ${FinancialMath.formatWeightGrams(bundle.bill.totalWeightUg)}", MARGIN + contentWidth * 0.56f, y, bodyPaint)
        y += 14f
        if (bundle.customer.address.isNotBlank()) {
            canvas.drawText(bundle.customer.address.take(46), MARGIN, y, subtitlePaint)
        }
        val statusLabel = PaymentStatus.fromCode(bundle.bill.paymentStatus).label
        canvas.drawText("Status: $statusLabel", MARGIN + contentWidth * 0.56f, y, bodyBoldPaint)
        y += 22f

        // Items Table Header
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 24f, headerBgPaint)
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 24f, borderPaint)
        val col1 = MARGIN + 8f
        val col2 = MARGIN + 34f
        val col3 = MARGIN + 290f
        val col4 = MARGIN + 385f
        val col5 = PAGE_WIDTH - MARGIN - 8f

        canvas.drawText("#", col1, y + 16f, sectionTitlePaint)
        canvas.drawText("ITEM DESCRIPTION", col2, y + 16f, sectionTitlePaint)
        canvas.drawText("NET WT", col3, y + 16f, sectionTitlePaint)
        canvas.drawText("RATE × %", col4, y + 16f, sectionTitlePaint)
        val rightHeaderPaint = Paint(sectionTitlePaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("AMOUNT", col5, y + 16f, rightHeaderPaint)
        y += 24f

        val ratePctShort = "${FinancialMath.formatInr(bundle.bill.ratePerGramPaise, symbol, false)} × ${FinancialMath.formatPercentage(bundle.bill.percentageBps)}"
        bundle.items.forEachIndexed { index, item ->
            if (y > PAGE_HEIGHT - 190f) return@forEachIndexed
            canvas.drawText("${index + 1}", col1, y + 16f, bodyPaint)
            canvas.drawText(item.itemName.take(36), col2, y + 16f, bodyBoldPaint)
            canvas.drawText(FinancialMath.formatWeightGrams(item.netWeightUg), col3, y + 16f, bodyPaint)
            canvas.drawText(ratePctShort, col4, y + 16f, subtitlePaint)
            canvas.drawText(FinancialMath.formatInr(item.calculatedAmountPaise, symbol), col5, y + 16f, rightBoldPaint)
            y += 22f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, borderPaint)
        }

        y += 18f
        // Summary Totals Box (Right aligned)
        val summaryLeft = PAGE_WIDTH - MARGIN - 235f
        canvas.drawRect(summaryLeft, y, PAGE_WIDTH - MARGIN, y + 108f, headerBgPaint)
        canvas.drawRect(summaryLeft, y, PAGE_WIDTH - MARGIN, y + 108f, borderPaint)

        var sy = y + 20f
        canvas.drawText("Calculated Amount", summaryLeft + 12f, sy, bodyPaint)
        canvas.drawText(FinancialMath.formatInr(bundle.bill.calculatedAmountPaise, symbol), PAGE_WIDTH - MARGIN - 12f, sy, rightPaint)

        sy += 19f
        canvas.drawText("Final Bill Amount", summaryLeft + 12f, sy, sectionTitlePaint)
        canvas.drawText(FinancialMath.formatInr(bundle.bill.finalAmountPaise, symbol), PAGE_WIDTH - MARGIN - 12f, sy, rightBoldPaint)

        sy += 19f
        canvas.drawText("Amount Received", summaryLeft + 12f, sy, bodyPaint)
        canvas.drawText(FinancialMath.formatInr(bundle.bill.amountReceivedPaise, symbol), PAGE_WIDTH - MARGIN - 12f, sy, rightPaint)

        sy += 12f
        canvas.drawLine(summaryLeft + 10f, sy, PAGE_WIDTH - MARGIN - 10f, sy, borderPaint)
        sy += 18f
        canvas.drawText("Remaining Balance", summaryLeft + 12f, sy, bodyBoldPaint)
        canvas.drawText(FinancialMath.formatInr(bundle.bill.remainingBalancePaise, symbol), PAGE_WIDTH - MARGIN - 12f, sy, rightBoldPaint)

        // Payment breakdown on left if present
        if (bundle.payments.isNotEmpty()) {
            var py = y + 14f
            canvas.drawText("PAYMENT RECORDS", MARGIN, py, sectionTitlePaint)
            py += 16f
            bundle.payments.take(4).forEach { p ->
                val line = "${DateUtils.formatShortDate(p.paymentDate)} • ${PaymentMethod.fromCode(p.paymentMethod).label}: ${FinancialMath.formatInr(p.amountPaise, symbol)}"
                canvas.drawText(line, MARGIN, py, bodyPaint)
                py += 14f
            }
        }

        // Footer
        val footerY = PAGE_HEIGHT - MARGIN - 24f
        canvas.drawLine(MARGIN, footerY - 12f, PAGE_WIDTH - MARGIN, footerY - 12f, goldLinePaint)
        canvas.drawText(settings.invoiceFooter.take(90), MARGIN, footerY + 4f, subtitlePaint)
    }
}
