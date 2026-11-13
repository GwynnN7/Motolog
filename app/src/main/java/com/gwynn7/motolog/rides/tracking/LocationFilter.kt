package com.gwynn7.motolog.rides.tracking

/** Location acceptance policy for the recorder. Never connects gaps in a track. */
object LocationFilter {
    // Maximum reported horizontal error accepted while recording. Lower is stricter;
    // this is unrelated to minimum movement between updates or the preparation gate.
    const val MAX_ACCURACY_METERS = 50f
    const val MAX_GAP_SECONDS = 30.0
    fun isUsable(accuracy: Float, ageSeconds: Double): Boolean =
        accuracy.isFinite() && accuracy in 0f..MAX_ACCURACY_METERS && ageSeconds in 0.0..10.0

    fun segmentDistance(distance: Double, seconds: Double, accuracy: Float, speed: Float): Double? {
        if (!distance.isFinite() || seconds <= 0 || seconds > MAX_GAP_SECONDS) return null
        if (distance / seconds > 100.0) return null // Reject implausible GPS jumps (> 360 km/h).
        if (speed < 0.8f && distance < accuracy.coerceAtLeast(5f)) return 0.0
        return distance.coerceAtLeast(0.0)
    }
}
