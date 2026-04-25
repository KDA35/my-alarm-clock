package com.myalarm.clock.data

import java.time.DayOfWeek
import java.util.Calendar

object DayOfWeekMask {
    const val MONDAY = 1 shl 0
    const val TUESDAY = 1 shl 1
    const val WEDNESDAY = 1 shl 2
    const val THURSDAY = 1 shl 3
    const val FRIDAY = 1 shl 4
    const val SATURDAY = 1 shl 5
    const val SUNDAY = 1 shl 6

    const val WEEKDAYS = MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY
    const val WEEKENDS = SATURDAY or SUNDAY
    const val EVERY_DAY = WEEKDAYS or WEEKENDS

    private val ORDER = listOf(
        DayOfWeek.MONDAY to MONDAY,
        DayOfWeek.TUESDAY to TUESDAY,
        DayOfWeek.WEDNESDAY to WEDNESDAY,
        DayOfWeek.THURSDAY to THURSDAY,
        DayOfWeek.FRIDAY to FRIDAY,
        DayOfWeek.SATURDAY to SATURDAY,
        DayOfWeek.SUNDAY to SUNDAY
    )

    fun Int.containsDay(day: Int): Boolean = (this and day) != 0

    fun Int.toDayList(): List<DayOfWeek> =
        ORDER.filter { (_, bit) -> this.containsDay(bit) }.map { it.first }

    /**
     * Преобразует Calendar.DAY_OF_WEEK (1=SUNDAY ... 7=SATURDAY) в нашу битовую маску.
     */
    private fun calendarDayToBit(calendarDay: Int): Int = when (calendarDay) {
        Calendar.MONDAY -> MONDAY
        Calendar.TUESDAY -> TUESDAY
        Calendar.WEDNESDAY -> WEDNESDAY
        Calendar.THURSDAY -> THURSDAY
        Calendar.FRIDAY -> FRIDAY
        Calendar.SATURDAY -> SATURDAY
        Calendar.SUNDAY -> SUNDAY
        else -> 0
    }

    /**
     * Возвращает миллисекунды следующего срабатывания будильника, начиная от [from].
     * Если [Alarm.daysOfWeek] == 0 — одноразовый: ближайший hour:minute (сегодня или завтра).
     * Иначе — ближайший день из маски.
     */
    fun calculateNextTrigger(alarm: Alarm, from: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.daysOfWeek == 0) {
            if (cal.timeInMillis <= from) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        repeat(8) { offset ->
            val candidate = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val bit = calendarDayToBit(candidate.get(Calendar.DAY_OF_WEEK))
            if (alarm.daysOfWeek.containsDay(bit) && candidate.timeInMillis > from) {
                return candidate.timeInMillis
            }
        }
        return cal.timeInMillis
    }

    fun formatDaysOfWeek(mask: Int): String {
        if (mask == 0) return "Однократно"
        return when (mask) {
            EVERY_DAY -> "Каждый день"
            WEEKDAYS -> "Будни"
            WEEKENDS -> "Выходные"
            else -> {
                val labels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                (0..6).filter { mask and (1 shl it) != 0 }.joinToString(", ") { labels[it] }
            }
        }
    }
}
