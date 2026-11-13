package com.gwynn7.motolog.rides.tracking

import kotlin.math.acos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** MotoTrack's gravity projection, with bounded calibration and invalid-vector guards. */
class LeanEstimator {
    private data class Vector(val x: Float, val y: Float, val z: Float) {
        operator fun plus(v: Vector) = Vector(x + v.x, y + v.y, z + v.z)
        operator fun minus(v: Vector) = Vector(x - v.x, y - v.y, z - v.z)
        operator fun times(n: Float) = Vector(x * n, y * n, z * n)
        fun dot(v: Vector) = x * v.x + y * v.y + z * v.z
        fun magnitude() = sqrt(dot(this))
        fun normal(): Vector? = magnitude().takeIf { it.isFinite() && it > 0.01f }?.let { this * (1f / it) }
        fun project(axis: Vector) = this - axis * dot(axis)
    }
    private var gravitySum = Vector(0f, 0f, 0f)
    private var gravityCount = 0
    private var strongestAcceleration = Vector(0f, 0f, 0f)
    private var forward: Vector? = null
    private var baseline: Vector? = null

    fun finishCalibration(): Boolean {
        if (gravityCount < 10 || strongestAcceleration.magnitude() < 0.5f) return false
        val normalGravity = gravitySum.normal() ?: return false
        forward = strongestAcceleration.project(normalGravity).normal() ?: return false
        baseline = normalGravity.project(forward!!).normal()
        return baseline != null
    }

    fun acceleration(values: FloatArray, calibrating: Boolean) {
        if (!calibrating || baseline != null) return
        val vector = Vector(values[0], values[1], values[2])
        if (vector.magnitude() > strongestAcceleration.magnitude()) strongestAcceleration = vector
    }

    fun gravity(values: FloatArray, calibrating: Boolean): Int? {
        val vector = Vector(values[0], values[1], values[2])
        if (baseline == null) {
            if (calibrating && gravityCount < 500) {
                gravitySum += vector
                gravityCount++
            } else if (!calibrating && gravityCount >= 10 && strongestAcceleration.magnitude() >= 0.5f) {
                finishCalibration()
            }
            return null
        }
        val projected = vector.normal()?.project(forward!!)?.normal() ?: return null
        return Math.toDegrees(acos(projected.dot(baseline!!).coerceIn(-1f, 1f)).toDouble()).roundToInt()
    }
}
