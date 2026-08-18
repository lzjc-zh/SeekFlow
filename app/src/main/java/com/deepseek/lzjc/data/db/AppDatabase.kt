package com.deepseek.lzjc.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UsageEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao

    companion object {
        /** v1 -> v2：usage_records 增加 providerId 列并建索引 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE usage_records ADD COLUMN providerId TEXT NOT NULL DEFAULT 'deepseek_default'"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_usage_records_providerId_date_model " +
                        "ON usage_records(providerId, date, model)"
                )
            }
        }
    }
}
