package com.myalarm.clock.ui.onboarding

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    var completed: Boolean
        get() = prefs.getBoolean(KEY_COMPLETED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_COMPLETED, value).apply()
        }

    fun reset() {
        completed = false
    }

    companion object {
        private const val KEY_COMPLETED = "completed"
    }
}
