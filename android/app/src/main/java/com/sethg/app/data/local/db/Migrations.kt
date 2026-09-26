package com.sethg.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ── Room migrations ───────────────────────────────────────────────────────────
// Lots live only on the phone until they sync, so a schema change must NEVER
// wipe the database. Every version bump needs a Migration here, written from
// the exported schemas in app/schemas/ (commit those JSON files).

/** v1 → v2: add lots + lot_photos (v1 only had the cache tables). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `lots` (`lotId` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                "`weightKg` REAL NOT NULL, `estimateLow` INTEGER NOT NULL, `estimateHigh` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`lotId`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `lot_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`lotId` TEXT NOT NULL, `filePath` TEXT NOT NULL, `sha256` TEXT NOT NULL, " +
                "`capturedAt` INTEGER NOT NULL, `nonce` TEXT NOT NULL, `payload` TEXT NOT NULL, " +
                "`signature` TEXT NOT NULL, `aiVerdict` TEXT NOT NULL, `aiLabel` TEXT, " +
                "FOREIGN KEY(`lotId`) REFERENCES `lots`(`lotId`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lot_photos_lotId` ON `lot_photos` (`lotId`)")
    }
}

/** v2 → v3: remember which city/zone's rates priced each lot. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // An older build (DB v1) can reset the version number but leaves our lot tables
        // untouched, so on the way back up the column may already exist.
        if (!db.hasColumn("lots", "priceRegion")) {
            db.execSQL("ALTER TABLE `lots` ADD COLUMN `priceRegion` TEXT")
        }
    }
}

private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
            .any { it == column }
    }

/** v3 → v4: add role, certificateUrl, isVerified to cached_user. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `cached_user` ADD COLUMN `role` TEXT NOT NULL DEFAULT 'user'")
        db.execSQL("ALTER TABLE `cached_user` ADD COLUMN `certificateUrl` TEXT")
        db.execSQL("ALTER TABLE `cached_user` ADD COLUMN `isVerified` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v4 → v5: lot location (recycler matching) and the handover code. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("lots", "lat")) db.execSQL("ALTER TABLE `lots` ADD COLUMN `lat` REAL")
        if (!db.hasColumn("lots", "lon")) db.execSQL("ALTER TABLE `lots` ADD COLUMN `lon` REAL")
        if (!db.hasColumn("lots", "handoverOtp")) db.execSQL("ALTER TABLE `lots` ADD COLUMN `handoverOtp` TEXT")
    }
}

/** v5 → v6: vendor transaction ledger and recycler purchase ledger. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vendor_transactions` (" +
                "`txnId` TEXT NOT NULL, " +
                "`customerName` TEXT NOT NULL, " +
                "`photoPath` TEXT, " +
                "`category` TEXT NOT NULL, " +
                "`weightKg` REAL NOT NULL, " +
                "`quantity` INTEGER NOT NULL, " +
                "`grade` TEXT NOT NULL, " +
                "`isRunnable` INTEGER NOT NULL, " +
                "`isWorking` INTEGER NOT NULL, " +
                "`estimateLow` INTEGER NOT NULL, " +
                "`estimateHigh` INTEGER NOT NULL, " +
                "`finalPrice` REAL NOT NULL, " +
                "`vendorLat` REAL, " +
                "`vendorLon` REAL, " +
                "`isPaid` INTEGER NOT NULL DEFAULT 0, " +
                "`createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`txnId`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recycler_purchases` (" +
                "`purchaseId` TEXT NOT NULL, " +
                "`vendorName` TEXT NOT NULL, " +
                "`vendorPhone` TEXT, " +
                "`vendorLat` REAL, " +
                "`vendorLon` REAL, " +
                "`material` TEXT NOT NULL, " +
                "`weightKg` REAL NOT NULL, " +
                "`quantity` INTEGER NOT NULL, " +
                "`grade` TEXT NOT NULL, " +
                "`amountPaid` REAL NOT NULL, " +
                "`notes` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`purchaseId`))"
        )
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
