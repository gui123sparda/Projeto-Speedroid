package com.example.tunerstudioandroid

import java.util.Locale

class CsvLog(private val capacity: Int = 60000) {
    private val rows = StringBuilder()
    var size = 0
        private set
    fun append(values: List<Double?>): Boolean {
        if (size >= capacity) return false
        rows.append(values.joinToString(",") { value -> value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.3f", it) } ?: "" }).append('\n')
        size++
        return true
    }
    fun body() = rows.toString()
    fun clear() { rows.setLength(0); size = 0 }
    companion object {
        const val HEADER = "Time (s),RPM,MAP,TPS,CLT,AFR,AFR Target,Gego,GPS speed (km/h),Acceleration (m/s2),Estimated speed (km/h)\n"
    }
}
