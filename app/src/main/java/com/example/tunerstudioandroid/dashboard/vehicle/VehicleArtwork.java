package com.example.tunerstudioandroid.dashboard.vehicle;

import com.example.tunerstudioandroid.dashboard.vehicle.VectorPainter.*;
import com.example.tunerstudioandroid.dashboard.vehicle.VehicleTelemetry.Reading;
import java.util.List;

/** Original custom cluster artwork ported from TunerSpeeduino, retaining every path and scale. */
public final class VehicleArtwork {
    private static final Color INK = Color.web("#e4e4dc");
    private static final Color DIM = Color.web("#3f443f");
    private final VehicleTelemetry telemetry;
    private final VehicleModel model;
    private final VectorPainter painter = new VectorPainter();
    public VehicleArtwork(VehicleTelemetry telemetry, VehicleModel model) {
        this.telemetry = telemetry;
        this.model = model;
    }
    public List<VectorPainter.Command> draw() {
        VectorPainter g = painter;
        g.clear();
        g.setTextBaseline(VPos.CENTER);
        g.setLineCap(StrokeLineCap.BUTT);
        switch (model) {
            case ESCORT_ZETEC -> drawEscort(g);
            case MONDEO_1997 -> drawMondeo(g);
            case RALLY -> drawRally(g);
        }
        return painter.getCommands();
    }

    private void drawEscort(VectorPainter g) {
        shell(g, true);
        // 96FB-10C956-BA: small left tach, large central speedometer,
        // fuel above-left of coolant inside ONE shared right-hand face.
        g.save();
        dial(g, 250, 378, 138, 7000, 1000, 200, 210, 235, telemetry.rpm(), true, true);
        dial(g, 600, 348, 190, 220, 20, 5, 220, 220, telemetry.speedKmh(), false, true);
        face(g, 950, 378, 138);
        auxiliary(g, 908, 356, 62, telemetry.fuelPercent(), false);
        auxiliary(g, 990, 418, 58, telemetry.coolantC(), true);
        g.restore();
        // Reference cluster: direction indicators flank the speedometer; oil,
        // battery and brake symbols follow the lower arc inside the tachometer.
        warning(g, 405, 255, "left", telemetry.active("leftTurn"), true);
        warning(g, 795, 255, "right", telemetry.active("rightTurn"), true);
        warning(g, 810, 288, "high", telemetry.active("highBeam"), true);
        warning(g, 205, 466, "oil", telemetry.active("engineProtectOil", "oilWarning"), false);
        warning(g, 260, 478, "battery", lowBattery(), false);
        warning(g, 315, 451, "brake", telemetry.active("brakeWarning"), true);
        odometer(g, 692, 380, 106, telemetry.read("odometer", "distanceTotal"), false);
        odometer(g, 600, 493, 119, telemetry.read("tripDistance", "trip"), true);
        // Needles sit in front of the face and mechanical odometer windows.
        needle(g, 250, 378, 138 * .93, 210 - 235 * telemetry.rpm().fraction(0, 7000), telemetry.rpm().available());
        needle(g, 600, 348, 190 * .93, 220 - 220 * telemetry.speedKmh().fraction(0, 220), telemetry.speedKmh().available());
        resetStem(g, 700, 480);
        dataState(g);
    }

    private void drawMondeo(VectorPainter g) {
        shell(g, false);
        // 1997 petrol cluster: temperature and fuel low at the outer edges,
        // with two large dials and the warning strips above the small gauges.
        g.save();
        dial(g, 410, 345, 166, 8000, 1000, 200, 220, 260, telemetry.rpm(), true, false);
        dial(g, 790, 345, 166, 240, 20, 5, 220, 260, telemetry.speedKmh(), false, false);
        face(g, 155, 432, 82);
        face(g, 1045, 432, 82);
        auxiliary(g, 155, 453, 64, telemetry.coolantC(), true);
        auxiliary(g, 1045, 453, 64, telemetry.fuelPercent(), false);
        g.restore();
        warning(g, 149, 270, "SRS", telemetry.active("airbagWarning"), false);
        warning(g, 123, 315, "ABS", telemetry.active("absWarning"), false);
        warning(g, 178, 315, "brake", telemetry.active("brakeWarning"), false);
        warning(g, 1010, 315, "battery", lowBattery(), false);
        warning(g, 1070, 315, "oil", telemetry.active("engineProtectOil", "oilWarning"), false);
        warning(g, 1010, 270, "high", telemetry.active("highBeam"), false);
        arrow(g, 576, 205, false, telemetry.active("leftTurn"));
        arrow(g, 624, 205, true, telemetry.active("rightTurn"));
        odometer(g, 790, 294, 107, telemetry.read("odometer", "distanceTotal"), false);
        odometer(g, 790, 405, 105, telemetry.read("tripDistance", "trip"), true);
        needle(g, 410, 345, 166 * .93, 220 - 260 * telemetry.rpm().fraction(0, 8000), telemetry.rpm().available());
        needle(g, 790, 345, 166 * .93, 220 - 260 * telemetry.speedKmh().fraction(0, 240), telemetry.speedKmh().available());
        resetStem(g, 876, 458);
        dataState(g);
    }

    private void shell(VectorPainter g, boolean escort) {
        g.save();
        g.setFill(Color.web(escort ? "#444b4e" : "#181b1c"));
        g.beginPath();
        g.moveTo(60, 482);
        if (escort) {
            g.bezierCurveTo(66, 280, 115, 190, 315, 132);
            g.bezierCurveTo(495, 78, 765, 78, 952, 139);
            g.bezierCurveTo(1092, 186, 1132, 281, 1140, 482);
        } else {
            g.lineTo(68, 321);
            g.bezierCurveTo(76, 170, 261, 100, 600, 91);
            g.bezierCurveTo(939, 100, 1124, 170, 1132, 321);
            g.lineTo(1140, 482);
        }
        g.quadraticCurveTo(1145, 555, 1075, 557);
        if (escort) {
            g.quadraticCurveTo(825, 560, 600, 555);
            g.quadraticCurveTo(375, 560, 125, 557);
        } else {
            g.lineTo(125, 557);
        }
        g.quadraticCurveTo(55, 555, 60, 482);
        g.closePath();
        g.fill();
        g.setStroke(Color.web("#535859"));
        g.setLineWidth(4);
        g.stroke();
        g.setStroke(Color.web("#070909"));
        g.setLineWidth(1);
        g.stroke();
        g.restore();
    }

    private void face(VectorPainter g, double cx, double cy, double r) {
        g.setFill(Color.web("#070909"));
        g.fillOval(cx - r - 5, cy - r - 5, 2 * r + 10, 2 * r + 10);
        g.setFill(Color.web(model == VehicleModel.ESCORT_ZETEC ? "#e3e5db" : "#121617"));
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setStroke(Color.web("#393d3e"));
        g.setLineWidth(1.2);
        g.strokeOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setStroke(Color.web("#080b0c"));
        g.setLineWidth(3);
        g.strokeOval(cx - r - 3, cy - r - 3, 2 * r + 6, 2 * r + 6);
    }

    private void dial(VectorPainter g, double cx, double cy, double r, double max,
                      double labelStep, double minorStep, double start, double sweep,
                      Reading reading, boolean tach, boolean escort) {
        Color ink = model == VehicleModel.ESCORT_ZETEC ? Color.web("#272e2c") : INK;
        face(g, cx, cy, r);
        int divisions = (int) Math.round(max / minorStep);
        for (int i = 0; i <= divisions; i++) {
            double value = i * minorStep;
            boolean major = Math.abs(value / labelStep - Math.rint(value / labelStep)) < .0001;
            double angle = Math.toRadians(start - sweep * value / max);
            double outer = r * .94, inner = r * (major ? .83 : .886);
            g.setStroke(tach && value >= (escort ? 6000 : 6500) ? Color.web("#d24c41") : ink);
            g.setLineWidth(major ? r * .022 : r * .009);
            g.strokeLine(cx + Math.cos(angle) * inner, cy - Math.sin(angle) * inner,
                    cx + Math.cos(angle) * outer, cy - Math.sin(angle) * outer);
            if (major && (tach || i > 0)) {
                double labelR = r * (tach ? .70 : .66);
                String label = Integer.toString((int) (tach ? value / 1000 : value));
                text(g, label, cx + Math.cos(angle) * labelR, cy - Math.sin(angle) * labelR,
                        r * (tach ? .14 : .105), ink);
            }
        }
        if (tach) {
            text(g, "x 1000", cx, cy + r * (escort ? .34 : .68), 13, ink);
        } else {
            text(g, "km/h", cx, escort ? cy - r * .27 : cy + r * .70, 17, ink);
            if (!escort) text(g, "km", cx, cy - r * .43, 11, ink);
        }
        if (!reading.available()) text(g, "--", cx, cy + 30, 13, Color.web("#858b88"));
    }

    private void auxiliary(VectorPainter g, double cx, double cy, double r, Reading reading, boolean temp) {
        Color ink = model == VehicleModel.ESCORT_ZETEC ? Color.web("#272e2c") : INK;
        for (int i = 0; i <= 8; i++) {
            double angle = Math.toRadians(135 - i * 90.0 / 8);
            g.setStroke(temp && i == 8 ? Color.web("#dc4a3b") : ink);
            g.setLineWidth(i % 4 == 0 ? 5 : 3);
            double inner = r * (i % 4 == 0 ? .73 : .82), outer = r * .98;
            g.strokeLine(cx + Math.cos(angle) * inner, cy - Math.sin(angle) * inner,
                    cx + Math.cos(angle) * outer, cy - Math.sin(angle) * outer);
        }
        text(g, temp ? "NORMAL" : "1/2", cx, cy - r * .46, temp ? 10 : 11, ink);
        if (!temp) {
            text(g, "E", cx - r * .90, cy - r * .30, 11, ink);
            text(g, "F", cx + r * .90, cy - r * .30, 11, ink);
        }
        symbol(g, cx - 27, cy + (temp ? 8 : 15), temp ? "coolant" : "fuel", ink, .72);
        // C/H is a display calibration: 40–140 °C, with 90 °C at the middle mark.
        double fraction = temp ? reading.fraction(40, 140) : reading.fraction(0, 100);
        needle(g, cx, cy, r * .95, 135 - 90 * fraction, reading.available());
        if (!reading.available()) text(g, "--", cx + 20, cy + 16, 11, Color.web("#8e9490"));
    }

    private void needle(VectorPainter g, double cx, double cy, double length, double angle, boolean available) {
        g.save();
        g.translate(cx, cy);
        g.rotate(-angle);
        // The black pivot surrounds the blade; the red needle stays visible across it.
        double hub = Math.max(5, length * .115);
        g.setFill(new LinearGradient(-hub, -hub, hub, hub, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#24282a")), new Stop(.5, Color.web("#111415")),
                new Stop(1, Color.web("#050707"))));
        g.fillOval(-hub, -hub, hub * 2, hub * 2);
        g.setStroke(Color.web("#020404"));
        g.setLineWidth(1.2);
        g.strokeOval(-hub, -hub, hub * 2, hub * 2);
        // Profile traced proportionally from the supplied Escort replacement needle:
        // pointed rear tail, broad shoulder and a long, gently tapering front blade.
        double width = Math.max(1.3, length * .03);
        double[] xs = {-length * .38, -length * .10, length * .985,
                length, length * .985, -length * .10};
        double[] ys = {0, -width, -width * .18, 0, width * .18, width};
        g.save();
        g.translate(1, 1.5);
        g.setFill(Color.color(0, 0, 0, .4));
        g.fillPolygon(xs, ys, 6);
        g.restore();
        g.setGlobalAlpha(available ? 1 : .38);
        g.setFill(Color.web("#ee3228"));
        g.fillPolygon(xs, ys, 6);
        g.setFill(Color.web("#ff795c"));
        g.fillPolygon(new double[] {-length * .38, -length * .10, length * .985, length},
                new double[] {0, -width, -width * .18, 0}, 4);
        g.restore();
    }

    private void odometer(VectorPainter g, double cx, double cy, double width, Reading value, boolean trip) {
        int digits = trip ? 4 : 6;
        String display = "------".substring(0, digits);
        if (value.available() && value.getValue() >= 0) {
            long count = (long) (value.getValue() * (trip ? 10 : 1));
            display = String.format(java.util.Locale.ROOT, "%0" + digits + "d", count);
            display = display.substring(Math.max(0, display.length() - digits));
        }
        g.setFill(Color.web("#080a09"));
        g.fillRoundRect(cx - width / 2, cy - 11, width, 23, 3, 3);
        g.setStroke(Color.web("#363c39"));
        g.setLineWidth(1);
        g.strokeRoundRect(cx - width / 2, cy - 11, width, 23, 3, 3);
        double cell = width / digits;
        for (int i = 0; i < digits; i++) {
            double x = cx - width / 2 + cell * (i + .5);
            g.setFill(trip && i == digits - 1 ? Color.web("#a7aa99") : Color.web("#1c211d"));
            g.fillRect(x - cell / 2 + 1, cy - 9, cell - 2, 19);
            text(g, display.substring(i, i + 1), x, cy + 1, 16,
                    trip && i == digits - 1 ? Color.web("#20241f") : INK);
        }
    }

    private void resetStem(VectorPainter g, double x, double y) {
        g.setFill(Color.web("#060808"));
        g.fillOval(x - 9, y - 9, 18, 18);
        g.setStroke(Color.web("#4a4d4d"));
        g.setLineWidth(1.4);
        g.strokeOval(x - 8, y - 8, 16, 16);
        g.setFill(Color.web("#242828"));
        g.fillRoundRect(x - 5, y - 18, 10, 19, 4, 4);
    }

    private boolean lowBattery() {
        Reading battery = telemetry.read("batteryVoltage");
        return battery.available() && battery.getValue() < 11.7;
    }

    private void dataState(VectorPainter g) {
        if (!telemetry.rpm().available() && !telemetry.speedKmh().available() && !telemetry.coolantC().available()) {
            text(g, "SEM DADOS DA ECU", 600, 592, 11, Color.web("#727b79"));
        }
    }

    private void warning(VectorPainter g, double x, double y, String kind, boolean active, boolean round) {
        g.setFill(model == VehicleModel.ESCORT_ZETEC && y > 400 ? Color.TRANSPARENT : Color.web("#121617"));
        if (round) g.fillOval(x - 16, y - 16, 32, 32);
        else g.fillRoundRect(x - 25, y - 12, 50, 24, 18, 18);
        Color lit = kind.equals("high") ? Color.web("#5195ed") : (kind.equals("turn") || kind.equals("left") || kind.equals("right"))
                ? Color.web("#5ed581") : kind.equals("ABS") || kind.equals("SRS")
                ? Color.web("#e5ae41") : Color.web("#ed4a38");
        symbol(g, x, y, kind, active ? lit : model == VehicleModel.ESCORT_ZETEC && y > 400 ? Color.web("#454d46") : DIM, .78);
    }

    private void arrow(VectorPainter g, double x, double y, boolean right, boolean active) {
        g.save();
        g.translate(x, y);
        if (!right) g.scale(-1, 1);
        g.setStroke(active ? Color.web("#5ed581") : DIM);
        g.setLineWidth(2);
        g.strokePolygon(new double[] {-11, 1, 1, 12, 1, 1, -11},
                new double[] {-4, -4, -9, 0, 9, 4, 4}, 7);
        g.restore();
    }

    private void symbol(VectorPainter g, double x, double y, String kind, Color color, double scale) {
        g.save();
        g.translate(x, y);
        g.scale(scale, scale);
        g.setStroke(color);
        g.setFill(color);
        g.setLineWidth(1.8);
        switch (kind) {
            case "battery" -> {
                g.strokeRect(-14, -8, 28, 17);
                g.strokeLine(-9, -11, -3, -11); g.strokeLine(5, -11, 11, -11);
                g.strokeLine(-10, 0, -4, 0); g.strokeLine(5, 0, 11, 0); g.strokeLine(8, -3, 8, 3);
            }
            case "oil" -> {
                g.strokePolygon(new double[] {-13, -4, -4, 5, 10, 16, 8, 4, -8},
                        new double[] {-5, -5, -10, -10, -5, -8, 5, 7, 7}, 9);
                g.strokeOval(-19, -5, 7, 9); g.fillOval(16, 5, 4, 6);
            }
            case "fuel" -> {
                g.strokeRect(-9, -11, 14, 24); g.strokeRect(-6, -8, 8, 8);
                g.strokeLine(-12, 14, 8, 14); g.strokeLine(5, -5, 13, 0);
                g.strokeLine(13, 0, 13, 9); g.strokeArc(7, 5, 6, 8, 180, 180, ArcType.OPEN);
                g.strokeLine(9, -12, 16, -5); g.strokeLine(16, -5, 16, 1);
            }
            case "coolant" -> {
                g.strokeRoundRect(-3, -14, 6, 23, 4, 4); g.strokeOval(-6, 5, 12, 12);
                g.strokeLine(3, -9, 9, -9); g.strokeLine(3, -3, 9, -3);
                for (int i = -1; i <= 1; i++) g.strokeArc(i * 10 - 6, 17, 12, 5, 0, 180, ArcType.OPEN);
            }
            case "brake" -> {
                g.strokeOval(-11, -11, 22, 22); g.strokeArc(-18, -14, 36, 28, 140, 80, ArcType.OPEN);
                g.strokeArc(-18, -14, 36, 28, -40, 80, ArcType.OPEN);
                text(g, "!", 0, 1, 16, color);
            }
            case "high" -> {
                g.strokeArc(-2, -10, 20, 20, -90, 180, ArcType.OPEN); g.strokeLine(8, -10, 8, 10);
                for (int i = -2; i <= 2; i++) g.strokeLine(-15, i * 4, 3, i * 4);
            }
            case "left" -> arrow(g, 0, 0, false, color != DIM);
            case "right" -> arrow(g, 0, 0, true, color != DIM);
            case "turn" -> { arrow(g, -9, 0, false, color != DIM); arrow(g, 9, 0, true, color != DIM); }
            default -> text(g, kind, 0, 0, 12, color);
        }
        g.restore();
    }

    private void drawRally(VectorPainter g) {
        // Competition display: shift scale, primary readings, then eight sensor cells.
        g.setFill(new LinearGradient(0, 45, 0, 575, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#39434b")), new Stop(.1, Color.web("#20282e")),
                new Stop(1, Color.web("#10161b"))));
        g.fillRoundRect(30, 45, 1140, 530, 44, 44);
        g.setStroke(Color.web("#59636b")); g.setLineWidth(2);
        g.strokeRoundRect(30, 45, 1140, 530, 44, 44);
        g.setFill(Color.web("#05090c"));
        g.fillRoundRect(78, 85, 1044, 455, 12, 12);
        g.setStroke(Color.web("#303a40")); g.setLineWidth(1);
        g.strokeRoundRect(78, 85, 1044, 455, 12, 12);
        for (double x : new double[] {53, 1147}) {
            for (double y : new double[] {75, 545}) {
                g.setFill(Color.web("#090d10")); g.fillOval(x - 6, y - 6, 12, 12);
                g.setStroke(Color.web("#69737a")); g.strokeLine(x - 3, y, x + 3, y);
            }
        }
        text(g, "RALLY", 163, 115, 20, INK);
        text(g, "TELEMETRY", 274, 116, 11, Color.web("#94a4ae"));
        statusFlag(g, 560, 115, "RUN", telemetry.active("running"), false);
        statusFlag(g, 710, 115, "SYNC", telemetry.active("sync"), false);
        statusFlag(g, 860, 115, "ERR", telemetry.active("error"), true);
        statusFlag(g, 1010, 115, "DATA", telemetry.rpm().available(), false);
        drawRpmBar(g, 130, 176, 940, 25);
        digitalValue(g, 325, 285, telemetry.rpm().text(0), "RPM");
        digitalValue(g, 875, 285, telemetry.speedKmh().text(0), "km/h");
        g.setFill(Color.web("#111c21")); g.fillRoundRect(535, 227, 130, 133, 12, 12);
        g.setStroke(Color.web("#43565e")); g.strokeRoundRect(535, 227, 130, 133, 12, 12);
        Reading gear = telemetry.read("gear");
        text(g, gear.available() ? gear.getValue() == 0 ? "N" : gear.text(0) : "--", 600, 283, 76, Color.web("#d4ef75"));
        text(g, "GEAR", 600, 339, 12, Color.web("#a4b2bd"));
        g.setStroke(Color.web("#26343c")); g.setLineWidth(1);
        g.strokeLine(110, 380, 1090, 380);
        rallyTelemetry(g, 232, 411, "EXHAUST", telemetry.temperatureC("exhaustTemp", "egt"), "°C");
        rallyTelemetry(g, 477, 411, "OIL TEMP", telemetry.temperatureC("oilTemp"), "°C");
        rallyTelemetry(g, 722, 411, "COOLANT", telemetry.coolantC(), "°C");
        rallyTelemetry(g, 967, 411, "GEARBOX", telemetry.temperatureC("gearboxTemp"), "°C");
        rallyTelemetry(g, 232, 481, "RAIL PRESS", telemetry.pressureKpa("railPressure", "fuelRailPressure"), "kPa");
        rallyTelemetry(g, 477, 481, "FUEL PRESS", telemetry.pressureKpa("fuelPressure"), "kPa");
        rallyTelemetry(g, 722, 481, "OIL PRESS", telemetry.pressureKpa("oilPressure"), "kPa");
        rallyTelemetry(g, 967, 481, "BATTERY", telemetry.read("batteryVoltage"), "V");
        dataState(g);
    }

    private void drawRpmBar(VectorPainter g, double x, double y, double width, double height) {
        int segments = 40;
        double gap = 4, sw = (width - gap * (segments - 1)) / segments;
        int active = (int) Math.floor(telemetry.rpm().fraction(0, 8000) * segments);
        for (int i = 0; i <= 8; i++) {
            double tickX = x + width * i / 8;
            text(g, Integer.toString(i), tickX, y - 19, 14, i >= 7 ? Color.web("#ff7966") : INK);
        }
        for (int i = 0; i < segments; i++) {
            g.setFill(i >= 33 ? Color.web(i < active ? "#ff5146" : "#351d1f")
                    : i >= 28 ? Color.web(i < active ? "#ffc75a" : "#332b19")
                    : Color.web(i < active ? "#c5ec6a" : "#1d2b26"));
            g.fillRoundRect(x + i * (sw + gap), y, sw, height, 2, 2);
        }
    }

    private void statusFlag(VectorPainter g, double cx, double cy, String label, boolean active, boolean error) {
        g.setFill(Color.web(active ? error ? "#45201f" : "#203b2b" : "#25282a"));
        g.fillRoundRect(cx - 48, cy - 15, 96, 30, 6, 6);
        text(g, label, cx, cy, 13, Color.web(active ? error ? "#ff6258" : "#b7ec8c" : "#8b949a"));
    }

    private void rallyTelemetry(VectorPainter g, double x, double y, String label, Reading reading, String unit) {
        g.setFill(Color.web("#10191e"));
        g.fillRoundRect(x - 117, y - 21, 234, 64, 6, 6);
        text(g, label, x, y - 5, 11, Color.web("#a3b3bd"));
        fittedText(g, reading.text(1), x - 25, y + 21, 26, reading.available() ? INK : Color.web("#6f818b"), 140, false);
        fittedText(g, unit, x + 88, y + 24, 12, Color.web("#a3b3bd"), 40, false);
    }

    private void digitalValue(VectorPainter g, double x, double y, String value, String units) {
        fittedText(g, value, x, y, 78, INK, 340, true);
        text(g, units, x, y + 62, 15, Color.web("#a4b2bd"));
    }

    private void fittedText(VectorPainter g, String value, double x, double y, double size,
                            Color color, double maxWidth, boolean mono) {
        String family = mono ? "DejaVu Sans Mono" : "DejaVu Sans";
        FontWeight weight = mono ? FontWeight.BOLD : FontWeight.NORMAL;
        Font font = Font.font(family, weight, size);
        g.setFont(font);
        g.setTextAlign(TextAlignment.CENTER);
        g.setFill(color);
        g.fillText(value, x, y, maxWidth);
    }

    private void text(VectorPainter g, String text, double x, double y, double size, Color color) {
        g.setTextAlign(TextAlignment.CENTER);
        g.setFill(color);
        g.setFont(Font.font("DejaVu Sans", FontWeight.NORMAL, size));
        g.fillText(text, x, y);
    }
}
