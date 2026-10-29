package com.example.tunerstudioandroid.vss

import org.clonetuner.comm.Link
import org.junit.Assert.*
import org.junit.Test

class GpsVssBridgeTest {
    @Test fun negotiatesOnceAndLimitsPacketsToFiveHz() {
        val peer=Peer()
        val bridge=GpsVssBridge()
        bridge.tick(peer,true,GpsFix(100.0,1000),1000)
        bridge.tick(peer,true,GpsFix(100.0,1000),1100)
        bridge.tick(peer,true,GpsFix(100.0,1000),1200)
        assertEquals(1,peer.queries)
        assertEquals(2,peer.sent.size)
        assertEquals(GpsVssBridge.Status.ACTIVE,bridge.status)
        bridge.tick(peer,true,GpsFix(100.0,1000),2600)
        assertArrayEquals(byteArrayOf(103,1,1,1,0,0),peer.sent.last())
        assertEquals(GpsVssBridge.Status.NO_FIX,bridge.status)
        bridge.tick(peer,false,null,2800)
        assertArrayEquals(byteArrayOf(103,1,1,0,0,0),peer.sent.last())
    }
    @Test fun stockFirmwareNeverReceivesSpeedPackets() {
        val peer=Peer().apply { supported=false }
        val bridge=GpsVssBridge()
        bridge.tick(peer,true,GpsFix(80.0,1000),1000)
        bridge.tick(peer,true,GpsFix(80.0,1000),1300)
        assertEquals(1,peer.queries)
        assertTrue(peer.sent.isEmpty())
        assertEquals(GpsVssBridge.Status.UNSUPPORTED,bridge.status)
    }
    private class Peer:Link {
        var queries=0; var supported=true; val sent=mutableListOf<ByteArray>()
        override fun gpsVssCapabilities():ByteArray { queries++;return if(supported)byteArrayOf(71,80,83,86,83,83,1) else byteArrayOf() }
        override fun sendGpsVss(command:ByteArray) { sent.add(command) }
        override fun displayName()="test"
        override fun isSimulator()=false
        override fun isConnected()=true
        override fun connect()=true
        override fun disconnect() { }
        override fun signature()="test"
        override fun version()="test"
        override fun readChannels()=mapOf<String,Double>()
        override fun readPage(page:Int)=byteArrayOf()
        override fun writePage(page:Int,data:ByteArray) { }
        override fun burn(page:Int) { }
        override fun sendCommand(name:String,commands:List<String>) { }
    }
}
