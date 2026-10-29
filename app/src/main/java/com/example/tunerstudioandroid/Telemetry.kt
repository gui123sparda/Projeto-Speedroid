package com.example.tunerstudioandroid

/** Canonical ECU names and units for interoperable fuel-analysis CSV files. */
object Telemetry {
    fun logValues(seconds: Double, channel: (String) -> Double?, gpsKmh: Double?, acceleration: Double?, estimatedKmh: Double?): List<Double?> =
        listOf(seconds, channel("rpm"), channel("map"), channel("tps"), channel("coolantRaw")?.minus(40),
            channel("afr"), channel("afrTarget"), channel("egoCorrection"), gpsKmh, acceleration, estimatedKmh)
}
