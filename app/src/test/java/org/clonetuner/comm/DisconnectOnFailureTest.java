package org.clonetuner.comm;

import org.clonetuner.ini.IniModel;
import org.clonetuner.model.Ecu;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class DisconnectOnFailureTest {
    @Test public void lostTelemetryClosesPortAndInvalidatesOldValues() throws Exception {
        IniModel ini = new IniModel();
        ini.signature="test";
        Ecu ecu = new Ecu(ini);
        ecu.setChannel("rpm",3000);
        CountDownLatch failed = new CountDownLatch(1);
        Link peer = new Link() {
            volatile boolean connected;
            public String displayName(){return "lost peer";}
            public boolean isSimulator(){return false;}
            public boolean connect(){connected=true;return true;}
            public void disconnect(){connected=false;failed.countDown();}
            public boolean isConnected(){return connected;}
            public String signature(){return "test";}
            public String version(){return "test";}
            public Map<String,Double> readChannels(){throw new IllegalStateException("unplugged");}
            public byte[] readPage(int p){return new byte[128];}
            public void writePage(int p,byte[] d){}
            public void burn(int p){}
            public void sendCommand(String n,List<String> c){}
        };
        Transport transport=new Transport(ecu);
        transport.connect(peer);
        try {
            assertTrue("Port must close after failed telemetry", failed.await(1500,TimeUnit.MILLISECONDS));
            assertFalse(transport.isConnected());
            assertTrue(ecu.channels().isEmpty());
        } finally {transport.disconnect();}
    }
}
