package com.example.tunerstudioandroid

import kotlin.math.abs

/** GPS is the absolute reference. Inertial prediction is bounded to 2 seconds. */
class SpeedEstimator {
    data class Reading(val kmh: Double, val source: String)
    private var fixTime = Long.MIN_VALUE
    private var sampleTime = Long.MIN_VALUE
    private var base = 0.0
    private var predicted = 0.0
    private var inertial = false

    fun gps(mps: Double, accuracyMps: Double, timeMs: Long): Boolean {
        if (!mps.isFinite() || mps !in 0.0..150.0 || !accuracyMps.isFinite() ||
            accuracyMps !in 0.0..3.0 || timeMs <= fixTime) return false
        fixTime = timeMs
        sampleTime = timeMs
        base = mps
        predicted = mps
        inertial = false
        return true
    }

    fun acceleration(forwardMps2: Double, timeMs: Long) {
        if (fixTime == Long.MIN_VALUE || timeMs <= sampleTime || !forwardMps2.isFinite()) return
        val dt = (timeMs - sampleTime) / 1000.0
        sampleTime = timeMs
        if (timeMs - fixTime > 2000 || dt > 0.3 || abs(forwardMps2) > 15) return
        val acceleration = if (abs(forwardMps2) < 0.12) 0.0 else forwardMps2
        predicted = (predicted + acceleration * dt).coerceIn(0.0, 150.0)
        inertial = true
    }

    fun reading(timeMs: Long): Reading? {
        if (fixTime == Long.MIN_VALUE || timeMs < fixTime || timeMs - fixTime > 6000) return null
        val usePrediction = inertial && timeMs - fixTime <= 2000 && timeMs - sampleTime <= 300
        return Reading((if (usePrediction) predicted else base) * 3.6, if (usePrediction) "GPS + inercial" else "GPS")
    }

    fun gpsKmh(timeMs: Long): Double? = reading(timeMs)?.let { base * 3.6 }

    fun reset() { fixTime = Long.MIN_VALUE; sampleTime = Long.MIN_VALUE; inertial = false }
}
