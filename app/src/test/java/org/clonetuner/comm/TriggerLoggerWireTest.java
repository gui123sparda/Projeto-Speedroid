package org.clonetuner.comm;

import org.clonetuner.ini.IniModel;
import org.clonetuner.model.TriggerCapture;
import org.junit.Test;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static org.junit.Assert.*;

public class TriggerLoggerWireTest {
    @Test public void realLinkUsesIniCommandsAndChecksCaptureCrc() throws Exception {
        var ini = IniModel.load(Path.of("src/main/assets/speeduino.ini"), null);
        var io = new LoggerIo();
        var link = new StreamLink(ini, io);
        link.canId(3);
        link.connect();
        try {
            link.startTriggerLogger(TriggerCapture.Mode.COMPOSITE);
            assertArrayEquals(new byte[]{'J'}, io.command);
            var capture = link.readTriggerLogger(TriggerCapture.Mode.COMPOSITE);
            assertArrayEquals(new byte[]{'T',3,0,0,0,2,123}, io.command);
            assertEquals(127, capture.samples().size());
            assertEquals(1000, capture.samples().get(1).timeUs());
            assertTrue(capture.samples().get(1).crank());
            assertTrue(capture.samples().get(1).cam());
            io.corrupt = true;
            assertThrows(IllegalStateException.class, () -> link.readTriggerLogger(TriggerCapture.Mode.COMPOSITE));
            io.corrupt = false;
            link.stopTriggerLogger(TriggerCapture.Mode.COMPOSITE);
            assertArrayEquals(new byte[]{'j'}, io.command);
            link.startTriggerLogger(TriggerCapture.Mode.TOOTH);
            assertArrayEquals(new byte[]{'H'}, io.command);
            assertEquals(127, link.readTriggerLogger(TriggerCapture.Mode.TOOTH).samples().size());
            assertArrayEquals(new byte[]{'T',3,1,(byte)252,0,1,(byte)252}, io.command);
            link.stopTriggerLogger(TriggerCapture.Mode.TOOTH);
            assertArrayEquals(new byte[]{'h'}, io.command);
        } finally { link.disconnect(); }
        assertFalse(io.open);
    }

    private static class LoggerIo implements SerialIo {
        boolean open, corrupt;
        char mode;
        byte[] command, reply = new byte[0];
        int position;
        public String description() { return "logger firmware peer"; }
        public void open() { open=true; }
        public boolean isOpen() { return open; }
        public void close() { open=false; }
        public int read(byte[] out,int count) {
            int n = Math.min(3, Math.min(count, reply.length-position));
            if(n>0) { System.arraycopy(reply,position,out,0,n); position+=n; }
            return n;
        }
        public int write(byte[] data) {
            command=Arrays.copyOfRange(data,2,data.length-4);
            if(command[0]=='H' || command[0]=='J') mode=(char)command[0];
            if(command[0]=='T') {
                var payload=ByteBuffer.allocate(1+(mode=='J'?635:508));
                payload.put((byte)0);
                for(int i=0;i<127;i++) {
                    payload.putInt(mode=='J'?100000+i*1000:1000);
                    if(mode=='J') payload.put((byte)(i%2==0?0x10:0x13));
                }
                reply=SerialProtocol.frame(payload.array());
                if(corrupt) reply[reply.length-1]^=1;
            } else reply=SerialProtocol.frame(new byte[]{0});
            position=0;
            return data.length;
        }
    }
}
