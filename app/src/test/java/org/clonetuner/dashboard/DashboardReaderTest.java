package org.clonetuner.dashboard;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public final class DashboardReaderTest {
    @org.junit.Test
    public void originalRegression() throws Exception {
        var d = DashboardReader.read(Path.of("src/test/resources/dashboard/components.dash"));
        check(d.components().size() == 6, "all painter types must be kept");
        check(d.components().get(0).properties().get("RelativeX").equals("-0.1"), "negative geometry");
        check(d.resources().get("pixel").bytes().length > 0, "embedded image");
        check(d.components().get(0).properties().get("OutputChannel").equals("rpm"), "channel binding");
        String fixture = Files.readString(Path.of("src/test/resources/dashboard/components.dash"));
        reject("<dsh>");
        reject("<!DOCTYPE dsh [<!ENTITY x SYSTEM 'file:///etc/hostname'>]><dsh>&x;</dsh>");
        reject(fixture.replace("RelativeWidth>0.3", "RelativeWidth>NaN"));
        reject(fixture.replace("RelativeWidth>0.3", "RelativeWidth>0"));
        reject(fixture.replace("StartAngle>210", "StartAngle>NaN"));
        reject(fixture.replace("iVBOR", "***"));
        reject(fixture.replace("<Id>c1</Id>", "<Id>c0</Id>"));
        Path text = Files.createTempFile("dashboard-text", ".dash");
        try {
            Files.writeString(
                text, fixture.replace("<Title>Engine RPM</Title>", "<Title>  Engine RPM  </Title>"));
            check(DashboardReader.read(text).components().get(0).text("Title").equals("  Engine RPM  "),
                "literal label whitespace");
        } finally {
            Files.delete(text);
        }
        Path unknown = Files.createTempFile("dashboard-unknown", ".dash");
        try {
            Files.writeString(unknown, fixture.replace("Analog Gauge", "Unknown Painter"));
            check(!DashboardReader.read(unknown).diagnostics().isEmpty(), "unknown painter diagnostic");
        } finally {
            Files.delete(unknown);
        }
        int n = 0;
        for (String name :
            List.of("Speeduino_Sweep.dash", "Speeduino_LED.dash", "Speeduino_LED_Amber.dash")) {
            Path p = Path.of("src/main/assets/dashboards", name);
            if (Files.exists(p)) {
                var original = DashboardReader.read(p);
                check(original.components().size() == (n == 0 ? 24 : 49), "reference count: " + name);
                check(original.diagnostics().isEmpty(), "reference diagnostics: " + original.diagnostics());
                System.out.println(name + " OK: " + original.components().size());
            } else
                System.out.println("Reference unavailable: " + p);
            n++;
        }
        System.out.println("DashboardReaderTest OK");
    }
    private static void reject(String xml) throws Exception {
        Path p = Files.createTempFile("dashboard-invalid", ".dash");
        try {
            Files.writeString(p, xml);
            try {
                DashboardReader.read(p);
                throw new AssertionError("Invalid XML accepted");
            } catch (IOException expected) {
                check(expected.getMessage().contains(p.getFileName().toString()), "file diagnostic");
            }
        } finally {
            Files.delete(p);
        }
    }
    private static void check(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
    }
}
