package org.clonetuner.ini;

import org.junit.Test;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class TableLabelsTest {
    @Test public void explicitAxisLabelsReachTheEditor() throws Exception {
        var file = Files.createTempFile("table-labels", ".ini");
        try {
            Files.writeString(file, "[TableEditor]\ntable = fuel, fuel3d, \"Fuel\", 1\nxyLabels = \"RPM\", \"Load\"\nxBins = rpmBins, rpm\nyBins = loadBins, load\nzBins = values\n");
            var table = IniModel.load(file, null).tables.get("fuel");
            assertEquals("RPM", table.xLabel);
            assertEquals("Load", table.yLabel);
        } finally { Files.deleteIfExists(file); }
    }

    @Test public void unnamedAxesUseTheirBinsInsteadOfGenericXY() throws Exception {
        var file = Files.createTempFile("table-labels", ".ini");
        try {
            Files.writeString(file, "[TableEditor]\ntable = fuel, fuel3d, \"Fuel\", 1\nxBins = rpmBins, rpm\nyBins = loadBins, load\nzBins = values\n");
            var table = IniModel.load(file, null).tables.get("fuel");
            assertEquals("rpmBins", table.xLabel);
            assertEquals("loadBins", table.yLabel);
        } finally { Files.deleteIfExists(file); }
    }
}
