package org.clonetuner.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

/** Offline, advisory fuel analysis. Never reads or modifies tune pages or a serial link. */
public final class FuelTrimAdvisor {
    private FuelTrimAdvisor() { }

    public record Settings(double targetAfr, double minimumClt, int minimumSamples, double maximumCorrection) {
        public Settings {
            if (!Double.isFinite(targetAfr) || targetAfr < 8 || targetAfr > 22
                    || !Double.isFinite(minimumClt) || minimumClt < -40 || minimumClt > 150
                    || minimumSamples < 3 || minimumSamples > 10000
                    || !Double.isFinite(maximumCorrection) || maximumCorrection <= 0 || maximumCorrection > 10)
                throw new IllegalArgumentException("AFR: 8–22; CLT: -40–150 °C; samples: 3–10000; correction: >0–10%.");
        }
    }
    public record Cell(int rpm, int map, int samples, double rawPercent, double suggestedPercent,
                       double spread, boolean ready) { }
    public record Report(List<Cell> cells, int accepted, int rejected, boolean loggedTarget, boolean loggedEgo) {
        public Report { cells = List.copyOf(cells); }
    }
    private record Sample(double time, double rpm, double map, double tps, double clt, double afr,
                          double target, double ego) { }

    public static Report analyze(Reader input, Settings settings) throws IOException {
        BufferedReader reader = new BufferedReader(input);
        String header = reader.readLine();
        if (header == null) throw new IOException("Empty datalog.");
        List<String> names = fields(header.replace("\ufeff", ""));
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String key = names.get(i).strip().toLowerCase(Locale.ROOT);
            if (columns.put(key, i) != null) throw new IOException("Duplicate CSV column: " + key);
        }
        if (!columns.containsKey("ego correction") && columns.containsKey("gego"))
            columns.put("ego correction", columns.get("gego"));
        for (String key : List.of("time (s)", "rpm", "map", "tps", "clt", "afr"))
            if (!columns.containsKey(key)) throw new IOException("Missing column: " + key
                    + ". Required: Time (s), RPM, MAP, TPS, CLT, AFR.");
        Map<Integer, List<Double>> regions = new TreeMap<>();
        int accepted = 0, rejected = 0, lines = 0;
        long characters = header.length();
        Sample previous = null;
        String line;
        while ((line = reader.readLine()) != null) {
            characters += line.length();
            if (++lines > 200000 || characters > 20 * 1024 * 1024) throw new IOException("Datalog exceeds 200000 rows or 20 MiB.");
            if (line.isBlank()) continue;
            List<String> row = fields(line);
            Sample current;
            try {
                if (row.size() != names.size()) throw new NumberFormatException();
                current = new Sample(value(row, columns, "time (s)", 0), value(row, columns, "rpm", 0),
                        value(row, columns, "map", 0), value(row, columns, "tps", 0),
                        value(row, columns, "clt", 0), value(row, columns, "afr", 0),
                        value(row, columns, "afr target", settings.targetAfr()),
                        value(row, columns, "ego correction", 100));
            } catch (NumberFormatException e) { rejected++; previous = null; continue; }
            double dt = previous == null ? 0 : current.time - previous.time;
            boolean stable = previous != null && dt > 0 && dt <= .5
                    && Math.abs(current.tps - previous.tps) / dt <= 10
                    && Math.abs(current.map - previous.map) / dt <= 20
                    && Math.abs(current.rpm - previous.rpm) / dt <= 600;
            boolean valid = current.time >= 0 && current.rpm >= 600 && current.rpm <= 15000
                    && current.map >= 10 && current.map <= 400 && current.tps >= 0 && current.tps <= 100
                    && current.clt >= settings.minimumClt() && current.clt <= 150
                    && current.afr >= 8 && current.afr <= 22 && current.target >= 8 && current.target <= 22
                    && current.ego >= 50 && current.ego <= 150;
            previous = valid ? current : null;
            if (!valid || !stable) { rejected++; continue; }
            int rpm = (int) (current.rpm / 500) * 500;
            int map = (int) (current.map / 10) * 10;
            // Include any logged closed-loop trim in the inferred base-fuel correction.
            double correction = (current.afr / current.target * current.ego / 100 - 1) * 100;
            regions.computeIfAbsent(rpm * 1000 + map, ignored -> new ArrayList<>()).add(correction);
            accepted++;
        }
        List<Cell> cells = new ArrayList<>();
        for (var entry : regions.entrySet()) {
            List<Double> values = entry.getValue();
            Collections.sort(values);
            double median = quantile(values, .5);
            double spread = quantile(values, .9) - quantile(values, .1);
            cells.add(new Cell(entry.getKey() / 1000, entry.getKey() % 1000, values.size(), median,
                    Math.max(-settings.maximumCorrection(), Math.min(settings.maximumCorrection(), median)),
                    spread, values.size() >= settings.minimumSamples() && spread <= 10));
        }
        return new Report(cells, accepted, rejected, columns.containsKey("afr target"), columns.containsKey("ego correction"));
    }
    private static double value(List<String> row, Map<String, Integer> columns, String key, double fallback) {
        Integer index = columns.get(key);
        if (index == null) return fallback;
        double result = Double.parseDouble(row.get(index).strip());
        if (!Double.isFinite(result)) throw new NumberFormatException();
        return result;
    }
    private static double quantile(List<Double> values, double q) {
        double index = (values.size() - 1) * q;
        int lo = (int) index, hi = (int) Math.ceil(index);
        return values.get(lo) + (values.get(hi) - values.get(lo)) * (index - lo);
    }
    /** One CSV record per line; escaped quotes and commas inside quoted fields are supported. */
    private static List<String> fields(String line) throws IOException {
        List<String> result = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, closed = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else field.append(c);
            } else if (c == ',') {
                result.add(field.toString()); field.setLength(0); closed = false;
            } else if (c == '"' && field.length() == 0 && !closed) quoted = true;
            else if (closed || c == '"') throw new IOException("Malformed CSV quoting.");
            else field.append(c);
        }
        if (quoted) throw new IOException("Unterminated CSV quote; use one record per line.");
        result.add(field.toString());
        return result;
    }
}
