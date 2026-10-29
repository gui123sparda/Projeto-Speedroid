package org.clonetuner.model;

import java.io.StringReader;

public final class FuelTrimAdvisorTest {
    private static final String HEADER = "Time (s),RPM,MAP,TPS,CLT,AFR,AFR Target,EGO correction\n";
    @org.junit.Test
    public void originalRegression() throws Exception {
        String[] args = new String[0];
        var settings = new FuelTrimAdvisor.Settings(14, 70, 3, 5);
        var lean = FuelTrimAdvisor.analyze(new StringReader(log(15.4, 100)), settings);
        check(lean.accepted() == 5 && lean.rejected() == 1, "first sample has no stability history");
        var cell = lean.cells().getFirst();
        near(cell.rawPercent(), 10); near(cell.suggestedPercent(), 5);
        check(cell.ready(), "stable populated region ready");
        near(FuelTrimAdvisor.analyze(new StringReader(log(12.6, 100)), settings)
                .cells().getFirst().suggestedPercent(), -5);
        near(FuelTrimAdvisor.analyze(new StringReader(log(14, 110)), settings)
                .cells().getFirst().rawPercent(), 10);
        near(FuelTrimAdvisor.analyze(new StringReader(log(14, 110).replace("EGO correction", "Gego")), settings)
                .cells().getFirst().rawPercent(), 10);
        var sparse = FuelTrimAdvisor.analyze(new StringReader(log(14, 100)),
                new FuelTrimAdvisor.Settings(14, 70, 20, 5));
        check(!sparse.cells().getFirst().ready(), "sparse data withheld");
        var cold = FuelTrimAdvisor.analyze(new StringReader(log(14, 100).replace(",80,", ",40,")), settings);
        check(cold.accepted() == 0, "cold samples rejected");
        var transientLog = HEADER + "0,2000,50,10,80,14,14,100\n"
                + "0.1,2000,50,50,80,14,14,100\n"
                + "2,2000,50,50,80,14,14,100\n"
                + "2.1,2000,50,50,80,NaN,14,100\n";
        check(FuelTrimAdvisor.analyze(new StringReader(transientLog), settings).accepted() == 0,
                "transients, gaps and nonfinite samples rejected");
        String noisy = HEADER;
        for (int i = 0; i < 8; i++) noisy += (i * .1) + ",2000,50,10,80," + (i % 2 == 0 ? 12 : 17) + ",14,100\n";
        check(!FuelTrimAdvisor.analyze(new StringReader(noisy), settings).cells().getFirst().ready(), "noisy data withheld");
        String fallback = "\ufeff\"Time (s)\",RPM,MAP,TPS,CLT,AFR\n0,2000,50,10,80,14\n0.1,2000,50,10,80,14\n";
        near(FuelTrimAdvisor.analyze(new StringReader(fallback), settings).cells().getFirst().rawPercent(), 0);
        String outlier = log(14, 100) + "0.6,2000,50,10,80,21,14,100\n";
        near(FuelTrimAdvisor.analyze(new StringReader(outlier), settings).cells().getFirst().rawPercent(), 0);
        String regions = HEADER + "0,1999,49,10,80,14,14,100\n"
                + "0.1,1999,49,10,80,14,14,100\n0.2,2000,50,10,80,14,14,100\n";
        var boundaries = FuelTrimAdvisor.analyze(new StringReader(regions), settings).cells();
        check(boundaries.size() == 2 && boundaries.getFirst().rpm() == 1500
                && boundaries.getFirst().map() == 40 && boundaries.getLast().rpm() == 2000
                && boundaries.getLast().map() == 50, "region boundary assignment");
        for (String row : new String[]{"0.1,2200,50,10,80,14,14,100", "0.1,2000,60,10,80,14,14,100",
                "0.1,2000,50,10,80,Infinity,14,100", "0.1,0,50,10,80,14,14,100", "bad,row"}) {
            var filtered = FuelTrimAdvisor.analyze(new StringReader(HEADER + "0,2000,50,10,80,14,14,100\n" + row + "\n"), settings);
            check(filtered.accepted() == 0, "invalid or transient row accepted: " + row);
        }
        fails("RPM,AFR\n2000,14\n", settings);
        fails(HEADER + "\"unterminated\n", settings);
        try { new FuelTrimAdvisor.Settings(Double.NaN, 70, 3, 5); throw new AssertionError("NaN target"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("FuelTrimAdvisorTest OK");
    }
    private static String log(double afr, double ego) {
        StringBuilder s = new StringBuilder(HEADER);
        for (int i = 0; i < 6; i++) s.append(i * .1).append(",2000,50,10,80,").append(afr).append(",14,").append(ego).append('\n');
        return s.toString();
    }
    private static void fails(String csv, FuelTrimAdvisor.Settings settings) throws Exception {
        try { FuelTrimAdvisor.analyze(new StringReader(csv), settings); throw new AssertionError("invalid CSV accepted"); }
        catch (java.io.IOException expected) { }
    }
    private static void near(double actual, double expected) {
        check(Math.abs(actual - expected) < 1e-8, actual + " != " + expected);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
