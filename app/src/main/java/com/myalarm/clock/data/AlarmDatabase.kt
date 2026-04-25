package com.myalarm.clock.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Alarm::class, AlarmGroup::class],
    version = 2,
    exportSchema = false
)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun alarmGroupDao(): AlarmGroupDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alarm_groups` (" +
                        "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "`name` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL DEFAULT 1, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE alarms ADD COLUMN groupId INTEGER")
                db.execSQL("ALTER TABLE alarms ADD COLUMN volume INTEGER NOT NULL DEFAULT 7")
                db.execSQL(
                    "ALTER TABLE alarms ADD COLUMN vibrationPattern TEXT NOT NULL DEFAULT 'basic'"
                )
            }
        }
    }
}
