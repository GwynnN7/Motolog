package com.gwynn7.motolog.rides.data

data class RouteChunk(val points: List<RidePoint>) {
    val key: Long get() = points.first().id
}

data class RouteSnapshot(val chunks: List<RouteChunk> = emptyList(), val pointCount: Int = 0) {
    val first: RidePoint? get() = chunks.firstOrNull()?.points?.firstOrNull()
    val last: RidePoint? get() = chunks.lastOrNull()?.points?.lastOrNull()
}

/** Immutable completed chunks are shared between updates. Only the small tail changes. */
class RouteGeometry(private val chunkSize: Int = 256) {
    init { require(chunkSize >= 2) }
    private val complete = mutableListOf<RouteChunk>()
    private val tail = mutableListOf<RidePoint>()
    private var count = 0
    private var lastId = 0L

    fun append(points: List<RidePoint>): RouteSnapshot {
        for (point in points) {
            if (point.id <= lastId) continue
            val previous = tail.lastOrNull()
            if (previous != null && (previous.segment != point.segment || tail.size == chunkSize)) {
                complete += RouteChunk(tail.toList())
                tail.clear()
                if (previous.segment == point.segment) tail += previous
            }
            tail += point
            lastId = point.id
            count++
        }
        return RouteSnapshot(complete + if (tail.isEmpty()) emptyList() else listOf(RouteChunk(tail.toList())), count)
    }
}
