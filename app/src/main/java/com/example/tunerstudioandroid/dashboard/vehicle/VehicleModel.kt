package com.example.tunerstudioandroid.dashboard.vehicle

enum class VehicleModel(val id:String,val label:String,val description:String) {
    RALLY("rally","Rally","Escala segmentada 0–8000 RPM, marcha central e oito células de sensores."),
    ESCORT_ZETEC("escort-zetec","Ford Escort Zetec 1.8","Mostradores claros: 0–7000 RPM, 0–220 km/h e face compartilhada de combustível/temperatura."),
    MONDEO_1997("mondeo-1997","Ford Mondeo 2.0 (1997)","Mostradores escuros: 0–8000 RPM, 0–240 km/h e instrumentos auxiliares nas extremidades.")
}
