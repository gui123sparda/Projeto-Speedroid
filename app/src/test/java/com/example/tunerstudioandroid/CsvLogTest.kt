package com.example.tunerstudioandroid

import org.junit.Assert.*
import org.junit.Test

class CsvLogTest {
    @Test fun absentPhoneDataStaysEmptyAndRowsAreBounded() {
        val log = CsvLog(2)
        assertTrue(log.append(listOf(0.0, 1000.0, null)))
        assertTrue(log.append(listOf(0.2, 1200.0, 36.0)))
        assertFalse(log.append(listOf(0.4, 1500.0, 40.0)))
        assertEquals(2, log.size)
        assertEquals("0.000,1000.000,\n0.200,1200.000,36.000\n", log.body())
    }
}
