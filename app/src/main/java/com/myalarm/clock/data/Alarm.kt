package com.myalarm.clock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val daysOfWeek: Int = 0,
    val enabled: Boolean = true,
    val ringtoneUri: String? = null,
    val vibrationEnabled: Boolean = true,
    val snoozeIntervalMinutes: Int = 10,
    val snoozeMaxRepeats: Int = 3,
    val createdAt: Long = System.currentTimeMillis()
)
