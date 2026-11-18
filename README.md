
**Jewellery Desk** is an offline-first wholesale jewellery billing, QR code scanning, customer ledger, and payment management Android application built with Kotlin, Jetpack Compose, Material 3, Room (SQLite), CameraX, ML Kit Barcode Scanning, and OkHttp/Jsoup.

> Note: Built natively for Android (Kotlin + Jetpack Compose) to match the active Android Studio/Gradle build environment.

---

## Project Structure

- `app/src/main/java/com/example/`
  - `JewelleryDeskApp.kt`: Application entry point & manual constructor-injection `AppContainer`.
  - `MainActivity.kt`: Single-activity host with `enableEdgeToEdge()`, adaptive `NavigationBar` (mobile) / `NavigationRail` (tablet), and type-safe Navigation Compose routing.
  - `domain/`
    - `FinancialMath.kt`: Decimal-safe `BigDecimal` + integer paise/microgram calculations and Indian Rupee (`₹`) formatting.
    - `DateUtils.kt`: Date formatting and report period boundaries.
    - `BackupModels.kt`: `@Serializable` JSON backup/restore payload models.
  - `data/`
    - `local/Entities.kt`: Relational Room entities (`customers`, `bills`, `bill_items`, `payments`, `settings`) with foreign keys & indexes.
    - `local/Daos.kt`: Reactive `Flow` queries, correlated balance aggregations, and snapshot export queries.
    - `local/JewelleryDatabase.kt`: Room SQLite database.
    - `remote/SawariyaSethProductService.kt`: Isolated URL validator, OkHttp fetcher, and Jsoup HTML parser for `https://sawariyaseth.cc/item/...`.
    - `pdf/ReceiptPdfGenerator.kt`: Native `PdfDocument` A4 receipt generator, `FileProvider` share sheet (including WhatsApp), and `PrintManager` print adapter.
    - `repository/JewelleryRepository.kt`: Transactional billing, immutable payment recording, chronological customer ledger builder, and JSON backup/restore.
  - `ui/theme/`: Deep Burgundy (`#5B1726`), Antique Gold (`#B89555`), Warm Ivory (`#F8F6F1`), and bundled `Inter` + `Playfair Display` typography.
  - `ui/components/`: Reusable Material 3 cards, KPI tiles, dialogs, and CameraX + ML Kit `QrScannerModal`.
  - `ui/screens/`: All 8 modules (`Dashboard`, `New Bill`, `Inventory`, `Customers` + `Customer Ledger`, `Bills History` + `Bill Receipt`, `Payments`, `Reports`, `Settings`).

---

## Dependencies

- Jetpack Compose (Material 3, Navigation Compose)
- Room (`room-runtime`, `room-ktx`, KSP `room-compiler`)
- CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`)
- ML Kit Barcode Scanning (`com.google.mlkit:barcode-scanning`)
- OkHttp + Jsoup (`org.jsoup:jsoup`) for Sawariya Seth product page retrieval
- `kotlinx-serialization-json` for type-safe navigation & JSON backup/restore
- JUnit + Robolectric + Coroutines Test for local verification

---

## Installation & Run Commands

1. Ensure Android SDK (`ANDROID_SDK_ROOT`) and JDK 17+ are configured.
2. Build debug APK:
   ```bash
   gradle :app:assembleDebug
   ```
3. Run unit and Robolectric tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```

---

## Database Design (SQLite / Room)

All monetary amounts are stored as **integer paise** (`Long`, `₹1.00 = 100 paise`) and all weights as **integer micrograms** (`Long`, `1 g = 1,000,000 µg`) to prevent floating-point rounding drift.

- **`customers`**: `id`, `shop_name`, `owner_name`, `phone`, `address`, `gst_number`, `opening_balance_paise`, `notes`, `created_at` (indexed on `shop_name`, `phone`).
- **`inventory_items`**: `id`, `product_url` (unique), `product_ref_id` (unique), `item_name`, `category` (nullable), `net_weight_ug`, `other_details`, `image_url`, `date_added`, `status` (`AVAILABLE` / `SOLD`), `linked_bill_id` (FK -> `bills.id` ON DELETE SET NULL), `notes`, `created_at`.
- **`bills`**: `id`, `bill_number` (unique), `customer_id` (FK -> `customers.id`), `bill_date`, `rate_per_gram_paise`, `percentage_bps`, `total_weight_ug`, `calculated_amount_paise`, `final_amount_paise`, `amount_received_paise`, `remaining_balance_paise`, `payment_status`, `notes`, `created_at`.
- **`bill_items`**: `id`, `bill_id` (FK -> `bills.id` ON DELETE CASCADE), `inventory_item_id` (nullable FK -> `inventory_items.id` ON DELETE SET NULL), `item_name`, `product_url`, `net_weight_ug`, `calculated_amount_paise`, `notes`, `created_at`.
- **`payments`**: `id`, `customer_id` (FK -> `customers.id`), `bill_id` (FK -> `bills.id` ON DELETE SET NULL), `amount_paise`, `payment_method`, `payment_date`, `notes`, `created_at`.
- **`settings`**: singleton business profile, default rate/percentage, currency symbol, and invoice footer.

---

## Configuration & Sawariya Seth QR Retrieval

- Open **Settings** to configure Business Name, Address, Phone, Optional GSTIN, Default Rate per Gram, Default Percentage, and Invoice Footer.
- `SawariyaSethProductService` validates URLs against `sawariyaseth.cc`, checks HTML `#productdetails` and `#pricebreakup` tables for Net Weight and SKU/title, and never invents missing weights.
- **Offline / Fallback behavior**: If internet is unavailable or a product page omits net weight, the app opens a pre-filled **Manual Item Entry** form.

---

## Testing Instructions

Run the complete calculation, Sawariya Seth HTML parser, and transactional database/ledger test suite:

```bash
gradle :app:testDebugUnitTest
```

---

## Known Limitations

- `sawariyaseth.cc` product retrieval requires internet connectivity and depends on the supplier's public HTML structure (`#pricebreakup` / `#productdetails`). Changes to their website markup fall back gracefully to manual entry.
- Cloud synchronization (e.g., Supabase) is intentionally omitted in this offline-first v1 release; use **Settings -> Export JSON / Restore JSON** for portable backups.
