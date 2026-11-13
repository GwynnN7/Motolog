package com.gwynn7.motolog.ui

import android.content.Context
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import java.text.NumberFormat

fun Context.formatLogInterval(days: Long): String {
    val interval = logInterval(days)
    val parts = listOf(interval.years to R.plurals.interval_years,
        interval.months to R.plurals.interval_months, interval.days to R.plurals.interval_days)
        .filter { it.first > 0 }.map { (count, label) -> resources.getQuantityString(label, count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), count) }
    return parts.joinToString(getString(R.string.interval_separator))
}

fun formatMoney(value: Double): String = NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}.format(value) + " " + UnitHelper.getCurrency()

fun Context.specification(value: Double): String = if (value <= 0) getString(R.string.not_set)
    else NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)
