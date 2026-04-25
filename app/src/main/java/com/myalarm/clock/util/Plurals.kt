package com.myalarm.clock.util

import android.content.Context
import com.myalarm.clock.R

fun pluralizeRepeats(count: Int, context: Context): String =
    if (count == 0) context.getString(R.string.edit_snooze_repeats_unlimited)
    else context.resources.getQuantityString(R.plurals.snooze_repeats, count, count)
