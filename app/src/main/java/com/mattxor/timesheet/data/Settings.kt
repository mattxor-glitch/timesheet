package com.mattxor.timesheet.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Choices offered for how long history is kept. [days] == null means never delete. */
enum class Retention(val days: Int?, val label: String) {
    WEEK(7, "1 week"),
    MONTH(31, "1 month"),
    QUARTER(92, "3 months"),
    HALF_YEAR(183, "6 months"),
    YEAR(366, "1 year"),
    TWO_YEARS(731, "2 years"),
    FIVE_YEARS(1827, "5 years"),
    NEVER(null, "Never delete"),
}

class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _retention = MutableStateFlow(
        prefs.getString(KEY_RETENTION, null)
            ?.let { name -> Retention.entries.firstOrNull { it.name == name } }
            ?: Retention.NEVER
    )
    val retention: StateFlow<Retention> = _retention.asStateFlow()

    private val _carryOverMidnight = MutableStateFlow(prefs.getBoolean(KEY_CARRY, false))
    val carryOverMidnight: StateFlow<Boolean> = _carryOverMidnight.asStateFlow()

    fun setRetention(value: Retention) {
        prefs.edit().putString(KEY_RETENTION, value.name).apply()
        _retention.value = value
    }

    fun setCarryOverMidnight(value: Boolean) {
        prefs.edit().putBoolean(KEY_CARRY, value).apply()
        _carryOverMidnight.value = value
    }

    private companion object {
        const val KEY_RETENTION = "retention"
        const val KEY_CARRY = "carry_over_midnight"
    }
}
