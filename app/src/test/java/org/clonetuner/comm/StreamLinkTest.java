package org.clonetuner.comm;

import org.clonetuner.ini.IniModel;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.Assert.*;

public class StreamLinkTest {
    @Test public void readsFragmentedBluetoothOrUsbFramesAndChecksCrc() throws Exception {
        IniModel ini = new IniModel();
        FakeIo io = new FakeIo();
        StreamLink link = new StreamLink(ini, io);
        link.connect();
        io.reply = SerialProtocol.frame(new byte[] {0,'t','e','s','t'});
        assertEquals("test", link.signature());
        assertArrayEquals(new byte[] {0,1,'Q',(byte)0xce,0x6e,(byte)0x8e,(byte)0xef}, io.sent.toByteArray());
        io.position = 0;
        io.reply[io.reply.length-1] ^= 1;
        try { link.signature(); fail("Corrupt CRC accepted"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("CRC")); }
        link.disconnect();
        assertTrue(io.closed);
    }
    @Test public void failedUnderlyingPortStillGetsClosed() throws Exception {
        FakeIo io = new FakeIo();
        StreamLink link = new StreamLink(new IniModel(), io);
        link.connect();
        io.open = false; // a USB read error can clear its open flag before cleanup
        link.disconnect();
        assertTrue("Disconnected driver must release resources",io.closed);
    }
    private static class FakeIo implements SerialIo {
        boolean open, closed;
        byte[] reply = new byte[0];
        int position;
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        public String description() { return "fragmented serial"; }
        public void open() { open=true; }
        public boolean isOpen() { return open; }
        public int read(byte[] out,int count) { if(position>=reply.length)return 0; out[0]=reply[position++];return 1; }
        public int write(byte[] in) { sent.write(in,0,in.length);return in.length; }
        public void close() { closed=true;open=false; }
    }
}
