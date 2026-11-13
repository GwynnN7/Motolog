package com.gwynn7.motolog.rides.tracking

/** A preparation fix must be accurate and fresh, not a cached position from an earlier ride. */
object GpsReadiness {
    const val MAX_ACCURACY_METERS = 35f
    fun isReady(accuracyMeters: Float?, ageSeconds: Double): Boolean =
        accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters in 0f..MAX_ACCURACY_METERS &&
            ageSeconds.isFinite() && ageSeconds in 0.0..10.0
}
