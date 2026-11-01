package com.gwynn7.motolog

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.core.graphics.scale
import kotlinx.coroutines.flow.map

enum class Path {
    Add,
    Edit
}

val repairColors = arrayOf(
    R.color.red,
    R.color.orange,
    R.color.blue,
    R.color.yellow,
    R.color.pink,
    R.color.green,
    R.color.white
)

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
    fun getDistanceText(context: Context) = if (distance == Distance.MILES) context.getString(R.string.miles_undercase) else distance.value
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

    fun saveDistance(context: Context, scope: CoroutineScope, newDistance: Distance) {
        distance = newDistance
        scope.launch(Dispatchers.IO) {
            context.settings.edit { settings -> settings[distanceKey] = newDistance.value }
        }
    }

    fun saveCurrency(context: Context, scope: CoroutineScope, newCurrency: Currency) {
        currency = newCurrency
        scope.launch(Dispatchers.IO) {
            context.settings.edit { settings -> settings[currencyKey] = newCurrency.value }
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

fun capitalize(string: String): String {
    return string.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

fun stop(activity: Activity?) {
    activity?.finish()
}

fun getResizedBitmap(image: Bitmap, maxSize: Int): Bitmap {
    var width = image.width
    var height = image.height

    val bitmapRatio = width.toFloat() / height.toFloat()
    if (bitmapRatio > 1) {
        width = maxSize
        height = (width / bitmapRatio).toInt()
    } else {
        height = maxSize
        width = (height * bitmapRatio).toInt()
    }
    return image.scale(width, height)
}

fun deleteImage(image: Uri?) {
    val path = image?.path ?: return
    val oldFile = File(path)
    if (oldFile.exists()) oldFile.delete()
}
