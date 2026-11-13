package com.gwynn7.motolog.rides.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.gwynn7.motolog.R
import com.gwynn7.motolog.rides.data.Mileage
import com.gwynn7.motolog.rides.data.RidePoint
import java.util.Locale

fun rideDistance(meters: Double, unit: String): String = String.format(Locale.getDefault(), "%.2f %s", Mileage.distanceInUnit(meters, unit), unit)
fun rideDuration(millis: Long): String {
    val seconds = millis / 1000
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}
fun rideSpeed(metersPerSecond: Float, unit: String): String = String.format(Locale.getDefault(), "%.0f %s", metersPerSecond * if (unit == "mi") 2.236936 else 3.6, if (unit == "mi") "mph" else "km/h")

fun openFuelSearch(context: Context, lastPoint: RidePoint?) {
    val query = lastPoint?.let { "petrol stations near ${it.latitude},${it.longitude}" } ?: "petrol stations near me"
    val uri = Uri.parse("https://www.google.com/maps/search/").buildUpon()
        .appendQueryParameter("api", "1").appendQueryParameter("query", query).build()
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, R.string.ride_no_map_app, Toast.LENGTH_LONG).show() }
}
