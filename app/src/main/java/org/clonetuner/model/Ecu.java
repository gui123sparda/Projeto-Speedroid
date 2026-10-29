package org.clonetuner.model;

import org.clonetuner.ini.Expr;
import org.clonetuner.ini.IniModel;
import org.clonetuner.ini.IniModel.Channel;
import org.clonetuner.ini.IniModel.Constant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live ECU state: the tune pages (RAM copy of every constant) plus the last realtime channel block.
 * All values are exposed as user units, applying each constant's scale/translate.
 */
public final class Ecu {

    private final IniModel ini;
    private final byte[][] pages;
    private final Map<String, Double> channels = new ConcurrentHashMap<>();
    private final Map<String, Double> pcValues = new ConcurrentHashMap<>();
    private final Set<Integer> dirtyPages = ConcurrentHashMap.newKeySet();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Expr.Ctx ctx = new Expr.Ctx() {
        @Override
        public double num(String name) {
            return valueOf(name);
        }

        @Override
        public String str(String name) {
            return null;
        }

        @Override
        public double array(String name, int index) {
            return arrayValue(name, index);
        }

        @Override
        public String bitString(String name, int index) {
            return bitStringValue(name, index);
        }
    };

    public Ecu(IniModel ini) {
        this.ini = ini;
        this.pages = new byte[Math.max(1, ini.nPages)][];
        for (int i = 0; i < pages.length; i++) {
            int size = i < ini.pageSize.length ? ini.pageSize[i] : 256;
            pages[i] = new byte[Math.max(1, size)];
        }
        for (Constant c : ini.constants.values()) {
            ensure(c.page, c.offset + Math.max(1, c.length()) * c.bytesPerItem());
        }
        seedDefaults();
    }

    public IniModel ini() {
        return ini;
    }

    // ------------------------------------------------------------------ defaults

    /** Gives an offline tune plausible values so the UI is immediately usable. */
    private void seedDefaults() {
        // PC variables first: constant defaults may be expressions that reference them.
        for (Constant c : ini.pcScalars.values()) {
            pcValues.put(c.name, pcDefault(c));
        }
        for (Constant c : ini.pcBits.values()) {
            pcValues.put(c.name, pcDefault(c));
        }
        for (Constant c : ini.constants.values()) {
            List<String> dv = ini.defaults.get(c.name);
            int n = Math.max(1, c.length());
            if (c.isBits()) {
                // setBits, not setRaw: several bits constants share one byte and a
                // whole-byte write would clobber the neighbours (e.g. algorithm/nInjectors).
                setBits(c, dv == null || dv.isEmpty() ? 0 : tokenValue(c, dv.get(0)));
                continue;
            }
            double lo = num(c.loRaw, 0);
            double hi = num(c.hiRaw, 0);
            double base = hi > lo ? lo + (hi - lo) * 0.5 : 0;
            for (int i = 0; i < n; i++) {
                double v;
                if (dv != null && !dv.isEmpty()) {
                    v = tokenValue(c, dv.get(Math.min(i, dv.size() - 1)));
                } else if (c.isArray() && n > 1 && hi > lo) {
                    // Arrays without defaults get a ramp across their range, which reads
                    // much better than a flat mid plane (axes become usable immediately).
                    v = lo + (hi - lo) * ((double) i / (n - 1));
                } else {
                    v = base;
                }
                setRaw(c, i, v);
            }
        }
        // Fuel/spark tables read better with a gentle gradient than a flat mid plane.
        gradient("veTable", 35, 95);
        gradient("advTable1", 10, 38);
        gradient("afrTable", 12.5, 14.7);
    }

    /** Starts a new local tune from the INI defaults. It is never sent until Burn is chosen. */
    public void resetTune() {
        for (byte[] page : pages) {
            java.util.Arrays.fill(page, (byte) 0);
        }
        seedDefaults();
        for (int i = 0; i < pages.length; i++) {
            dirtyPages.add(i);
        }
        fireChanged();
    }

    private void gradient(String name, double from, double to) {
        Constant c = ini.constant(name);
        if (c == null || !c.isArray()) {
            return;
        }
        int rows = c.rows();
        int cols = c.cols();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                double tx = cols <= 1 ? 0 : (double) x / (cols - 1);
                double ty = rows <= 1 ? 0 : (double) y / (rows - 1);
                double v = from + (to - from) * (0.65 * tx + 0.35 * ty);
                setRaw(c, y * cols + x, v);
            }
        }
    }

    // ------------------------------------------------------------------ constants

    public Constant constant(String name) {
        return ini.constant(name);
    }

    public double get(String name) {
        Constant c = ini.constant(name);
        if (c == null) {
            return valueOf(name);
        }
        return get(c, 0);
    }

    public double get(String name, int index) {
        Constant c = ini.constant(name);
        return c == null ? 0 : get(c, index);
    }

    public double get(Constant c, int index) {
        if (c.page <= 0) {
            return pcValues.getOrDefault(c.name, pcDefault(c));
        }
        if (c.isBits()) {
            return bitsValue(c, index);
        }
        long raw = readRaw(c.page, c.offset + index * c.bytesPerItem(), c.type);
        return (raw + num(c.translateRaw, 0)) * num(c.scaleRaw, 1);
    }

    private double bitsValue(Constant c, int index) {
        long raw = readRaw(c.page, c.offset, c.type);
        long mask = (1L << (c.bitHi - c.bitLo + 1)) - 1;
        return (raw >> c.bitLo) & mask;
    }

    public void set(String name, double value) {
        set(name, 0, value);
    }

    public void set(String name, int index, double value) {
        Constant c = ini.constant(name);
        if (c == null) {
            pcValues.put(name, value);
            fireChanged();
            return;
        }
        if (c.page <= 0) {
            pcValues.put(c.name, value);
            fireChanged();
            return;
        }
        if (c.isBits()) {
            setBits(c, value);
        } else {
            setRaw(c, index, value);
        }
        dirtyPages.add(c.page - 1);
        fireChanged();
    }

    public void setRaw(Constant c, int index, double userValue) {
        if (c.page <= 0) {
            pcValues.put(c.name, userValue);
            return;
        }
        double scale = num(c.scaleRaw, 1);
        double translate = num(c.translateRaw, 0);
        double raw = scale == 0 ? userValue : userValue / scale - translate;
        writeRaw(c.page, c.offset + index * c.bytesPerItem(), c.type, Math.round(raw));
    }

    private void setBits(Constant c, double option) {
        long cur = readRaw(c.page, c.offset, c.type);
        int width = c.bitHi - c.bitLo + 1;
        long mask = (1L << width) - 1;
        long clean = cur & ~(mask << c.bitLo);
        long val = ((long) option & mask) << c.bitLo;
        writeRaw(c.page, c.offset, c.type, clean | val);
    }

    public double[] array(String name) {
        Constant c = ini.constant(name);
        if (c == null) {
            return new double[0];
        }
        double[] out = new double[c.length()];
        for (int i = 0; i < out.length; i++) {
            out[i] = get(c, i);
        }
        return out;
    }

    public void setArray(String name, double[] values) {
        Constant c = ini.constant(name);
        if (c == null) {
            return;
        }
        for (int i = 0; i < values.length && i < c.length(); i++) {
            setRaw(c, i, values[i]);
        }
        dirtyPages.add(Math.max(0, c.page - 1));
        fireChanged();
    }

    public String optionLabel(Constant c, int index) {
        List<String> opts = c.options;
        if (opts.isEmpty()) {
            return String.valueOf(index);
        }
        if (index < 0 || index >= opts.size()) {
            return String.valueOf(index);
        }
        return opts.get(index);
    }

    public List<String> visibleOptions(Constant c) {
        List<String> out = new ArrayList<>();
        for (String s : c.options) {
            String v = s.trim();
            if (v.equalsIgnoreCase("INVALID")) {
                continue;
            }
            out.add(v);
        }
        return out;
    }

    public int visibleOptionIndex(Constant c, double value) {
        int actual = (int) Math.round(value);
        int vis = 0;
        for (int i = 0; i < c.options.size(); i++) {
            if (c.options.get(i).trim().equalsIgnoreCase("INVALID")) {
                continue;
            }
            if (i == actual) {
                return vis;
            }
            vis++;
        }
        return Math.max(0, Math.min(Math.max(0, vis - 1), actual));
    }

    /** Maps a position in {@link #visibleOptions(Constant)} back to the raw option index. */
    public int actualOptionIndex(Constant c, int visibleIndex) {
        int vis = 0;
        for (int i = 0; i < c.options.size(); i++) {
            if (c.options.get(i).trim().equalsIgnoreCase("INVALID")) {
                continue;
            }
            if (vis == visibleIndex) {
                return i;
            }
            vis++;
        }
        return visibleIndex;
    }

    public String units(Constant c) {
        if (c == null) {
            return "";
        }
        String u = c.units == null ? "" : c.units.trim();
        if (u.startsWith("{")) {
            String s = Expr.text(u, ctx);
            return s == null ? "" : s;
        }
        return u;
    }

    public String format(Constant c, double value) {
        int d = c == null ? 2 : c.digits;
        return String.format(Locale.ROOT, "%." + Math.max(0, d) + "f", value);
    }

    public String display(String constantName) {
        Constant c = ini.constant(constantName);
        if (c == null) {
            return "";
        }
        if (c.isBits()) {
            int idx = (int) Math.round(get(c, 0));
            return optionLabel(c, idx);
        }
        return format(c, get(c, 0));
    }

    private double pcDefault(Constant c) {
        List<String> vals = ini.defaults.get(c.name);
        if (vals == null || vals.isEmpty()) {
            return c.isBits() ? 0 : num(c.loRaw, 0);
        }
        return tokenValue(c, vals.get(0));
    }

    /** Resolves a defaultValue token: option label for enumerations, expression/number otherwise. */
    private double tokenValue(Constant c, String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        String t = token.trim();
        if (!c.options.isEmpty()) {
            for (int i = 0; i < c.options.size(); i++) {
                if (c.options.get(i).trim().equalsIgnoreCase(t)) {
                    return i;
                }
            }
        }
        return num(t, 0);
    }

    // ------------------------------------------------------------------ channels

    public double channel(String name) {
        Double v = channels.get(name);
        return v == null ? 0 : v;
    }

    public String channelText(Channel c, int digits) {
        double v = channel(c.name);
        return String.format(Locale.ROOT, "%." + Math.max(0, digits) + "f", v);
    }

    public Map<String, Double> channels() {
        return channels;
    }

    public void updateChannels(Map<String, Double> values) {
        channels.putAll(values);
    }

    public void setChannel(String name, double value) {
        channels.put(name, value);
    }

    public double channelUnitsScale(Channel c) {
        return num(c.scaleRaw, 1);
    }

    // ------------------------------------------------------------------ pages

    public byte[] page(int page) {
        return pages[Math.max(0, Math.min(pages.length - 1, page - 1))];
    }

    public byte[][] allPages() {
        return pages;
    }

    public Set<Integer> dirtyPages() {
        return dirtyPages;
    }

    public boolean isDirty() {
        return !dirtyPages.isEmpty();
    }

    public void markClean() {
        dirtyPages.clear();
        fireChanged();
    }

    public void markPageClean(int pageIndex) {
        dirtyPages.remove(pageIndex);
        fireChanged();
    }

    public void markDirty(int page) {
        dirtyPages.add(page);
    }

    public void replacePage(int page, byte[] data) {
        byte[] target = page(page);
        System.arraycopy(data, 0, target, 0, Math.min(target.length, data.length));
        fireChanged();
    }

    private void ensure(int page, int size) {
        int idx = Math.max(0, Math.min(pages.length - 1, page - 1));
        if (pages[idx] == null) {
            pages[idx] = new byte[Math.max(1, size)];
        } else if (pages[idx].length < size) {
            byte[] bigger = new byte[size];
            System.arraycopy(pages[idx], 0, bigger, 0, pages[idx].length);
            pages[idx] = bigger;
        }
    }

    public long readRaw(int page, int offset, String type) {
        byte[] data = page(page);
        int o = offset;
        int size = switch (type.toUpperCase(Locale.ROOT)) {
            case "U08", "S08" -> 1;
            case "U16", "S16" -> 2;
            case "U32", "S32", "F32" -> 4;
            default -> 1;
        };
        if (o < 0) {
            return 0;
        }
        if (o + size > data.length) {
            ensure(page, o + size);
            data = page(page);
        }
        long v = 0;
        boolean little = ini.endianness.equalsIgnoreCase("little");
        for (int i = 0; i < size; i++) {
            int b = data[o + i] & 0xFF;
            if (little) {
                v |= (long) b << (8 * i);
            } else {
                v = (v << 8) | b;
            }
        }
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "S08" -> (byte) v;
            case "S16" -> (short) v;
            case "S32" -> (int) v;
            default -> v;
        };
    }

    public void writeRaw(int page, int offset, String type, long value) {
        String t = type.toUpperCase(Locale.ROOT);
        int size = switch (t) {
            case "U08", "S08" -> 1;
            case "U16", "S16" -> 2;
            case "U32", "S32", "F32" -> 4;
            default -> 1;
        };
        // Clamp instead of silently wrapping: ramps that overshoot the type range
        // (e.g. fuelLoadBins 0..511 with scale 2 in a U08) must stay monotonic.
        value = switch (t) {
            case "U08" -> Math.max(0L, Math.min(255L, value));
            case "S08" -> Math.max(-128L, Math.min(127L, value));
            case "U16" -> Math.max(0L, Math.min(65535L, value));
            case "S16" -> Math.max(-32768L, Math.min(32767L, value));
            case "U32" -> Math.max(0L, Math.min(0xFFFFFFFFL, value));
            case "S32" -> Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
            default -> value;
        };
        ensure(page, offset + size);
        byte[] data = page(page);
        boolean little = ini.endianness.equalsIgnoreCase("little");
        for (int i = 0; i < size; i++) {
            int shift = little ? 8 * i : 8 * (size - 1 - i);
            data[offset + i] = (byte) ((value >> shift) & 0xFF);
        }
    }

    // ------------------------------------------------------------------ expression context

    private double valueOf(String name) {
        if (name == null) {
            return 0;
        }
        // Derived [OutputChannels] entries (fuelLoadMax, throttle, ...) are computed
        // from the live state and take precedence over same-named PC constants.
        Channel oc = ini.channel(name);
        if (oc != null && oc.isExpr()) {
            if (exprDepth > 32) {
                return 0;
            }
            exprDepth++;
            try {
                return Expr.eval(oc.expr, ctx);
            } catch (RuntimeException e) {
                return 0;
            } finally {
                exprDepth--;
            }
        }
        Constant c = ini.constant(name);
        if (c != null) {
            return get(c, 0);
        }
        Constant pc = ini.pcBits.get(name);
        if (pc != null) {
            return pcValues.getOrDefault(name, 0.0);
        }
        String expr = ini.pcExprs.get(name);
        if (expr != null) {
            return Expr.eval(expr, ctx);
        }
        Double ch = channels.get(name);
        if (ch != null) {
            return ch;
        }
        return 0;
    }

    private int exprDepth;

    public Expr.Ctx ctx() {
        return ctx;
    }

    public String stringValue(String name) {
        if (ini.pcLists.containsKey(name)) {
            return null;
        }
        return null;
    }

    public double arrayValue(String name, int index) {
        Constant c = ini.constant(name);
        if (c != null) {
            return get(c, index);
        }
        List<String> list = ini.pcLists.get(name);
        if (list != null && index >= 0 && index < list.size()) {
            try {
                return Double.parseDouble(list.get(index));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public String bitStringValue(String name, int index) {
        Constant pc = ini.pcBits.get(name);
        if (pc != null && index >= 0 && index < pc.options.size()) {
            return pc.options.get(index);
        }
        Constant c = ini.constant(name);
        if (c != null && index >= 0 && index < c.options.size()) {
            return c.options.get(index);
        }
        List<String> list = ini.pcLists.get(name);
        if (list != null && index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return "";
    }

    public String textValue(String expression) {
        return Expr.text(expression, ctx);
    }

    public boolean test(String expression) {
        return Expr.test(expression, ctx);
    }

    public double num(String expression, double def) {
        if (expression == null || expression.isBlank()) {
            return def;
        }
        String s = expression.trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            return Expr.eval(s, ctx);
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Expr.eval(s, ctx);
        }
    }

    // ------------------------------------------------------------------ listeners

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    public void fireChanged() {
        for (Runnable r : listeners) {
            r.run();
        }
    }

    // ------------------------------------------------------------------ tables

    public double[][] tableValues(String zBins) {
        Constant c = ini.constant(zBins);
        if (c == null) {
            return new double[0][0];
        }
        int rows = c.rows();
        int cols = c.cols();
        double[][] out = new double[rows][cols];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                out[y][x] = get(c, y * cols + x);
            }
        }
        return out;
    }

    public void setTableValue(String zBins, int row, int col, double value) {
        Constant c = ini.constant(zBins);
        if (c == null) {
            return;
        }
        int cols = c.cols();
        setRaw(c, row * cols + col, value);
        dirtyPages.add(Math.max(0, c.page - 1));
        fireChanged();
    }

    public Map<String, Double> snapshotConstants() {
        Map<String, Double> out = new LinkedHashMap<>();
        ini.constants.forEach((name, c) -> out.put(name, get(c, 0)));
        return out;
    }
}
