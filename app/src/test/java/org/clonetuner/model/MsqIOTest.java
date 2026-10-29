package org.clonetuner.model;

import org.clonetuner.ini.IniModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Checks TunerStudio-style named constants and page data round-trip. */
public final class MsqIOTest {
    @org.junit.Test
    public void originalRegression() throws Exception {
        String[] args = new String[0];
        IniModel ini = IniModel.load(Path.of("src/main/assets/speeduino.ini"), null);
        Ecu source = new Ecu(ini);
        source.set("pinLayout", 2);
        source.set("rpmhigh", 7500);
        Path file = Files.createTempFile("clonetuner-roundtrip-", ".msq");
        try {
            MsqIO.save(file, source);
            String xml = Files.readString(file);
            if (!xml.contains("<constant") || !xml.contains("name=\"rpmhigh\"") || !xml.contains("number=\"0\"")) {
                throw new AssertionError("MSQ is missing TunerStudio constant/page structure");
            }
            Ecu target = new Ecu(ini);
            if (!ini.signature.equals(MsqIO.load(file, target))) throw new AssertionError("Wrong signature");
            if (target.get("rpmhigh") != source.get("rpmhigh") || target.get("pinLayout") != source.get("pinLayout")) {
                throw new AssertionError("Named constants changed during round trip");
            }
            for (int i = 1; i <= ini.nPages; i++) {
                if (!Arrays.equals(source.page(i), target.page(i))) {
                    throw new AssertionError("Page " + i + " changed during round trip");
                }
            }
            byte[] before = target.page(1).clone();
            Files.writeString(file, xml.replace("signature=\"" + ini.signature + "\"", "signature=\"other firmware\""));
            try { MsqIO.load(file, target); throw new AssertionError("Expected signature rejection"); }
            catch (java.io.IOException expected) { /* expected */ }
            if (!Arrays.equals(before, target.page(1))) throw new AssertionError("Mismatched tune changed ECU data");
            System.out.println("MsqIOTest OK");
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
