package org.clonetuner.comm;

import org.clonetuner.ini.IniModel;
import org.clonetuner.model.Ecu;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class BurnTransactionTest {
    @org.junit.Test
    public void originalRegression() throws Exception {
        String[] args = new String[0];
        IniModel ini = IniModel.load(Path.of("src/main/assets/speeduino.ini"), null);
        Ecu ecu = new Ecu(ini);
        ControlledLink link = new ControlledLink(ini);
        Transport transport = new Transport(ecu);
        transport.connect(link);
        ecu.set("pinLayout", 1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread burn = new Thread(() -> { try { transport.burnDirty(); } catch (Throwable error) { failure.set(error); } });
        try {
            burn.start();
            if (!link.writing.await(2, TimeUnit.SECONDS)) throw new AssertionError("Burn never started");
            boolean interleaved = link.telemetryDuringWrite.await(250, TimeUnit.MILLISECONDS);
            // An edit made while the older page is being transmitted must remain pending.
            ecu.set("pinLayout", 3);
            link.resume.countDown();
            burn.join(2000);
            if (burn.isAlive()) throw new AssertionError("Burn did not finish");
            if (interleaved) throw new AssertionError("Telemetry request interleaved with write/burn transaction");
            if (failure.get() != null) throw new AssertionError(failure.get());
            if (!ecu.isDirty()) throw new AssertionError("Burn cleared a newer local edit that was never transmitted");
            System.out.println("BurnTransactionTest OK");
        } finally { link.resume.countDown(); transport.disconnect(); }
    }

    private static final class ControlledLink implements Link {
        private final byte[][] pages;
        private final String signature;
        private volatile boolean connected;
        private volatile boolean inWrite;
        private final CountDownLatch writing = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);
        private final CountDownLatch telemetryDuringWrite = new CountDownLatch(1);
        ControlledLink(IniModel ini) {
            signature = ini.signature;
            pages = new byte[ini.nPages][];
            for (int i = 0; i < pages.length; i++) pages[i] = new byte[ini.pageSize[i]];
        }
        public String displayName() { return "controlled serial peer"; }
        public boolean isSimulator() { return false; }
        public boolean connect() { return connected = true; }
        public void disconnect() { connected = false; }
        public boolean isConnected() { return connected; }
        public String signature() { return signature; }
        public String version() { return "test"; }
        public Map<String, Double> readChannels() {
            if (inWrite) telemetryDuringWrite.countDown();
            return Map.of("rpm", 0.0);
        }
        public byte[] readPage(int page) { return pages[page - 1].clone(); }
        public void writePage(int page, byte[] data) {
            byte[] sent = data.clone();
            inWrite = true;
            writing.countDown();
            try { if (!resume.await(2, TimeUnit.SECONDS)) throw new AssertionError("Test did not release write"); }
            catch (InterruptedException ex) { throw new RuntimeException(ex); }
            pages[page - 1] = sent;
        }
        public void burn(int page) { inWrite = false; }
        public void sendCommand(String name, List<String> commands) {}
    }
}
