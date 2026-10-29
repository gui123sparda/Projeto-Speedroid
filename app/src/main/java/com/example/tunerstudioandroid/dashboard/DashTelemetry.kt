package com.example.tunerstudioandroid.dashboard

import com.example.tunerstudioandroid.vss.GpsFix
import org.clonetuner.dashboard.DashboardDefinition.Component

/** Adapts legacy .dash channel names without inventing unavailable ECU data. */
class DashTelemetry(
    private val channel: (String) -> Double?,
    private val constant: (String) -> Double?,
    private val gps: () -> GpsFix?,
    private val clock: () -> Long,
    private val connected: () -> Boolean,
    private val logging: () -> Boolean,
    private val progress: () -> Double?
) {
    data class Reading(val value: Double?, val units: String)
    fun read(name: String): Double? = when(name) {
        "gpsActive" -> if(gps()?.valid(clock()) == true) 1.0 else 0.0
        "gpsSpeedMPH" -> gps()?.takeIf { it.valid(clock()) }?.kmh?.div(1.609344)
        "gpsSpeedKPH", "gpsSpeedKmh", "gpsSpeed" -> gps()?.takeIf { it.valid(clock()) }?.kmh
        "dataLoggingActive" -> if(logging()) 1.0 else 0.0
        "interrogationProgress" -> progress()
        "CrankingOC" -> channel("crank")
        "MAP DecelOC" -> channel("mapaccden")
        "bootloaderCapsGT0_OC" -> if(connected()) constant("bootloaderCaps")?.let { if(it>0)1.0 else 0.0 } else null
        "LPtpsGTtpsfloodRPANDANDLPrpmLTcrankRPMRP_OC" -> {
            val tps=channel("tps"); val rpm=channel("rpm"); val flood=constant("tpsflood"); val crank=constant("crankRPM")
            if(tps!=null && rpm!=null && flood!=null && crank!=null) if(tps>flood && rpm<crank) 1.0 else 0.0 else null
        }
        "null", "" -> null
        else -> channel(name)
    }
    fun read(component: Component): Reading {
        val name=component.text("OutputChannel")
        val unit=component.text("Units")
        if(name=="coolant" || name=="iat") {
            val raw=channel(name+"Raw")
            if(raw!=null) {
                val celsius=raw-40
                return Reading(if(unit=="°F") celsius*1.8+32 else celsius,if(unit=="°F") "°F" else "°C")
            }
        }
        return Reading(read(name)?.takeIf { it.isFinite() },unit)
    }
}
