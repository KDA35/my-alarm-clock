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
    val createdAt: Long = System.currentTimeMillis(),
    val groupId: Long? = null,
    val volume: Int = 7,
    val vibrationPattern: String = VibrationPattern.BASIC
)

object VibrationPattern {
    const val OFF = "off"
    const val BASIC = "basic"
    const val SHORT = "short"
    const val LONG = "long"
    const val DOUBLE = "double"

    val ALL = listOf(BASIC, SHORT, LONG, DOUBLE)

    fun toLongArray(name: String): LongArray = when (name) {
        SHORT -> longArrayOf(0, 300, 200, 300, 200)
        LONG -> longArrayOf(0, 2000, 500)
        DOUBLE -> longArrayOf(0, 100, 100, 100, 800)
        else -> longArrayOf(0, 1000, 1000)
    }
}
