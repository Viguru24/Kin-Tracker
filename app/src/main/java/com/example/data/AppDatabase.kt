package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FamilyMember::class, ActivityLog::class, GroupPinMapping::class, SafeZone::class, ShoppingItem::class, LocationBreadcrumb::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun familyDao(): FamilyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE family_members ADD COLUMN isLocationPaused INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "family_tracker_db"
                )
                .addMigrations(MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
