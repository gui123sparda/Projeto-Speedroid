package com.example.tunerstudioandroid.dashboard

import org.clonetuner.dashboard.DashboardDefinition.Component
import kotlin.math.min

object DashGeometry {
    data class Box(val x: Double, val y: Double, val width: Double, val height: Double) {
        fun contains(px: Double, py: Double) = px >= x && py >= y && px <= x+width && py <= y+height
    }
    fun viewport(width: Double, height: Double, designWidth: Double = 1200.0, designHeight: Double = 620.0): Box {
        val scale = min(width/designWidth,height/designHeight)
        val w = designWidth*scale; val h=designHeight*scale
        return Box((width-w)/2,(height-h)/2,w,h)
    }
    fun bounds(c: Component, viewport: Box): Box {
        var x=viewport.x+c.number("RelativeX",0.0)*viewport.width
        var y=viewport.y+c.number("RelativeY",0.0)*viewport.height
        var w=c.number("RelativeWidth",1.0)*viewport.width
        var h=c.number("RelativeHeight",1.0)*viewport.height
        if(c.flag("ShapeLockedToAspect",false)) { val size=min(w,h); x+=(w-size)/2; y+=(h-size)/2; w=size; h=size }
        return Box(x,y,w,h)
    }
    fun fraction(value: Double, minimum: Double, maximum: Double, peg: Boolean): Double {
        val fraction=if(maximum==minimum || !value.isFinite()) 0.0 else (value-minimum)/(maximum-minimum)
        return if(peg) fraction.coerceIn(0.0,1.0) else fraction
    }
    fun argb(value: String, fallback: Int) = value.toLongOrNull()?.toInt() ?: fallback
}
