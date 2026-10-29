package org.clonetuner.comm

import org.clonetuner.ini.IniModel
import org.clonetuner.model.Ecu
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Path

class CommandFailureTest {
    private val ini get() = IniModel.load(Path.of("src/main/assets/speeduino.ini"), null)

    @Test fun disconnectedCommandReportsFailure() {
        val transport = Transport(Ecu(ini))
        assertThrows(IllegalStateException::class.java) { transport.sendCommand("cmdStopTestMode") }
    }

    @Test fun unknownCommandCannotBeReportedAsSent() {
        val definition = ini
        val transport = Transport(Ecu(definition))
        transport.connect(SimLink(definition))
        try {
            assertThrows(IllegalArgumentException::class.java) { transport.sendCommand("doesNotExist") }
        } finally { transport.disconnect() }
    }

    @Test fun serialCommandFailureReachesTheCaller() {
        val definition = ini
        val transport = Transport(Ecu(definition))
        transport.connect(object : Link by SimLink(definition) {
            override fun sendCommand(commandName: String, rawCommands: List<String>) {
                throw IllegalStateException("Serial write incomplete")
            }
        })
        try {
            assertThrows(IllegalStateException::class.java) { transport.sendCommand("cmdStopTestMode") }
        } finally { transport.disconnect() }
    }
}
