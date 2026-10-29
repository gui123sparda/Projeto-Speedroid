package org.clonetuner.comm;

import org.clonetuner.ini.IniModel;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import static org.junit.Assert.*;

public class GpsVssWireTest {
    @Test public void negotiatesAndWritesGpsThroughTheSameCrcSerialEnvelope() throws Exception {
        Peer peer=new Peer();
        StreamLink link=new StreamLink(new IniModel(),peer);
        link.connect();
        try {
            peer.response=SerialProtocol.frame(new byte[]{0,'G','P','S','V','S','S',1});
            assertArrayEquals(new byte[]{'G','P','S','V','S','S',1},link.gpsVssCapabilities());
            assertArrayEquals(new byte[]{0,3,'g',1,0},Arrays.copyOf(peer.sent.toByteArray(),5));
            peer.position=0;peer.sent.reset();peer.response=SerialProtocol.frame(new byte[]{0});
            link.sendGpsVss(new byte[]{'g',1,1,3,(byte)0xd2,4});
            assertArrayEquals(new byte[]{0,6,'g',1,1,3,(byte)0xd2,4},Arrays.copyOf(peer.sent.toByteArray(),8));
        } finally { link.disconnect(); }
    }
    @Test public void stockFirmwareRejectionIsAValidCapabilityResponse() throws Exception {
        Peer peer=new Peer();
        StreamLink link=new StreamLink(new IniModel(),peer);
        link.connect();
        try {
            peer.response=SerialProtocol.frame(new byte[]{(byte)0x83});
            assertEquals(0,link.gpsVssCapabilities().length);
            assertTrue(link.isConnected());
        } finally { link.disconnect(); }
    }
    private static class Peer implements SerialIo {
        byte[] response=new byte[0];int position;boolean open;
        ByteArrayOutputStream sent=new ByteArrayOutputStream();
        public String description(){return "GPS peer";}
        public void open(){open=true;}
        public boolean isOpen(){return open;}
        public int read(byte[] b,int count){if(position>=response.length)return 0;b[0]=response[position++];return 1;}
        public int write(byte[] b){sent.write(b,0,b.length);return b.length;}
        public void close(){open=false;}
    }
}
