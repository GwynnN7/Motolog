package com.gwynn7.motolog.rides.data

/** Small card geometry: keep endpoints and pause boundaries so previews never bridge a pause. */
internal fun routePreview(points: List<RidePoint>, targetPoints: Int = 512): List<RidePoint> {
    require(targetPoints >= 2)
    if (points.size <= targetPoints) return points
    val stride = (points.size + targetPoints - 1) / targetPoints
    return points.filterIndexed { index, point ->
        index == 0 || index == points.lastIndex || index % stride == 0 ||
            points[index - 1].segment != point.segment ||
            points[index + 1].segment != point.segment
    }
}
