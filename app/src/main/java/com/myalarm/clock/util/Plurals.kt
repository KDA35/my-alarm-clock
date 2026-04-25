package com.myalarm.clock.util

import android.content.Context
import com.myalarm.clock.R

fun pluralizeRepeats(count: Int, context: Context): String = when {
    count == 0 -> context.getString(R.string.edit_snooze_repeats_unlimited)
    count % 10 == 1 && count % 100 != 11 ->
        context.getString(R.string.edit_snooze_repeats_format_one, count)
    count % 10 in 2..4 && count % 100 !in 12..14 ->
        context.getString(R.string.edit_snooze_repeats_format_few, count)
    else ->
        context.getString(R.string.edit_snooze_repeats_format_many, count)
}
