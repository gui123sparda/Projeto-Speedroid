package org.clonetuner.comm;

import org.clonetuner.model.TriggerCapture;
import java.util.List;
import java.util.Map;

/** Transport abstraction: real serial link or the built-in simulator. */
public interface Link {

    String displayName();

    boolean isSimulator();

    boolean connect() throws Exception;

    void disconnect();

    boolean isConnected();

    String signature();

    String version();

    /** Optional GPS VSS extension; stock firmware returns no capabilities. */
    default byte[] gpsVssCapabilities() { return new byte[0]; }
    default void sendGpsVss(byte[] command) { throw new UnsupportedOperationException("GPS VSS extension unavailable"); }

    default boolean supportsTriggerLogger(TriggerCapture.Mode mode) { return false; }
    default void startTriggerLogger(TriggerCapture.Mode mode) { throw new UnsupportedOperationException("Logger não disponível neste transporte"); }
    default void stopTriggerLogger(TriggerCapture.Mode mode) { throw new UnsupportedOperationException("Logger não disponível neste transporte"); }
    default TriggerCapture readTriggerLogger(TriggerCapture.Mode mode) { throw new UnsupportedOperationException("Logger não disponível neste transporte"); }

    Map<String, Double> readChannels();

    byte[] readPage(int page);

    void writePage(int page, byte[] data);

    void burn(int page);

    void sendCommand(String commandName, List<String> rawCommands);

}
