package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CustomerEntity::class,
        BillEntity::class,
        BillItemEntity::class,
        InventoryItemEntity::class,
        PaymentEntity::class,
        SettingsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class JewelleryDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun billDao(): BillDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun paymentDao(): PaymentDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `inventory_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `product_url` TEXT NOT NULL,
                        `product_ref_id` TEXT,
                        `item_name` TEXT NOT NULL,
                        `category` TEXT,
                        `net_weight_ug` INTEGER NOT NULL,
                        `other_details` TEXT NOT NULL,
                        `image_url` TEXT,
                        `date_added` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `linked_bill_id` INTEGER,
                        `notes` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`linked_bill_id`) REFERENCES `bills`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_items_product_url` ON `inventory_items` (`product_url`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_items_product_ref_id` ON `inventory_items` (`product_ref_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_items_status` ON `inventory_items` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_items_date_added` ON `inventory_items` (`date_added`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_items_linked_bill_id` ON `inventory_items` (`linked_bill_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bill_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bill_id` INTEGER NOT NULL,
                        `inventory_item_id` INTEGER,
                        `item_name` TEXT NOT NULL,
                        `product_url` TEXT,
                        `net_weight_ug` INTEGER NOT NULL,
                        `calculated_amount_paise` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`bill_id`) REFERENCES `bills`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`inventory_item_id`) REFERENCES `inventory_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `bill_items_new` (`id`, `bill_id`, `inventory_item_id`, `item_name`, `product_url`, `net_weight_ug`, `calculated_amount_paise`, `notes`, `created_at`)
                    SELECT `id`, `bill_id`, NULL, `item_name`, `product_url`, `net_weight_ug`, `calculated_amount_paise`, `notes`, `created_at` FROM `bill_items`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `bill_items`")
                db.execSQL("ALTER TABLE `bill_items_new` RENAME TO `bill_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_items_bill_id` ON `bill_items` (`bill_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_items_inventory_item_id` ON `bill_items` (`inventory_item_id`)")
            }
        }

        fun create(context: Context): JewelleryDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                JewelleryDatabase::class.java,
                "jewellery_desk.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
