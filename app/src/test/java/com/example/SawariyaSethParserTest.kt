package com.example

import com.example.data.remote.SawariyaFetchResult
import com.example.data.remote.SawariyaSethProductService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SawariyaSethParserTest {

    private val service = SawariyaSethProductService()

    @Test
    fun `validates allowed sawariyaseth domain and rejects external links`() {
        assertEquals(
            "https://sawariyaseth.cc/item/590268",
            service.normalizeAndValidateUrl("https://sawariyaseth.cc/item/590268")
        )
        assertEquals(
            "https://sawariyaseth.cc/item/590268",
            service.normalizeAndValidateUrl("sawariyaseth.cc/item/590268")
        )
        assertNull(service.normalizeAndValidateUrl("https://example.com/item/590268"))
        assertNull(service.normalizeAndValidateUrl("not a url"))
    }

    @Test
    fun `parses RKPE0165 product title and 0_434g net weight from real HTML structure`() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>RKPE0165 | Sawariya Seth Jewellery</title></head>
            <body>
              <div class="shop-product-single-title"><h2 class="title fs_24 mb-2">RKPE0165</h2></div>
              <div id="sync1"><div class="item"><img src="https://rikojs.com/storage/items/test.png"/></div></div>
              <div id="pricebreakup">
                <table class="table">
                  <thead><tr><th>Component</th><th>Rate</th><th>Weight</th><th>Final Value</th></tr></thead>
                  <tbody>
                    <tr><td class="fw_6">_ Gold</td><td>9581</td><td>0.434 g</td><td>₹4,158.15</td></tr>
                    <tr><td>GST</td><td>-</td><td>-</td><td>₹124.84</td></tr>
                  </tbody>
                </table>
              </div>
            </body>
            </html>
        """.trimIndent()

        val result = service.parseHtml("https://sawariyaseth.cc/item/590268", sampleHtml)
        assertTrue(result is SawariyaFetchResult.Success)
        val success = result as SawariyaFetchResult.Success
        assertEquals("RKPE0165", success.product.itemName)
        assertEquals(434_000L, success.product.netWeightUg)
        assertEquals("https://rikojs.com/storage/items/test.png", success.product.imageUrl)
    }

    @Test
    fun `missing weight triggers graceful fallback without inventing weight`() {
        val noWeightHtml = """
            <html>
            <head><title>MISSING_WT | Sawariya Seth Jewellery</title></head>
            <body><div class="shop-product-single-title"><h2 class="title">MISSING_WT</h2></div></body>
            </html>
        """.trimIndent()

        val result = service.parseHtml("https://sawariyaseth.cc/item/999", noWeightHtml)
        assertTrue(result is SawariyaFetchResult.PartialOrFailure)
        val failure = result as SawariyaFetchResult.PartialOrFailure
        assertEquals("MISSING_WT", failure.suggestedName)
    }
}
