package com.example.tunerstudioandroid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.location.*
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlin.math.*
import com.example.tunerstudioandroid.vss.GpsFix

class PhoneSensors(context: Context) : SensorEventListener, LocationListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val locations = context.getSystemService(LocationManager::class.java)
    private val app = context.applicationContext
    val speed = SpeedEstimator()
    var onGpsFix: (GpsFix?) -> Unit = {}
    var acceleration: Double? = null
        private set
    var gpsStatus = "GPS desativado"
        private set
    val hasAccelerometer get() = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null
    private var rotation: FloatArray? = null
    private var course: Double? = null
    private var courseTime = 0L
    private var running = false

    fun start() {
        if (running) return
        running = true
        speed.reset()
        sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.also { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.also { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            gpsStatus = "Autorize a localização precisa"; return
        }
        try {
            if (!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) { gpsStatus = "Ative o GPS nas configurações"; return }
            gpsStatus = "Aguardando sinal GPS"
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
        } catch (e: Exception) { gpsStatus = "GPS indisponível: ${e.message}" }
    }

    fun stop() {
        sensors.unregisterListener(this)
        locations.removeUpdates(this)
        running = false; rotation = null; course = null; acceleration = null; speed.reset()
        gpsStatus = "GPS pausado"
        onGpsFix(null)
    }

    override fun onLocationChanged(location: Location) {
        val now = SystemClock.elapsedRealtime()
        val sampleTime = location.elapsedRealtimeNanos / 1_000_000
        if (now - sampleTime !in 0..2500 || !location.hasSpeed() || !location.hasAccuracy() || location.accuracy > 30) {
            gpsStatus = "Aguardando GPS com precisão suficiente"; return
        }
        val accuracy = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond.toDouble()
            else if (location.accuracy <= 15) 2.5 else 4.0
        if (speed.gps(location.speed.toDouble(), accuracy, sampleTime)) {
            onGpsFix(GpsFix(location.speed.toDouble() * 3.6, sampleTime))
            gpsStatus = "GPS · ±${"%.1f".format(accuracy * 3.6)} km/h"
            if (location.hasBearing() && location.speed >= 2 && (!location.hasBearingAccuracy() || location.bearingAccuracyDegrees <= 25)) {
                val field = GeomagneticField(location.latitude.toFloat(), location.longitude.toFloat(), location.altitude.toFloat(), location.time)
                course = Math.toRadians((location.bearing - field.declination).toDouble())
                courseTime = sampleTime
            } else { course = null }
        } else gpsStatus = "GPS com baixa precisão de velocidade"
    }
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val matrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                rotation = matrix
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val v = event.values
                acceleration = sqrt((v[0]*v[0] + v[1]*v[1] + v[2]*v[2]).toDouble())
                val r = rotation ?: return
                val heading = course ?: return
                val time = event.timestamp / 1_000_000
                if (time - courseTime !in 0..2000) return
                val east = r[0]*v[0] + r[1]*v[1] + r[2]*v[2]
                val north = r[3]*v[0] + r[4]*v[1] + r[5]*v[2]
                speed.acceleration(east * sin(heading) + north * cos(heading), time)
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
    override fun onProviderDisabled(provider: String) { onGpsFix(null); speed.reset(); course = null; gpsStatus = "GPS desativado" }
    override fun onProviderEnabled(provider: String) { gpsStatus = "Aguardando sinal GPS" }
    @Deprecated("Legacy Android callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { }
}
