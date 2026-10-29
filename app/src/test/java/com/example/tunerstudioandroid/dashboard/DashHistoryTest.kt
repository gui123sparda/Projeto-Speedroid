package com.example.tunerstudioandroid.dashboard

import org.junit.Assert.*
import org.junit.Test

class DashHistoryTest {
    @Test fun historyExpiresButSessionRangesSurviveViewRecreation() {
        val history=DashHistory()
        history.accept("rpm",3500.0,1000,15000)
        history.accept("rpm",1000.0,2000,15000)
        assertEquals(3500.0,history.range("rpm")!!.peak,0.001)
        history.accept("rpm",1200.0,17001,15000)
        val range=history.range("rpm")!!
        assertEquals(1200.0,range.peak,0.001)
        assertEquals(1000.0,range.minimum,0.001)
        assertEquals(3500.0,range.maximum,0.001)
        history.accept("rpm",Double.NaN,18000,15000)
        assertEquals(3500.0,history.range("rpm")!!.maximum,0.001)
        history.clear()
        assertNull(history.range("rpm"))
    }
}
