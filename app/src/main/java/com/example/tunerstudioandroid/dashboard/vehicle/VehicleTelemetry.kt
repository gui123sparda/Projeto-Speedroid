package com.example.tunerstudioandroid.dashboard.vehicle

import java.util.Locale

/** Canonical values used by the original vehicle artwork, including the selected GPS/VSS source. */
class VehicleTelemetry(private val channel:(String)->Double?,private val units:(String)->String) {
    data class Reading(val value:Double,val units:String) {
        fun available()=value.isFinite()
        fun text(digits:Int)=if(available())String.format(Locale.ROOT,"%.${digits.coerceIn(0,6)}f",value) else "--"
        fun fraction(min:Double,max:Double)=if(available() && min!=max)((value-min)/(max-min)).coerceIn(0.0,1.0) else 0.0
    }
    fun read(vararg names:String):Reading {
        for(name in names) {
            val value=channel(name)
            if(value!=null && value.isFinite())return Reading(value,units(name))
        }
        return Reading(Double.NaN,"")
    }
    fun rpm()=read("rpm")
    fun speedKmh():Reading {
        val speed=read("vss","vehicleSpeed")
        return Reading(speed.value*(if(speed.units.equals("mph",true))1.609344 else 1.0),"km/h")
    }
    fun coolantC():Reading {
        val raw=read("coolantRaw")
        return if(raw.available())Reading(raw.value-40,"°C") else temperatureC("clt","coolant","coolantTemp")
    }
    fun fuelPercent():Reading {
        val fuel=read("fuelLevel","fuelLevelPct","fuelLevelPercent","tankLevel")
        return Reading(if(fuel.available())fuel.value.coerceIn(0.0,100.0) else Double.NaN,"%")
    }
    fun temperatureC(vararg names:String):Reading {
        val reading=read(*names)
        return Reading(if(reading.units.lowercase(Locale.ROOT).replace("°","")=="f")(reading.value-32)/1.8 else reading.value,"°C")
    }
    fun pressureKpa(vararg names:String):Reading {
        val reading=read(*names)
        val factor=when(reading.units.lowercase(Locale.ROOT)) { "psi"->6.894757293;"bar"->100.0;"pa"->0.001;else->1.0 }
        return Reading(reading.value*factor,"kPa")
    }
    fun active(vararg names:String):Boolean=read(*names).let { it.available() && it.value!=0.0 }
    fun readings():Map<String,Reading> = linkedMapOf("RPM" to rpm(),"Velocidade" to speedKmh(),"Combustível" to fuelPercent(),"Motor" to coolantC(),"Marcha" to read("gear"),"Escapamento" to temperatureC("exhaustTemp","egt"),"Óleo" to temperatureC("oilTemp"),"Câmbio" to temperatureC("gearboxTemp"),"Pressão do rail" to pressureKpa("railPressure","fuelRailPressure"),"Pressão de combustível" to pressureKpa("fuelPressure"),"Pressão de óleo" to pressureKpa("oilPressure"),"Bateria" to read("batteryVoltage"))
}
