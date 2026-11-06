package com.mattxor.timesheet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Project::class, TimeEntry::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TimesheetDao

    companion object {
        const val NAME = "timesheet.db"

        /** Adds manual entries and start-time edit tracking. Existing data is kept. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE time_entries ADD COLUMN manual INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE time_entries ADD COLUMN durationMillis INTEGER")
                db.execSQL("ALTER TABLE time_entries ADD COLUMN createdAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE time_entries ADD COLUMN originalStartMillis INTEGER")
                db.execSQL("ALTER TABLE time_entries ADD COLUMN startEditedAtMillis INTEGER")
                db.execSQL("UPDATE time_entries SET createdAtMillis = startMillis")
            }
        }

        /** Adds tracking for edited stop times, durations and projects. Existing data is kept. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE time_entries ADD COLUMN originalEndMillis INTEGER")
                db.execSQL("ALTER TABLE time_entries ADD COLUMN originalDurationMillis INTEGER")
                db.execSQL("ALTER TABLE time_entries ADD COLUMN originalProjectId INTEGER")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
