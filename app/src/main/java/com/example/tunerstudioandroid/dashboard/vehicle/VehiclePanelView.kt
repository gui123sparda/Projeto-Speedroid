package com.example.tunerstudioandroid.dashboard.vehicle

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.os.SystemClock
import androidx.core.view.ViewCompat
import com.example.tunerstudioandroid.dashboard.*
import java.util.Locale
import kotlin.math.roundToInt

class VehiclePanelView(context:Context,val model:VehicleModel,private val telemetry:VehicleTelemetry,private val history:DashHistory=DashHistory()):View(context),PanelSurface {
    override val view:View get()=this
    override val diagnostics:Set<String> = emptySet()
    private val artwork=VehicleArtwork(telemetry,model)
    private val renderer=VehicleSceneRenderer(context)
    init {
        contentDescription="Painel personalizado ${model.label}"
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable=true
    }
    override fun onMeasure(widthSpec:Int,heightSpec:Int) {
        val width=MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(width,resolveSize((width*620.0/1200).roundToInt(),heightSpec))
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xff0b0e10.toInt())
        val viewport=DashGeometry.viewport(width.toDouble(),height.toDouble())
        if(viewport.width<=0)return
        canvas.save();canvas.translate(viewport.x.toFloat(),viewport.y.toFloat())
        val scale=(viewport.width/1200).toFloat();canvas.scale(scale,scale);canvas.clipRect(0f,0f,1200f,620f)
        renderer.draw(canvas,artwork.draw());canvas.restore()
        val now=SystemClock.elapsedRealtime()
        telemetry.readings().forEach { (name,reading) -> if(reading.available())history.accept(name,reading.value,now,15000) }
        val description="${telemetry.rpm().text(0)} RPM · ${telemetry.speedKmh().text(0)} km/h · motor ${telemetry.coolantC().text(0)} °C · combustível ${telemetry.fuelPercent().text(0)} %"
        if(ViewCompat.getStateDescription(this)!=description)ViewCompat.setStateDescription(this,description)
    }
    override fun refresh() { postInvalidateOnAnimation() }
    override fun resetHistory() { history.clear();invalidate() }
    override fun extrema():List<String> = telemetry.readings().mapNotNull { (name,reading) -> history.range(name)?.let { range -> "$name: ${String.format(Locale.ROOT,"%.1f / %.1f",range.minimum,range.maximum)} ${reading.units}" } }
}
