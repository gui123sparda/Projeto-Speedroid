package com.example.tunerstudioandroid.vss

import kotlin.math.roundToInt

data class GpsFix(val kmh: Double, val elapsedMs: Long) {
    fun valid(nowMs: Long) = kmh.isFinite() && kmh in 0.0..500.0 && nowMs - elapsedMs in 0..1500
}

/** Optional negotiated extension, carried inside the normal Speeduino CRC envelope. */
object GpsVssProtocol {
    val query get() = byteArrayOf(103,1,0)
    fun supported(data: ByteArray) = data.contentEquals(byteArrayOf(71,80,83,86,83,83,1))
    fun packet(enabled: Boolean, fix: GpsFix?, nowMs: Long): ByteArray {
        val valid = enabled && fix?.valid(nowMs) == true
        val speed = if (valid) (fix!!.kmh * 10).roundToInt() else 0
        return byteArrayOf(103,1,1, ((if(enabled) 1 else 0) or (if(valid) 2 else 0)).toByte(), speed.toByte(), (speed ushr 8).toByte())
    }
}
