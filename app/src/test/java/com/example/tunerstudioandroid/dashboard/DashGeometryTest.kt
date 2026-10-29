package com.example.tunerstudioandroid.dashboard

import org.junit.Assert.*
import org.junit.Test
import org.clonetuner.dashboard.DashboardDefinition.Component

class DashGeometryTest {
    @Test fun aspectRatioAndNegativeOffsetsSurvivePhoneAndTabletSizes() {
        val viewport=DashGeometry.viewport(1000.0,1000.0,1200.0,600.0)
        assertEquals(250.0,viewport.y,0.001)
        assertEquals(500.0,viewport.height,0.001)
        val component=Component("test","Analog Gauge",mapOf("RelativeX" to "-0.1","RelativeY" to "0.2","RelativeWidth" to "0.4","RelativeHeight" to "0.3","ShapeLockedToAspect" to "true"))
        val bounds=DashGeometry.bounds(component,viewport)
        assertEquals(25.0,bounds.x,0.001)
        assertEquals(350.0,bounds.y,0.001)
        assertEquals(150.0,bounds.width,0.001)
    }
    @Test fun scaleSupportsReversedRangesAndArgbAlpha() {
        assertEquals(0.5,DashGeometry.fraction(12.5,15.0,10.0,true),0.001)
        assertEquals(1.0,DashGeometry.fraction(50.0,0.0,10.0,true),0.001)
        assertEquals(5.0,DashGeometry.fraction(50.0,0.0,10.0,false),0.001)
        assertEquals(0xCC333333.toInt(),DashGeometry.argb("-869059789",0))
    }
}
