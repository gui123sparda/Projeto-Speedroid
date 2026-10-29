package org.clonetuner.comm

import org.clonetuner.ini.IniModel
import org.clonetuner.model.Ecu
import org.clonetuner.model.TriggerCapture
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TriggerLoggerTest {
    private fun ini() = IniModel.load(Path.of("src/main/assets/speeduino.ini"), null)
    private fun toothBytes(): ByteArray = ByteBuffer.allocate(508).order(ByteOrder.BIG_ENDIAN).apply {
        repeat(126) { putInt(1000) }; putInt(3000)
    }.array()

    @Test fun toothIntervalsAreUnsignedBigEndianAndLongGapsRemainVisible() {
        val capture = TriggerLoggerProtocol.decode(TriggerCapture.Mode.TOOTH, toothBytes(), false)
        assertEquals(127, capture.samples().size)
        assertEquals(1000L, capture.samples()[0].intervalUs())
        assertEquals(129000L, capture.samples().last().timeUs())
        assertEquals(3000L, capture.samples().last().intervalUs())
        assertEquals(1, capture.longIntervals())
        assertFalse(capture.simulated())
    }

    @Test fun compositeUsesTimestampThenFlagsAndUnwrapsMicrosOverflow() {
        val bytes = ByteBuffer.allocate(635).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(-16); put(0x10)
            putInt(16); put(0x11)
            putInt(48); put(0x1b)
            repeat(124) { putInt(48); put(0) } // firmware padding
        }.array()
        val capture = TriggerLoggerProtocol.decode(TriggerCapture.Mode.COMPOSITE, bytes, false)
        assertEquals(3, capture.samples().size)
        assertEquals(0L, capture.samples()[0].timeUs())
        assertEquals(32L, capture.samples()[1].timeUs())
        assertEquals(64L, capture.samples()[2].timeUs())
        assertTrue(capture.samples()[2].crank())
        assertTrue(capture.samples()[2].cam())
        assertTrue(capture.samples()[2].sync())
        assertTrue(capture.samples()[2].secondaryTrigger())
        assertEquals(1, capture.crankEdges())
        assertEquals(1, capture.camEdges())
        assertTrue(capture.toCsv().contains("ECU,COMPOSITE,2,64,32,1,1,1,1"))
    }

    @Test fun paddedEmptyCaptureIsNotInventedSignalAndMalformedLengthsAreRejected() {
        assertTrue(TriggerLoggerProtocol.decode(TriggerCapture.Mode.TOOTH, ByteArray(508), false).samples().isEmpty())
        assertTrue(TriggerLoggerProtocol.decode(TriggerCapture.Mode.COMPOSITE, ByteArray(635), false).samples().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { TriggerLoggerProtocol.decode(TriggerCapture.Mode.COMPOSITE, ByteArray(634), false) }
    }

    @Test fun bundledIniProvidesSupportedLoggerDefinitions() {
        val definition = ini()
        val tooth = TriggerLoggerProtocol.definition(definition, TriggerCapture.Mode.TOOTH)
        val composite = TriggerLoggerProtocol.definition(definition, TriggerCapture.Mode.COMPOSITE)
        assertEquals("H", tooth.startCommand)
        assertEquals("h", tooth.stopCommand)
        assertEquals("J", composite.startCommand)
        assertEquals("j", composite.stopCommand)
        assertArrayEquals(byteArrayOf(84,0,0,0,0,2,123), SerialProtocol.command(composite.readCommand,0,0,0,0,byteArrayOf()))
        assertThrows(UnsupportedOperationException::class.java) { TriggerLoggerProtocol.definition(IniModel(), TriggerCapture.Mode.TOOTH) }
    }

    @Test fun transportCapturesInSerialLockAndStopsBeforeDisconnect() {
        val definition = ini()
        val arrived = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val sim = SimLink(definition)
        val link = object : Link by sim {
            override fun supportsTriggerLogger(mode: TriggerCapture.Mode) = sim.supportsTriggerLogger(mode)
            override fun startTriggerLogger(mode: TriggerCapture.Mode) = sim.startTriggerLogger(mode)
            override fun readTriggerLogger(mode: TriggerCapture.Mode): TriggerCapture {
                assertEquals(mode, sim.triggerLoggerMode())
                return sim.readTriggerLogger(mode).also { arrived.countDown() }
            }
            override fun stopTriggerLogger(mode: TriggerCapture.Mode) { sim.stopTriggerLogger(mode); stopped.countDown() }
            override fun disconnect() { assertNull(sim.triggerLoggerMode()); sim.disconnect() }
        }
        val transport = Transport(Ecu(definition))
        transport.connect(link)
        try {
            transport.startTriggerLogger(TriggerCapture.Mode.COMPOSITE)
            assertTrue(arrived.await(3, TimeUnit.SECONDS))
            assertTrue(transport.triggerLoggerActive())
            assertThrows(IllegalStateException::class.java) { transport.downloadPages() }
            assertThrows(IllegalStateException::class.java) { transport.burnDirty() }
            assertThrows(IllegalStateException::class.java) { transport.sendCommand("cmdStopTestMode") }
        } finally { transport.disconnect() }
        assertTrue(stopped.await(1, TimeUnit.SECONDS))
        assertFalse(transport.triggerLoggerActive())
        assertTrue(transport.triggerCapture().simulated())
    }

    @Test fun loggerReadFailureStopsCaptureAndClosesTheSerialSession() {
        val definition = ini()
        val sim = SimLink(definition)
        val closed = CountDownLatch(1)
        val transport = Transport(Ecu(definition))
        transport.connect(object : Link by sim {
            override fun supportsTriggerLogger(mode: TriggerCapture.Mode) = sim.supportsTriggerLogger(mode)
            override fun startTriggerLogger(mode: TriggerCapture.Mode) = sim.startTriggerLogger(mode)
            override fun stopTriggerLogger(mode: TriggerCapture.Mode) = sim.stopTriggerLogger(mode)
            override fun readTriggerLogger(mode: TriggerCapture.Mode): TriggerCapture { throw IllegalStateException("CRC mismatch") }
            override fun disconnect() { sim.disconnect(); closed.countDown() }
        })
        transport.startTriggerLogger(TriggerCapture.Mode.TOOTH)
        assertTrue(closed.await(3,TimeUnit.SECONDS))
        assertFalse(transport.triggerLoggerActive())
        assertFalse(transport.isConnected)
        assertNotNull(transport.triggerLoggerError())
    }
    @Test fun noReadyFlagDoesNotReadOrInventAPulseBlock() {
        val definition=ini()
        val sim=SimLink(definition)
        val reads=CountDownLatch(3)
        val transport=Transport(Ecu(definition))
        transport.connect(object : Link by sim {
            override fun supportsTriggerLogger(mode: TriggerCapture.Mode) = sim.supportsTriggerLogger(mode)
            override fun startTriggerLogger(mode: TriggerCapture.Mode) = sim.startTriggerLogger(mode)
            override fun stopTriggerLogger(mode: TriggerCapture.Mode) = sim.stopTriggerLogger(mode)
            override fun readTriggerLogger(mode: TriggerCapture.Mode): TriggerCapture { throw AssertionError("Read before firmware ready") }
            override fun readChannels(): Map<String,Double> = sim.readChannels().toMutableMap().apply {
                put("toothLog1Ready",0.0)
                if(sim.triggerLoggerMode()!=null) reads.countDown()
            }
        })
        try {
            transport.startTriggerLogger(TriggerCapture.Mode.COMPOSITE)
            assertTrue(reads.await(3,TimeUnit.SECONDS))
            assertNull(transport.triggerCapture())
            assertTrue(transport.isConnected)
        } finally { transport.disconnect() }
    }

    @Test fun failedStopClosesConnectionAndNextConnectionDiscardsPreviousCapture() {
        val definition=ini()
        val sim=SimLink(definition)
        val ready=CountDownLatch(1)
        val transport=Transport(Ecu(definition))
        transport.connect(object : Link by sim {
            override fun supportsTriggerLogger(mode: TriggerCapture.Mode) = sim.supportsTriggerLogger(mode)
            override fun startTriggerLogger(mode: TriggerCapture.Mode) = sim.startTriggerLogger(mode)
            override fun stopTriggerLogger(mode: TriggerCapture.Mode) { throw IllegalStateException("Lost stop acknowledgement") }
            override fun readTriggerLogger(mode: TriggerCapture.Mode) = sim.readTriggerLogger(mode).also { ready.countDown() }
        })
        transport.startTriggerLogger(TriggerCapture.Mode.TOOTH)
        assertTrue(ready.await(3,TimeUnit.SECONDS))
        assertThrows(IllegalStateException::class.java) { transport.stopTriggerLogger() }
        assertFalse(transport.isConnected)
        assertFalse(transport.triggerLoggerActive())
        assertNotNull(transport.triggerLoggerError())
        transport.connect(SimLink(definition))
        try {
            assertNull(transport.triggerCapture())
            assertNull(transport.triggerLoggerError())
        } finally { transport.disconnect() }
    }

}
