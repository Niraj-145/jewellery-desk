package com.example.data.remote

import com.example.domain.FinancialMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

data class SawariyaProductData(
    val productUrl: String,
    val productRefId: String?,
    val itemName: String,
    val category: String?,
    val netWeightUg: Long,
    val netWeightDisplay: String,
    val imageUrl: String? = null,
    val otherDetails: String = "",
    val notes: String = ""
)

sealed interface SawariyaFetchResult {
    data class Success(val product: SawariyaProductData) : SawariyaFetchResult
    data class PartialOrFailure(
        val normalizedUrl: String,
        val productRefId: String? = null,
        val suggestedName: String = "",
        val category: String? = null,
        val imageUrl: String? = null,
        val otherDetails: String = "",
        val reason: String
    ) : SawariyaFetchResult
}

/**
 * Isolated product retrieval service for https://sawariyaseth.cc/ QR URLs.
 * Validates domain, extracts product reference ID, retrieves HTML cleanly,
 * parses SKU/title, category (when present), net weight, and source details,
 * and falls back gracefully without inventing missing fields.
 */
class SawariyaSethProductService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    companion object {
        private val ALLOWED_HOSTS = setOf("sawariyaseth.cc", "www.sawariyaseth.cc")
        private val WEIGHT_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(?:g|gm|gms|gram|grams)\b""", RegexOption.IGNORE_CASE)
        private val ITEM_ID_REGEX = Regex("""/item/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    }

    fun normalizeAndValidateUrl(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
            trimmed.startsWith("sawariyaseth.cc", ignoreCase = true) ||
                trimmed.startsWith("www.sawariyaseth.cc", ignoreCase = true) -> "https://$trimmed"
            else -> return null
        }

        return try {
            val uri = java.net.URI(withScheme)
            val host = uri.host?.lowercase() ?: return null
            if (host !in ALLOWED_HOSTS) return null
            if (uri.path.isNullOrBlank() || uri.path == "/") return null
            // Strip trailing slash and fragment for canonical duplicate matching
            val cleanPath = uri.path.trimEnd('/')
            val cleanUri = java.net.URI(
                "https",
                "sawariyaseth.cc",
                cleanPath,
                uri.query,
                null
            )
            cleanUri.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun extractProductRefId(url: String): String? {
        val match = ITEM_ID_REGEX.find(url)
        if (match != null) return match.groupValues[1]
        return try {
            val uri = java.net.URI(url)
            val lastSeg = uri.path?.trimEnd('/')?.substringAfterLast('/')
            lastSeg?.takeIf { it.isNotBlank() && it != "item" }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchProduct(rawUrl: String): SawariyaFetchResult = withContext(Dispatchers.IO) {
        val validUrl = normalizeAndValidateUrl(rawUrl)
            ?: return@withContext SawariyaFetchResult.PartialOrFailure(
                normalizedUrl = rawUrl.trim(),
                reason = "Invalid QR URL. Expected a https://sawariyaseth.cc/item/... link."
            )
        val refId = extractProductRefId(validUrl)

        try {
            val request = Request.Builder()
                .url(validUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; JewelleryDesk/1.0)")
                .header("Accept", "text/html,application/xhtml+xml")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext SawariyaFetchResult.PartialOrFailure(
                        normalizedUrl = validUrl,
                        productRefId = refId,
                        reason = "Website returned HTTP ${response.code}. Enter item details manually."
                    )
                }
                val html = response.body?.string().orEmpty()
                if (html.isBlank()) {
                    return@withContext SawariyaFetchResult.PartialOrFailure(
                        normalizedUrl = validUrl,
                        productRefId = refId,
                        reason = "Product page returned empty content. Enter details manually."
                    )
                }
                parseHtml(validUrl, html)
            }
        } catch (e: Exception) {
            SawariyaFetchResult.PartialOrFailure(
                normalizedUrl = validUrl,
                productRefId = refId,
                reason = "Could not reach sawariyaseth.cc (${e.localizedMessage ?: "network error"}). Use manual entry."
            )
        }
    }

    fun parseHtml(productUrl: String, html: String): SawariyaFetchResult {
        val doc = Jsoup.parse(html, productUrl)
        val refId = extractProductRefId(productUrl)

        val h2Title = doc.selectFirst(".shop-product-single-title h2.title, h2.title, h1.title, h1")
            ?.text()?.trim().orEmpty()
        val pageTitle = doc.title().substringBefore("|").trim()
        val itemName = h2Title.ifBlank { pageTitle }

        val imageUrl = doc.selectFirst("#sync1 .item img, #sync1 .item a, meta[property=og:image]")
            ?.let { elem ->
                when (elem.tagName().lowercase()) {
                    "meta" -> elem.attr("content")
                    "a" -> elem.attr("abs:href").ifBlank { elem.attr("href") }
                    else -> elem.attr("abs:src").ifBlank { elem.attr("src") }
                }
            }?.takeIf { it.isNotBlank() }

        // Extract optional category or product details if present (never invent)
        var detectedCategory: String? = null
        val otherDetailParts = mutableListOf<String>()
        val detailRows = doc.select("#productdetails tr, #productdetails li, .product-details tr, .breadcrumb li")
        var netWeightStr: String? = null

        for (row in detailRows) {
            val rowText = row.text().trim()
            if (rowText.isBlank()) continue
            if (row.hasClass("breadcrumb-item") || row.parent()?.hasClass("breadcrumb") == true) {
                if (!rowText.equals("Home", ignoreCase = true) && !rowText.equals(itemName, ignoreCase = true)) {
                    detectedCategory = rowText
                }
                continue
            }
            if (rowText.contains("category", ignoreCase = true)) {
                val value = rowText.substringAfter(":", "").trim().ifBlank {
                    row.select("td").getOrNull(1)?.text()?.trim().orEmpty()
                }
                if (value.isNotBlank()) detectedCategory = value
            }
            if (netWeightStr == null && rowText.contains("net", ignoreCase = true) && rowText.contains("weight", ignoreCase = true)) {
                val match = WEIGHT_REGEX.find(rowText)
                if (match != null) {
                    netWeightStr = match.groupValues[1]
                }
            }
            if (row.parents().any { it.id() == "productdetails" } && rowText.length <= 120) {
                otherDetailParts.add(rowText)
            }
        }

        // Check #pricebreakup table rows (e.g., Component: "_ Gold", Rate: "9581", Weight: "0.434 g")
        val componentNotes = mutableListOf<String>()
        val breakupRows = doc.select("#pricebreakup table tbody tr, table.table tbody tr")
        for (row in breakupRows) {
            val cells = row.select("td, th")
            if (cells.size >= 3) {
                val component = cells[0].text().trim().trimStart('_', ' ')
                val weightCell = cells[2].text().trim()
                val valueCell = cells.getOrNull(3)?.text()?.trim().orEmpty()
                if (component.isNotBlank() && !component.equals("Total", ignoreCase = true)) {
                    otherDetailParts.add(
                        listOf(component, weightCell, valueCell)
                            .filter { it.isNotBlank() && it != "-" }
                            .joinToString(": ")
                    )
                }
                val isGoldOrNet = component.contains("gold", ignoreCase = true) ||
                    component.contains("net", ignoreCase = true) ||
                    component.contains("metal", ignoreCase = true)
                if (netWeightStr == null && isGoldOrNet) {
                    val match = WEIGHT_REGEX.find(weightCell)
                    if (match != null) {
                        netWeightStr = match.groupValues[1]
                        componentNotes.add("$component ($weightCell)")
                    }
                }
            }
        }

        // Last resort: check any weight cell in price breakup excluding stone/diamond/gross
        if (netWeightStr == null) {
            for (row in doc.select("#pricebreakup table tbody tr")) {
                val text = row.text()
                if (!text.contains("stone", ignoreCase = true) &&
                    !text.contains("diamond", ignoreCase = true) &&
                    !text.contains("gross", ignoreCase = true)
                ) {
                    val match = WEIGHT_REGEX.find(text)
                    if (match != null) {
                        netWeightStr = match.groupValues[1]
                        break
                    }
                }
            }
        }

        val otherDetailsStr = otherDetailParts.distinct().joinToString(" | ")
        val parsedUg = netWeightStr?.let { FinancialMath.parseGramsToUg(it) }
        if (itemName.isBlank() || parsedUg == null || parsedUg <= 0L) {
            return SawariyaFetchResult.PartialOrFailure(
                normalizedUrl = productUrl,
                productRefId = refId,
                suggestedName = itemName,
                category = detectedCategory,
                imageUrl = imageUrl,
                otherDetails = otherDetailsStr,
                reason = "Net weight could not be detected on the product page. Please verify and enter manually."
            )
        }

        return SawariyaFetchResult.Success(
            SawariyaProductData(
                productUrl = productUrl,
                productRefId = refId,
                itemName = itemName,
                category = detectedCategory,
                netWeightUg = parsedUg,
                netWeightDisplay = FinancialMath.ugToPlainGramsString(parsedUg),
                imageUrl = imageUrl,
                otherDetails = otherDetailsStr,
                notes = componentNotes.joinToString(", ")
            )
        )
    }
}
