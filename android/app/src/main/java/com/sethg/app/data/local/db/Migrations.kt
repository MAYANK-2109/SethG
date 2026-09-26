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

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
