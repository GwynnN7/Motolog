package com.gwynn7.motolog.rides.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.StyleSpan
import com.google.android.material.color.MaterialColors
import com.gwynn7.motolog.BuildConfig
import com.gwynn7.motolog.R
import com.gwynn7.motolog.rides.data.RidePoint
import com.gwynn7.motolog.rides.data.RouteChunk
import com.gwynn7.motolog.rides.data.RouteGeometry
import com.gwynn7.motolog.rides.data.RouteSnapshot
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

enum class RouteMetric(val label: Int) {
    ROUTE(R.string.ride_metric_route), SPEED(R.string.ride_speed), LEAN(R.string.ride_lean),
    ACCELERATION(R.string.ride_acceleration), ALTITUDE(R.string.ride_altitude);

    fun value(point: RidePoint): Double? = when (this) {
        ROUTE -> null
        SPEED -> point.speedMetersPerSecond.toDouble()
        LEAN -> point.leanDegrees?.toDouble()
        ACCELERATION -> point.acceleration?.toDouble()
        ALTITUDE -> point.altitudeMeters
    }
}

/** Retains native geometry and markers. Metric changes update spans, never recreate the route. */
class RouteMapPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private var snapshot = RouteSnapshot()
    private var source: List<RidePoint>? = null
    private var metric = RouteMetric.ROUTE
    private val preview = Preview(context)
    private var mapView: MapView? = null
    private var googleMap: GoogleMap? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private var cameraSet = false
    private var alive = true
    private var following = false
    private var live = false
    private var thumbnail = false
    private val rendered = linkedMapOf<Long, RenderedChunk>()
    private var previewPoints = emptyList<RidePoint>()
    private val previewChunks = mutableMapOf<Long, Pair<RouteChunk, List<RidePoint>>>()
    private var previewStride = 0

    private class RenderedChunk(var chunk: RouteChunk, val line: Polyline) {
        val styles = mutableMapOf<RouteMetric, List<StyleSpan>>()
    }

    init {
        addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        contentDescription = context.getString(R.string.ride_map_hint)
    }

    fun attach(owner: LifecycleOwner, savedState: Bundle?, liveTracking: Boolean = false,
               previewMode: Boolean = false,
               onMapStatus: (Int?) -> Unit = {}) {
        live = liveTracking
        thumbnail = previewMode
        following = savedState?.getBoolean("route_following", live) ?: live
        preview.restoreViewport(savedState)
        if (!thumbnail) {
            val controls = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                minimumWidth = dp(80)
            }
            fun button(label: Int, action: () -> Unit) = com.google.android.material.button.MaterialButton(context).apply {
                cornerRadius = dp(24)
                text = context.getString(label)
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                setTextColor(Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(30, 30, 30))
                isClickable = true; isFocusable = true
                setOnClickListener { action() }
            }
            controls.addView(button(R.string.ride_fit_route) { following = false; fitRoute() }, LinearLayout.LayoutParams(-1, dp(48)))
            if (live) controls.addView(button(R.string.ride_follow) { following = true; followLast() }, LinearLayout.LayoutParams(-1, dp(48)))
            addView(controls, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { marginEnd = dp(8); bottomMargin = dp(32) })
            controls.visibility = if (live) GONE else VISIBLE
        }
        if (!BuildConfig.MAPS_ENABLED) {
            onMapStatus(R.string.ride_map_not_configured)
            return
        }
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            onMapStatus(R.string.ride_map_no_services)
            return
        }
        val view = MapView(context, GoogleMapOptions().mapToolbarEnabled(false).liteMode(thumbnail))
        view.onCreate(savedState?.getBundle("route_map"))
        mapView = view
        addView(view, 1, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        onMapStatus(R.string.ride_map_loading)
        // The SDK has no authentication-failure callback. Do not mistake onMapReady for
        // loaded tiles, or leave a blank map unexplained when its key/network is unusable.
        val loadingNotice = Runnable { if (alive) onMapStatus(R.string.ride_map_loading_failed) }
        // Lite mode supports click events, but not the full map's loaded/camera callbacks.
        // Do not hide a valid thumbnail waiting for a callback that will never arrive.
        if (!thumbnail) postDelayed(loadingNotice, 12_000)
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = view.onStart()
            override fun onResume(owner: LifecycleOwner) = view.onResume()
            override fun onPause(owner: LifecycleOwner) = view.onPause()
            override fun onStop(owner: LifecycleOwner) = view.onStop()
            override fun onDestroy(owner: LifecycleOwner) {
                alive = false
                removeCallbacks(loadingNotice)
                rendered.clear(); startMarker = null; endMarker = null
                googleMap = null
                view.onDestroy(); removeView(view); mapView = null
                owner.lifecycle.removeObserver(this)
            }
        })
        view.getMapAsync { map ->
            if (!alive) return@getMapAsync
            googleMap = map
            map.uiSettings.isMapToolbarEnabled = false
            if (!thumbnail) {
                map.uiSettings.isZoomControlsEnabled = false
                map.uiSettings.setAllGesturesEnabled(true)
            }
            if (thumbnail) {
                map.setOnMapClickListener { performClick() }
                map.setOnMarkerClickListener { performClick(); true }
            }
            if (!thumbnail) {
                map.setOnCameraMoveStartedListener { reason ->
                    if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) following = false
                }
                map.setOnMapLoadedCallback {
                    if (!alive) return@setOnMapLoadedCallback
                    removeCallbacks(loadingNotice)
                    onMapStatus(null)
                    if (following) followLast() else if (!cameraSet) fitRoute()
                }
            }
            updateMap(true)
        }
    }

    fun fit() { following = false; fitRoute() }
    fun follow() { following = true; followLast() }

    fun saveState(out: Bundle) {
        out.putBoolean("route_following", following)
        preview.saveViewport(out)
        mapView?.let { val state = Bundle(); it.onSaveInstanceState(state); out.putBundle("route_map", state) }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (thumbnail) return super.dispatchTouchEvent(event)
        // In ride details, NestedScrollView must not steal map drags or pinch gestures.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    fun showRoute(route: List<RidePoint>, selectedMetric: RouteMetric = RouteMetric.ROUTE) {
        if (source !== route) {
            if (thumbnail) cameraSet = false
            source = route
            showSnapshot(RouteGeometry().append(route), selectedMetric)
        } else showSnapshot(snapshot, selectedMetric)
    }

    fun showSnapshot(route: RouteSnapshot, selectedMetric: RouteMetric = RouteMetric.ROUTE) {
        val geometryChanged = snapshot !== route
        val styleChanged = metric != selectedMetric
        if (!geometryChanged && !styleChanged) return
        snapshot = route
        metric = selectedMetric
        if (geometryChanged && (googleMap == null || thumbnail)) {
            // Sample only for the offline canvas; completed samples are cached across GPS updates.
            val stride = max(1, (route.pointCount + 4095) / 4096)
            if (stride != previewStride) { previewChunks.clear(); previewStride = stride }
            val keys = route.chunks.mapTo(hashSetOf()) { it.key }
            previewChunks.keys.retainAll(keys)
            previewPoints = route.chunks.flatMap { chunk ->
                val cached = previewChunks[chunk.key]
                if (cached?.first === chunk) cached.second
                else chunk.points.filterIndexed { i, _ -> i == 0 || i == chunk.points.lastIndex || i % stride == 0 }
                    .also { previewChunks[chunk.key] = chunk to it }
            }
        }
        if (googleMap == null || thumbnail) {
            if (geometryChanged) preview.updateGeometry()
            if (following) preview.followEnd()
            preview.invalidate()
        }
        updateMap(styleChanged)
    }

    private fun color(point: RidePoint): Int {
        val value = metric.value(point) ?: return ROUTE_COLOR
        val range = when (metric) {
            RouteMetric.SPEED -> 0.0 to 40.0
            RouteMetric.LEAN -> 0.0 to 50.0
            RouteMetric.ACCELERATION -> 0.0 to 8.0
            // A fixed elevation scale keeps completed route chunks stable during a climb.
            RouteMetric.ALTITUDE -> 0.0 to 3000.0
            RouteMetric.ROUTE -> 0.0 to 1.0
        }
        val fraction = ((value - range.first) / (range.second - range.first)).coerceIn(0.0, 1.0).toFloat()
        return Color.HSVToColor(floatArrayOf(190f * (1 - fraction), 0.8f, 0.8f))
    }

    private fun updateMap(styleChanged: Boolean) {
        val map = googleMap ?: return
        if (thumbnail) mapView?.visibility = if (snapshot.pointCount > 0) VISIBLE else GONE
        val keys = snapshot.chunks.mapTo(hashSetOf()) { it.key }
        val iterator = rendered.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in keys) { entry.value.line.remove(); iterator.remove() }
        }
        for (chunk in snapshot.chunks) {
            val existing = rendered[chunk.key]
            val changed = existing == null || existing.chunk !== chunk
            val item = existing ?: RenderedChunk(chunk, map.addPolyline(PolylineOptions()
                .width(9f).color(ROUTE_COLOR).addAll(chunk.points.map { it.coordinate() }))).also { rendered[chunk.key] = it }
            if (existing != null && changed) {
                item.line.points = chunk.points.map { it.coordinate() }
                item.chunk = chunk
                item.styles.clear()
            }
            if (changed || styleChanged) {
                item.line.spans = item.styles.getOrPut(metric) {
                    if (metric == RouteMetric.ROUTE) emptyList()
                    else chunk.points.drop(1).map { StyleSpan(color(it)) }
                }
            }
        }
        val first = snapshot.first
        val last = snapshot.last
        if (first == null || last == null) {
            startMarker?.remove(); endMarker?.remove(); startMarker = null; endMarker = null
            return
        }
        if (startMarker == null) startMarker = map.addMarker(MarkerOptions().position(first.coordinate()))
        else if (startMarker!!.position != first.coordinate()) startMarker!!.position = first.coordinate()
        if (endMarker == null) endMarker = map.addMarker(MarkerOptions().position(last.coordinate()))
        else endMarker!!.position = last.coordinate()
        if (following) followLast()
        else if (!cameraSet) post { if (alive) fitRoute() }
    }

    private fun followLast() {
        val map = googleMap ?: run { preview.followEnd(); return }
        val last = snapshot.last ?: return
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(last.coordinate(), if (cameraSet) map.cameraPosition.zoom else 16f))
        cameraSet = true
    }

    private fun fitRoute() {
        val map = googleMap ?: run { preview.resetViewport(); return }
        val first = snapshot.first ?: return
        if (width == 0 || height == 0) return
        if (snapshot.pointCount == 1) map.moveCamera(CameraUpdateFactory.newLatLngZoom(first.coordinate(), 15f))
        else {
            val bounds = LatLngBounds.builder()
            snapshot.chunks.forEach { chunk -> chunk.points.forEach { bounds.include(it.coordinate()) } }
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(48)))
        }
        cameraSet = true
    }

    private fun RidePoint.coordinate() = LatLng(latitude, longitude)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private inner class Preview(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var zoom = 1f
        private var panX = 0f
        private var panY = 0f
        private var positions = emptyList<Pair<Double, Double>>()
        private var centerX = 0.0
        private var centerY = 0.0
        private var spanX = 0.00001
        private var spanY = 0.00001
        private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomAt(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        })
        private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent) = true
            override fun onSingleTapUp(event: MotionEvent): Boolean = performClick()
            override fun onScroll(first: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (!scaleDetector.isInProgress) { following = false; panX -= distanceX; panY -= distanceY; invalidate() }
                return true
            }
            override fun onDoubleTap(event: MotionEvent): Boolean {
                zoomAt(2f, event.x, event.y)
                return true
            }
        })

        init { isClickable = true; contentDescription = context.getString(R.string.ride_offline_map_hint) }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (thumbnail) {
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
            scaleDetector.onTouchEvent(event)
            gestures.onTouchEvent(event)
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            if (thumbnail) this@RouteMapPanel.performClick()
            return true
        }

        private fun zoomAt(factor: Float, focusX: Float, focusY: Float) {
            following = false
            val next = (zoom * factor).coerceIn(1f, 32f)
            val ratio = next / zoom
            panX = focusX - width / 2f - (focusX - width / 2f - panX) * ratio
            panY = focusY - height / 2f - (focusY - height / 2f - panY) * ratio
            zoom = next
            invalidate()
        }

        fun resetViewport() { zoom = 1f; panX = 0f; panY = 0f; invalidate() }
        fun saveViewport(state: Bundle) {
            state.putFloat("preview_zoom", zoom); state.putFloat("preview_x", panX); state.putFloat("preview_y", panY)
        }
        fun restoreViewport(state: Bundle?) {
            zoom = state?.getFloat("preview_zoom", 1f) ?: 1f
            panX = state?.getFloat("preview_x") ?: 0f; panY = state?.getFloat("preview_y") ?: 0f
        }
        fun updateGeometry() {
            val origin = previewPoints.firstOrNull() ?: run { positions = emptyList(); return }
            positions = previewPoints.map {
                val longitude = ((it.longitude - origin.longitude + 540.0) % 360.0) - 180.0
                longitude * cos(Math.toRadians(origin.latitude)) to -(it.latitude - origin.latitude)
            }
            val minX = positions.minOf { it.first }; val maxX = positions.maxOf { it.first }
            val minY = positions.minOf { it.second }; val maxY = positions.maxOf { it.second }
            centerX = (minX + maxX) / 2; centerY = (minY + maxY) / 2
            spanX = max(0.00001, maxX - minX); spanY = max(0.00001, maxY - minY)
        }
        private fun scale() = min((width - dp(48)) / spanX, (height - dp(if (live) 340 else 80)) / spanY).coerceAtLeast(1.0) * zoom
        fun followEnd() {
            val last = positions.lastOrNull() ?: return
            panX = (-(last.first - centerX) * scale()).toFloat()
            panY = (-(last.second - centerY) * scale()).toFloat()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
            val points = previewPoints
            paint.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
            paint.textSize = 13f * resources.displayMetrics.scaledDensity
            if (points.isEmpty()) {
                canvas.drawText(context.getString(R.string.ride_empty_route), dp(16).toFloat(), height / 2f, paint)
                return
            }
            val scale = scale()
            fun x(index: Int) = (width / 2.0 + panX + (positions[index].first - centerX) * scale).toFloat()
            fun y(index: Int) = (height / 2.0 + panY + (positions[index].second - centerY) * scale).toFloat()
            paint.strokeWidth = dp(3).toFloat(); paint.strokeCap = Paint.Cap.ROUND
            for (i in 1 until points.size) if (points[i].segment == points[i - 1].segment) {
                paint.color = color(points[i]); canvas.drawLine(x(i - 1), y(i - 1), x(i), y(i), paint)
            }
            paint.color = ROUTE_COLOR; canvas.drawCircle(x(0), y(0), dp(5).toFloat(), paint)
            paint.color = Color.rgb(190, 65, 45); canvas.drawCircle(x(points.lastIndex), y(points.lastIndex), dp(5).toFloat(), paint)
        }
    }

    private companion object { val ROUTE_COLOR = Color.rgb(0, 150, 136) }
}
