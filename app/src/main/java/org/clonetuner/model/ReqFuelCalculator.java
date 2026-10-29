package org.clonetuner.model;

/** TunerStudio/Speeduino required-fuel calculator, returning milliseconds. */
public final class ReqFuelCalculator {
    private static final double CC_PER_CUBIC_INCH = 16.38706;
    private static final double CC_MIN_PER_LB_HOUR = 10.5;

    private ReqFuelCalculator() {}

    public static double calculate(double displacement, boolean displacementIsCc,
                                   int cylinders, double injectorFlow,
                                   boolean injectorFlowIsCcPerMinute, double afr) {
        if (!Double.isFinite(displacement) || displacement <= 0) throw new IllegalArgumentException("Engine displacement must be positive.");
        if (!Double.isFinite(injectorFlow) || injectorFlow <= 0) throw new IllegalArgumentException("Injector flow must be positive.");
        if (cylinders < 1) throw new IllegalArgumentException("Cylinder count must be a positive whole number.");
        if (!Double.isFinite(afr) || afr <= 0) throw new IllegalArgumentException("Air-fuel ratio must be positive.");
        double cubicInches = displacementIsCc ? displacement / CC_PER_CUBIC_INCH : displacement;
        double poundsPerHour = injectorFlowIsCcPerMinute ? injectorFlow / CC_MIN_PER_LB_HOUR : injectorFlow;
        return (3.6e7 * cubicInches * 4.27793e-5) / (cylinders * afr * poundsPerHour) / 10.0;
    }
}
