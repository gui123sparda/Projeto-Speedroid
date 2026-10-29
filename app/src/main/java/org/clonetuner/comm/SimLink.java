package org.clonetuner.comm;

import org.clonetuner.ini.IniModel;
import org.clonetuner.model.TriggerCapture;
import java.nio.ByteBuffer;
import org.clonetuner.ini.IniModel.Channel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Built-in Speeduino simulator. Produces a realistic idle/cruise stream so every gauge, table and
 * dialog works without hardware, and keeps a writable copy of each tune page.
 */
public final class SimLink implements Link {

    private final IniModel ini;
    private final byte[][] pages;
    private volatile boolean connected;
    private final long start = System.nanoTime();
    private volatile double gpsSpeed = Double.NaN;
    private volatile long gpsReceived;
    private volatile TriggerCapture.Mode triggerLoggerMode;
    public TriggerCapture.Mode triggerLoggerMode() { return triggerLoggerMode; }
    @Override public boolean supportsTriggerLogger(TriggerCapture.Mode mode) {
        try { TriggerLoggerProtocol.definition(ini,mode); return true; }
        catch(UnsupportedOperationException error) { return false; }
    }
    @Override public void startTriggerLogger(TriggerCapture.Mode mode) {
        if(!connected) throw new IllegalStateException("Simulador desconectado");
        if(!supportsTriggerLogger(mode)) throw new UnsupportedOperationException("INI sem logger compatível");
        triggerLoggerMode=mode;
    }
    @Override public void stopTriggerLogger(TriggerCapture.Mode mode) { triggerLoggerMode=null; }
    @Override public TriggerCapture readTriggerLogger(TriggerCapture.Mode mode) {
        if(triggerLoggerMode!=mode || !connected) throw new IllegalStateException("Logger não está ativo");
        var bytes=ByteBuffer.allocate(TriggerLoggerProtocol.byteCount(mode));
        int normal=(int)(60000000.0/(Math.max(600,rpm)*60));
        int timestamp=(int)(System.nanoTime()/1000);
        for(int i=0;i<TriggerLoggerProtocol.RECORDS;i++) {
            if(mode==TriggerCapture.Mode.TOOTH) bytes.putInt(normal*(i%58==57?3:1));
            else {
                timestamp+=normal/2*(i%116==115?5:1);
                boolean cam=(i%116)<12;
                int flags=(i%2==0?1:0)|(cam?2:0)|16|(i%116==12?8:0);
                bytes.putInt(timestamp); bytes.put((byte)flags);
            }
        }
        return TriggerLoggerProtocol.decode(mode,bytes.array(),true);
    }

    private double throttle;
    private double rpm;

    public SimLink(IniModel ini) {
        this.ini = ini;
        this.pages = new byte[Math.max(1, ini.nPages)][];
        for (int i = 0; i < pages.length; i++) {
            pages[i] = new byte[i < ini.pageSize.length ? Math.max(1, ini.pageSize[i]) : 256];
        }
    }

    @Override
    public String displayName() {
        return "Simulator (offline)";
    }

    @Override
    public boolean isSimulator() {
        return true;
    }

    @Override
    public boolean connect() {
        connected = true;
        return true;
    }

    @Override
    public void disconnect() {
        connected = false;
        triggerLoggerMode=null;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public String signature() {
        return ini.signature;
    }

    @Override
    public String version() {
        return "Speeduino simulator 202501 (CloneTuner)";
    }

    @Override
    public byte[] gpsVssCapabilities() { return new byte[] {'G','P','S','V','S','S',1}; }

    @Override
    public void sendGpsVss(byte[] command) {
        int flags = command[3] & 255;
        gpsSpeed = (flags & 1) == 0 ? Double.NaN : (flags & 2) == 0 ? 0 : Math.round(((command[4] & 255) | ((command[5] & 255) << 8)) / 10.0);
        gpsReceived = System.nanoTime();
    }

    @Override
    public Map<String, Double> readChannels() {
        double t = (System.nanoTime() - start) / 1_000_000_000.0;
        // Slow throttle blips around idle, like a warm engine being feathered.
        double target = 2.0 + 8.0 * (0.5 + 0.5 * Math.sin(t * 0.35)) + 4.0 * Math.sin(t * 1.7);
        throttle += (target - throttle) * 0.08;
        double targetRpm = 820 + throttle * 42 + 30 * Math.sin(t * 2.3);
        rpm += (targetRpm - rpm) * 0.06;
        double noise = ThreadLocalRandom.current().nextDouble(-0.4, 0.4);

        double clt = 86 + 0.4 * Math.sin(t * 0.05);
        double iat = 32 + 1.2 * Math.sin(t * 0.11);
        double map = 32 + throttle * 1.55 + 0.6 * Math.sin(t * 1.1);
        double afr = 14.6 + 0.18 * Math.sin(t * 1.4) + noise * 0.05;
        double battery = 13.9 + 0.08 * Math.sin(t * 0.6);
        double advance = 12 + throttle * 0.55 + 1.5 * Math.sin(t * 0.9);
        double pw = 2.1 + throttle * 0.16 + 0.05 * Math.sin(t);
        double duty = 1.8 + throttle * 0.35;

        Map<String, Double> m = new LinkedHashMap<>();
        put(m, "rpm", rpm);
        put(m, "map", map);
        put(m, "tps", throttle);
        put(m, "clt", clt);
        put(m, "coolant", clt);
        put(m, "iat", iat);
        put(m, "mat", iat);
        put(m, "afr", afr);
        put(m, "afr2", afr + 0.05);
        put(m, "lambda", afr / 14.7);
        put(m, "afrTarget", 14.7);
        put(m, "lambdaTarget", 1.0);
        put(m, "batteryVoltage", battery);
        put(m, "baro", 100.4);
        put(m, "advance", advance);
        put(m, "pulseWidth", pw);
        put(m, "pulseWidth2", pw * 0.98);
        put(m, "pulseWidth3", pw * 1.02);
        put(m, "pulseWidth4", pw);
        put(m, "dutyCycle", duty);
        put(m, "egoCorrection", 100 + 1.2 * Math.sin(t * 0.8));
        put(m, "airCorrection", 99.5 + 0.5 * Math.sin(t * 0.3));
        put(m, "warmupEnrich", clt < 70 ? 118 : 100);
        put(m, "gammaEnrich", 100 + 6 * (1 - Math.min(1, clt / 80)));
        put(m, "accelEnrich", 100);
        put(m, "veCurr", 62 + throttle * 0.9);
        put(m, "VE1", 62 + throttle * 0.9);
        put(m, "VE2", 60 + throttle * 0.85);
        put(m, "boostTarget", 100 + throttle * 0.9);
        put(m, "boostDuty", throttle * 0.6);
        put(m, "fuelPressure", 300 + throttle);
        put(m, "oilPressure", 45 + rpm / 120.0);
        put(m, "flex", 8.0);
        put(m, "fuelTemp", 34.0);
        put(m, "CLIdleTarget", 850.0);
        put(m, "idleLoad", 32 + 2 * Math.sin(t * 0.4));
        put(m, "loopsPerSecond", 1680.0);
        put(m, "freeRAM", 1240.0);
        put(m, "secL", t);
        put(m, "rpmDOT", 12 * Math.cos(t * 2.3));
        put(m, "MAPxRPM", map * rpm / 100.0);
        put(m, "speed", 0.0);
        put(m, "gear", 1.0);
        put(m, "fuelTempCor", 100.0);
        put(m, "flexFuelCor", 104.0);
        put(m, "fanDuty", clt > 95 ? 60 : 0);
        put(m, "vvt1Duty", 20.0);
        put(m, "vvt1Target", 30.0);
        put(m, "vvt1Angle", 29.4);
        put(m, "vvt2Duty", 20.0);
        put(m, "vvt2Target", 30.0);
        put(m, "vvt2Angle", 29.8);
        put(m, "stgDutyCycle", 0.0);
        put(m, "idleTarget", 850.0);

        // Status bits exposed by the Speeduino output channel block.
        put(m, "running", 1);
        put(m, "crank", 0);
        put(m, "ase", 0);
        put(m, "warmup", clt < 70 ? 1 : 0);
        put(m, "sync", 1);
        put(m, "error", 0);
        put(m, "DFCOOn", 0);
        put(m, "boostCutFuel", 0);
        put(m, "idleControlOn", 1);
        put(m, "fanStatus", clt > 95 ? 1 : 0);
        put(m, "nitrousOn", 0);
        put(m, "launchHard", 0);
        put(m, "launchSoft", 0);
        put(m, "hardLimitOn", 0);
        put(m, "softlimitOn", 0);
        put(m, "engineProtectStatus", 0);
        put(m, "engineProtectRPM", 0);
        put(m, "engineProtectMAP", 0);
        put(m, "engineProtectOil", 0);
        put(m, "engineProtectAFR", 0);
        put(m, "engineProtectCoolant", 0);
        put(m, "sd_status", 1);
        put(m, "airConRequest", 0);
        put(m, "airConCompressor", 0);
        put(m, "airConFanStatus", 0);
        put(m, "IOError", 0);
        put(m, "burnPending", 0);
        put(m, "tpsaccaen", throttle > 12 ? 1 : 0);
        put(m, "tpsaccden", 0);
        put(m, "mapaccaen", 0);
        put(m, "mapaccden", 0);
        if (Double.isFinite(gpsSpeed)) put(m, "vss", System.nanoTime() - gpsReceived <= 1_500_000_000L ? gpsSpeed : 0);
        put(m,"toothLog1Ready",triggerLoggerMode==null?0.0:1.0);
        return m;
    }

    private void put(Map<String, Double> m, String name, double value) {
        // Only publish channels that the active INI actually declares.
        Channel c = ini.channel(name);
        if (c != null || name.equals("running") || name.equals("sync")) {
            m.put(name, value);
        }
    }

    @Override
    public byte[] readPage(int page) {
        byte[] p = pages[Math.max(0, Math.min(pages.length - 1, page - 1))];
        return p.clone();
    }

    @Override
    public void writePage(int page, byte[] data) {
        byte[] p = pages[Math.max(0, Math.min(pages.length - 1, page - 1))];
        System.arraycopy(data, 0, p, 0, Math.min(p.length, data.length));
    }

    @Override
    public void burn(int page) {
        // Simulator: RAM is EEPROM.
    }

    @Override
    public void sendCommand(String commandName, List<String> rawCommands) {
        // Simulator accepts every controller command silently.
    }

    /** Replaces the simulator page contents with the current tune (used on first connect). */
    public void loadPages(byte[][] source) {
        for (int i = 0; i < pages.length && i < source.length; i++) {
            System.arraycopy(source[i], 0, pages[i], 0, Math.min(pages[i].length, source[i].length));
        }
    }
}
