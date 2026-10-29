package org.clonetuner.ini;

import java.util.List;

/** Catches whitespace leaking into units and damage to literal protocol bytes. */
public final class IniTokenizerTest {
    @org.junit.Test
    public void originalRegression() {
        String[] args = new String[0];
        equal(
            Ini.strings("scalar, U08, 107, \"PSI\", 1, 0"), List.of("scalar", "U08", "107", "PSI", "1", "0"));
        equal(Ini.strings("  \"  title  \"  , \"\", , \"a,b\", { max(a,b) }"),
            List.of("  title  ", "", "", "a,b", "{ max(a,b) }"));
        equal(Ini.strings(" \" \\x00\\xFF\\n\" , 4"), List.of(" \0\u00ff\n", "4"));
        equal(Ini.strings("a,,b,"), List.of("a", "", "b", ""));
        System.out.println("IniTokenizerTest OK");
    }
    private static void equal(Object actual, Object expected) {
        if (!expected.equals(actual))
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
