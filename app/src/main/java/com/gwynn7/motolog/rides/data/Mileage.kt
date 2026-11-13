package com.gwynn7.motolog.rides.data

import kotlin.math.roundToLong

object Mileage {
    fun distanceInUnit(meters: Double, unit: String): Double = meters / if (unit == "mi") 1609.344 else 1000.0

    fun suggestedOdometer(current: Int, meters: Double, unit: String): Int {
        require(current >= 0 && meters.isFinite() && meters >= 0)
        val increment = distanceInUnit(meters, unit).roundToLong()
        require(increment <= Int.MAX_VALUE.toLong() - current) { "Odometer is too large" }
        return current + increment.toInt()
    }
}
