package org.clonetuner.model;

public final class ReqFuelCalculatorTest {
    @org.junit.Test
    public void originalRegression() {
        String[] args = new String[0];
        double cc = ReqFuelCalculator.calculate(2000, true, 4, 300, true, 14.7);
        double cid = ReqFuelCalculator.calculate(2000 / 16.38706, false, 4, 300 / 10.5, false, 14.7);
        if (Math.abs(cc - cid) > 1e-9) throw new AssertionError("Unit conversions disagree: " + cc + " vs " + cid);
        double doubledFlow = ReqFuelCalculator.calculate(2000, true, 4, 600, true, 14.7);
        if (Math.abs(cc / 2 - doubledFlow) > 1e-9) throw new AssertionError("ReqFuel must scale inversely with injector flow");
        if (!(cc > 0 && cc < 25.5)) throw new AssertionError("Expected a representable Speeduino ReqFuel value, got " + cc);
        try {
            ReqFuelCalculator.calculate(0, true, 4, 300, true, 14.7);
            throw new AssertionError("Zero displacement should be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        System.out.println("ReqFuelCalculatorTest OK (2000 cc: " + String.format(java.util.Locale.ROOT, "%.2f ms", cc) + ")");
    }
}
