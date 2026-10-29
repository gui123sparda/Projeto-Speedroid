package org.clonetuner.comm;


import org.clonetuner.ini.IniModel;
import org.clonetuner.model.TriggerCapture;
import org.clonetuner.ini.IniModel.Channel;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MegaSquirt / Speeduino serial protocol.
 *
 * Uses the Speeduino framed serial envelope and commands defined by the active INI.
 */
public final class StreamLink implements Link {

    private final IniModel ini;
    private final SerialIo port;
    private volatile boolean connected;
    private int canId;

    public StreamLink(IniModel ini, SerialIo port) {
        this.ini = ini;
        this.port = port;
    }

    @Override
    public String displayName() {
        return port.description();
    }

    @Override
    public boolean isSimulator() {
        return false;
    }

    @Override
    public boolean connect() throws Exception {
        port.open();
        Thread.sleep(1000);
        connected = true;
        return true;
    }

    @Override
    public void disconnect() {
        connected = false;
        port.close();
    }

    @Override
    public boolean isConnected() {
        return connected && port != null && port.isOpen();
    }

    @Override
    public synchronized String signature() {
        byte[] resp = request(ini.queryCommand.getBytes(StandardCharsets.ISO_8859_1), -1, false);
        String s = new String(resp, StandardCharsets.ISO_8859_1);
        return s.replace("\0", "").trim();
    }

    @Override
    public synchronized String version() {
        byte[] resp = request(ini.versionInfo.getBytes(StandardCharsets.ISO_8859_1), -1, false);
        String s = new String(resp, StandardCharsets.ISO_8859_1);
        int nul = s.indexOf('\0');
        return (nul >= 0 ? s.substring(0, nul) : s).trim();
    }

    @Override
    public synchronized Map<String, Double> readChannels() {
        int size = ini.ochBlockSize > 0 ? ini.ochBlockSize : 130;
        byte[] cmd = realtimeCommand(size);
        byte[] data = request(cmd, size, false);
        Map<String, Double> out = new LinkedHashMap<>();
        for (Channel c : ini.channels.values()) {
            int size2 = c.bytesPerItem();
            if (c.offset + size2 > data.length) {
                continue;
            }
            long raw = 0;
            boolean little = ini.endianness.equalsIgnoreCase("little");
            for (int i = 0; i < size2; i++) {
                int b = data[c.offset + i] & 0xFF;
                if (little) {
                    raw |= (long) b << (8 * i);
                } else {
                    raw = (raw << 8) | b;
                }
            }
            double value;
            if (c.isBits()) {
                long mask = (1L << (c.bitHi - c.bitLo + 1)) - 1;
                value = (raw >> c.bitLo) & mask;
            } else {
                if (c.type.toUpperCase(Locale.ROOT).startsWith("S")) {
                    int bits = size2 * 8;
                    raw = (raw << (64 - bits)) >> (64 - bits);
                }
                double scale = parse(c.scaleRaw, 1);
                double translate = parse(c.translateRaw, 0);
                value = (raw + translate) * scale;
            }
            out.put(c.name, value);
        }
        return out;
    }

    @Override
    public synchronized byte[] gpsVssCapabilities() {
        try { return request(new byte[] {'g', 1, 0}, 7, false); }
        catch (IllegalStateException error) {
            if (error.getMessage().contains("0x83")) return new byte[0];
            throw error;
        }
    }

    @Override
    public synchronized void sendGpsVss(byte[] command) {
        if (command.length != 6 || command[0] != 'g' || command[1] != 1 || command[2] != 1)
            throw new IllegalArgumentException("Invalid GPS VSS packet");
        request(command, 0, false);
    }

    @Override public boolean supportsTriggerLogger(TriggerCapture.Mode mode) {
        try { TriggerLoggerProtocol.definition(ini,mode); return ini.signature.toLowerCase(Locale.ROOT).startsWith("speeduino "); }
        catch(UnsupportedOperationException error) { return false; }
    }
    @Override public synchronized void startTriggerLogger(TriggerCapture.Mode mode) {
        if(!supportsTriggerLogger(mode)) throw new UnsupportedOperationException("Logger não suportado pelo INI atual");
        request(TriggerLoggerProtocol.definition(ini,mode).startCommand.getBytes(StandardCharsets.ISO_8859_1),0,false);
    }
    @Override public synchronized void stopTriggerLogger(TriggerCapture.Mode mode) {
        request(TriggerLoggerProtocol.definition(ini,mode).stopCommand.getBytes(StandardCharsets.ISO_8859_1),0,false);
    }
    @Override public synchronized TriggerCapture readTriggerLogger(TriggerCapture.Mode mode) {
        byte[] command=SerialProtocol.command(TriggerLoggerProtocol.definition(ini,mode).readCommand,canId,0,0,0,new byte[0]);
        return TriggerLoggerProtocol.decode(mode,request(command,TriggerLoggerProtocol.byteCount(mode),false),false);
    }

    private double parse(String s, double def) {
        try {
            return s == null ? def : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private byte[] realtimeCommand(int count) {
        String template = ini.ochGetCommand;
        if (template == null || template.isBlank()) {
            template = "r\\$tsCanId\\x30%2o%2c";
        }
        return SerialProtocol.command(template, canId, 0, 0, count, new byte[0]);
    }

    @Override
    public synchronized byte[] readPage(int page) {
        int size = page - 1 < ini.pageSize.length ? ini.pageSize[page - 1] : 256;
        byte[] all = new byte[size];
        int block = Math.max(1, Math.min(ini.blockingFactor > 0 ? ini.blockingFactor : 128, size));
        for (int off = 0; off < size; off += block) {
            int len = Math.min(block, size - off);
            byte[] cmd = SerialProtocol.command("p%2i%2o%2c", canId, page, off, len, new byte[0]);
            byte[] resp = request(cmd, len, false);
            System.arraycopy(resp, 0, all, off, len);
        }
        return all;
    }

    @Override
    public synchronized void writePage(int page, byte[] data) {
        // blockingFactor limits the entire payload, including M + CAN/page + offset/count.
        int block = Math.max(1, (ini.blockingFactor > 0 ? ini.blockingFactor : 128) - 7);
        int size = page - 1 < ini.pageSize.length ? ini.pageSize[page - 1] : data.length;
        if (data.length < size) {
            throw new IllegalArgumentException("Page " + page + " is shorter than its INI size");
        }
        for (int off = 0; off < size; off += block) {
            int len = Math.min(block, size - off);
            byte[] values = java.util.Arrays.copyOfRange(data, off, off + len);
            byte[] cmd = SerialProtocol.command("M%2i%2o%2c%v", canId, page, off, len, values);
            request(cmd, 0, false);
        }
    }

    @Override
    public synchronized void burn(int page) {
        request(SerialProtocol.command("b%2i", canId, page, 0, 0, new byte[0]), 0, true);
    }

    @Override
    public synchronized void sendCommand(String commandName, List<String> rawCommands) {
        for (String c : rawCommands) {
            if (c == null || c.isBlank()) {
                continue;
            }
            request(c.getBytes(StandardCharsets.ISO_8859_1), 0, false);
        }
    }

    // ------------------------------------------------------------------ framing

    private void send(byte[] data) {
        if (!isConnected()) {
            throw new IllegalStateException("Serial port is not open");
        }
        long written = port.write(data);
        if (written != data.length) {
            throw new IllegalStateException("Serial write incomplete: " + written + "/" + data.length);
        }
    }

    private byte[] request(byte[] command, int expected, boolean burn) {
        try {
            send(SerialProtocol.frame(command));
            byte[] header = readFully(2);
            int len = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
            if (len <= 0 || len > 4096) {
                throw new IllegalStateException("Invalid controller response length: " + len);
            }
            return SerialProtocol.responseData(readFully(len), readFully(4), expected, burn);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Serial command '" + (char) command[0] + "' ("
                    + command.length + " bytes): " + error.getMessage(), error);
        }
    }

    private byte[] readFully(int count) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + Math.max(2000, count * 12L);
        byte[] buf = new byte[Math.max(1, count)];
        while (out.size() < count && System.currentTimeMillis() < deadline) {
            int n = port.read(buf, Math.min(buf.length, count - out.size()));
            if (n > 0) {
                out.write(buf, 0, n);
            } else {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (out.size() != count) {
            throw new IllegalStateException("Serial read timed out: " + out.size() + "/" + count + " bytes");
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return port.description();
    }

    public int canId() {
        return canId;
    }

    public void canId(int id) {
        this.canId = id;
    }
}
