package org.clonetuner.comm;

import java.util.Arrays;
import java.util.zip.CRC32;

/** Dependency-free protocol checks; run with assertions enabled. */
public final class SerialProtocolTest {
    @org.junit.Test
    public void originalRegression() {
        String[] args = new String[0];
        byte[] read = SerialProtocol.command("p%2i%2o%2c", 0, 3, 256, 128, new byte[0]);
        assert Arrays.equals(read, new byte[] {'p', 0, 3, 0, 1, (byte) 128, 0});
        byte[] live = SerialProtocol.command("r\\$tsCanId\\x30%2o%2c", 0, 0, 0, 130, new byte[0]);
        assert Arrays.equals(live, new byte[] {'r', 0, 0x30, 0, 0, (byte) 130, 0});
        byte[] write = SerialProtocol.command("M%2i%2o%2c%v", 0, 2, 251, 2, new byte[] {7, 9});
        assert Arrays.equals(write, new byte[] {'M', 0, 2, (byte) 251, 0, 2, 0, 7, 9});
        byte[] frame = SerialProtocol.frame(read);
        assert frame.length == read.length + 6;
        assert frame[0] == 0 && frame[1] == read.length;
        CRC32 crc = new CRC32();
        crc.update(new byte[] {0, 42});
        long value = crc.getValue();
        byte[] responseCrc = {(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
        assert Arrays.equals(SerialProtocol.responseData(new byte[] {0, 42}, responseCrc, 1, false), new byte[] {42});
        expectFailure(() -> SerialProtocol.responseData(new byte[] {0, 42}, new byte[4], 1, false));
        expectFailure(() -> SerialProtocol.responseData(new byte[] {0, 42}, responseCrc, 2, false));
        System.out.println("SerialProtocolTest OK");
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("Expected failure"); }
        catch (IllegalStateException expected) { /* expected */ }
    }
}
