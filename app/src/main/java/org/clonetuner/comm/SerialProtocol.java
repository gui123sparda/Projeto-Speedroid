package org.clonetuner.comm;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.CRC32;

/** The Speeduino 1.0 serial envelope and INI command substitutions. */
final class SerialProtocol {
    private SerialProtocol() {}

    static byte[] frame(byte[] payload) {
        if (payload.length == 0 || payload.length > 0xffff) {
            throw new IllegalArgumentException("Invalid serial payload length: " + payload.length);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 6);
        out.write(payload.length >> 8);
        out.write(payload.length);
        out.write(payload, 0, payload.length);
        writeCrc(out, payload);
        return out.toByteArray();
    }

    static byte[] responseData(byte[] payload, byte[] crc, int expectedLength, boolean burn) {
        if (payload.length == 0 || crc.length != 4) {
            throw new IllegalStateException("Incomplete controller response");
        }
        CRC32 checksum = new CRC32();
        checksum.update(payload);
        long received = ((long) (crc[0] & 255) << 24) | ((long) (crc[1] & 255) << 16)
                | ((long) (crc[2] & 255) << 8) | (crc[3] & 255);
        if (checksum.getValue() != received) {
            throw new IllegalStateException("Controller response CRC mismatch");
        }
        int status = payload[0] & 255;
        if (status != 0 && !(burn && status == 4)) {
            throw new IllegalStateException(String.format("Controller error 0x%02X", status));
        }
        byte[] data = Arrays.copyOfRange(payload, 1, payload.length);
        if (expectedLength >= 0 && data.length != expectedLength) {
            throw new IllegalStateException("Controller returned " + data.length + " bytes; expected " + expectedLength);
        }
        return data;
    }

    static byte[] command(String template, int canId, int page, int offset, int count, byte[] values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < template.length();) {
            if (template.startsWith("\\$tsCanId", i)) {
                out.write(canId); i += 9;
            } else if (template.startsWith("$tsCanId", i)) {
                out.write(canId); i += 8;
            } else if (template.startsWith("\\x", i) && i + 3 < template.length()) {
                out.write(Integer.parseInt(template.substring(i + 2, i + 4), 16)); i += 4;
            } else if (template.startsWith("%2i", i)) {
                out.write(canId); out.write(page); i += 3;
            } else if (template.startsWith("%2o", i)) {
                writeShortLittle(out, offset); i += 3;
            } else if (template.startsWith("%2c", i)) {
                writeShortLittle(out, count); i += 3;
            } else if (template.startsWith("%v", i)) {
                out.write(values, 0, values.length); i += 2;
            } else if (template.charAt(i) == '\\' && i + 1 < template.length()) {
                out.write(template.charAt(i + 1)); i += 2;
            } else {
                out.write(template.charAt(i++));
            }
        }
        return out.toByteArray();
    }

    private static void writeShortLittle(ByteArrayOutputStream out, int value) {
        out.write(value);
        out.write(value >> 8);
    }

    private static void writeCrc(ByteArrayOutputStream out, byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload);
        long value = crc.getValue();
        out.write((int) (value >> 24)); out.write((int) (value >> 16));
        out.write((int) (value >> 8)); out.write((int) value);
    }
}
