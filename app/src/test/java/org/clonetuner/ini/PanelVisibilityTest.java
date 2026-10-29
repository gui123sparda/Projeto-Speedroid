package org.clonetuner.ini;

import org.junit.Test;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class PanelVisibilityTest {
    @Test public void panelConditionsAreParsedWithAndWithoutPosition() throws Exception {
        var file = Files.createTempFile("panel-visibility", ".ini");
        try {
            Files.writeString(file, "[UserDefined]\ndialog = main, \"Main\"\npanel = curveA, { enabled }\npanel = curveB, West, { enabled > 1 }\n");
            var items = IniModel.load(file, null).dialogs.get("main").items;
            assertEquals("{ enabled }", items.get(0).vis);
            assertEquals("", items.get(0).constraint);
            assertEquals("{ enabled > 1 }", items.get(1).vis);
            assertEquals("West", items.get(1).constraint);
        } finally { Files.deleteIfExists(file); }
    }
}
