package com.gwynn7.motolog

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.map
import com.gwynn7.motolog.settings.settings

enum class Path {
    Add,
    Edit
}

object UnitHelper {
    private val distanceKey = stringPreferencesKey("distance")
    private val currencyKey = stringPreferencesKey("currency")

    enum class Currency(val value: String) {
        EUR("€"),
        USD("$"),
        GBP("£"),
        JPY("¥")
    }

    enum class Distance(val value: String) {
        KM("km"),
        MILES("mi"),
    }

    var distance: Distance = Distance.KM
    var currency: Currency = Currency.EUR

    fun getDistance() = distance.value
    fun getCurrency() = currency.value

    fun loadData(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.settings.data.map { settings ->
                val dist = settings[distanceKey] ?: Distance.KM.value
                val curr = settings[currencyKey] ?: Currency.EUR.value
                Pair(fromDistance(dist), fromCurrency(curr))
            }.collect { (dist, curr) ->
                distance = dist
                currency = curr
            }
        }
    }

    private fun fromDistance(value: String): Distance =
        Distance.entries.firstOrNull { it.value == value } ?: Distance.KM
    private fun fromCurrency(value: String): Currency =
        Currency.entries.firstOrNull { it.value == value } ?: Currency.EUR
}

fun longToDateString(date: Long): String {
    val simpleDateFormat by lazy { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    return simpleDateFormat.format(Date(date))
}

fun dateFromLong(date: Long, field: Int): Int {
    val cal: Calendar = Calendar.getInstance()
    cal.time = Date(date)
    return cal.get(field)
}

fun longFromDate(year: Int, month: Int, dayOfMonth: Int): Long {
    val cal: Calendar = Calendar.getInstance()
    cal.set(year, month, dayOfMonth)
    return cal.timeInMillis
}

fun showToast(context: Context, text: String, length: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(context, text, length).show()
}

fun formatThousand(number: Int): String {
    val formatter = DecimalFormat("#,###")
    return formatter.format(number)
}

fun stop(activity: Activity?) {
    if (activity is MainActivity) activity.returnToGarage() else activity?.finish()
}
