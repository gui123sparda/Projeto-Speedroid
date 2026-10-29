package org.clonetuner.comm;

import org.clonetuner.ini.IniModel;
import org.clonetuner.model.Ecu;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Verifies page numbering and that failed burns retain pending changes. */
public final class TransportTest {
    @org.junit.Test
    public void originalRegression() throws Exception {
        String[] args = new String[0];
        IniModel ini = IniModel.load(Path.of("src/main/assets/speeduino.ini"), null);
        Ecu ecu = new Ecu(ini);
        FakeLink link = new FakeLink(ini);
        Transport transport = new Transport(ecu);
        transport.connect(link);
        ecu.set("pinLayout", 1);
        if (ecu.dirtyPages().isEmpty()) throw new AssertionError("Edit must be pending");
        int expectedPage = ecu.dirtyPages().iterator().next() + 1;
        link.rejectBurn = true;
        try { transport.burnDirty(); throw new AssertionError("Expected burn failure"); }
        catch (IllegalStateException expected) { /* expected */ }
        if (link.lastWrittenPage != expectedPage || !ecu.isDirty()) {
            throw new AssertionError("Failed burn changed page numbering or cleared dirty state");
        }
        link.rejectBurn = false;
        transport.burnDirty();
        if (ecu.isDirty() || link.lastBurnedPage != expectedPage) {
            throw new AssertionError("Successful burn did not clean the correct page");
        }
        ecu.set("pinLayout", 3);
        int pending = ecu.dirtyPages().size();
        FakeLink next = new FakeLink(ini);
        transport.connect(next);
        if (!ecu.isDirty() || ecu.dirtyPages().size() != pending || next.pageReads != 0) {
            throw new AssertionError("Connecting replaced a pending local tune");
        }
        next.failPage = 2;
        byte[] firstPage = ecu.page(1).clone();
        try { transport.downloadPages(); throw new AssertionError("Expected page failure"); }
        catch (IllegalStateException expected) { /* expected */ }
        if (!Arrays.equals(firstPage, ecu.page(1)) || !ecu.isDirty()) {
            throw new AssertionError("Partial download changed local tune");
        }
        ecu.setChannel("rpm", 3500);
        transport.disconnect();
        if (!ecu.channels().isEmpty()) throw new AssertionError("Offline instruments must not retain old live readings");
        System.out.println("TransportTest OK");
    }

    private static final class FakeLink implements Link {
        private final IniModel ini;
        private boolean connected;
        private boolean rejectBurn;
        private int lastWrittenPage;
        private int lastBurnedPage;
        private int pageReads;
        private int failPage;
        private final byte[][] pages;
        private FakeLink(IniModel ini) {
            this.ini = ini;
            pages = new byte[ini.nPages][];
            for (int i = 0; i < pages.length; i++) pages[i] = new byte[ini.pageSize[i]];
        }
        public String displayName() { return "fake"; }
        public boolean isSimulator() { return false; }
        public boolean connect() { connected = true; return true; }
        public void disconnect() { connected = false; }
        public boolean isConnected() { return connected; }
        public String signature() { return ini.signature; }
        public String version() { return "test"; }
        public Map<String, Double> readChannels() { return Map.of(); }
        public byte[] readPage(int page) {
            pageReads++;
            if (page == failPage) throw new IllegalStateException("read failed");
            return pages[page - 1].clone();
        }
        public void writePage(int page, byte[] data) { lastWrittenPage = page; pages[page - 1] = data.clone(); }
        public void burn(int page) { if (rejectBurn) throw new IllegalStateException("rejected"); lastBurnedPage = page; }
        public void sendCommand(String name, List<String> commands) {}
    }
}
