package org.clonetuner.model;

import java.util.List;
import java.util.Locale;

/** One immutable firmware block. All timestamps and intervals are microseconds. */
public record TriggerCapture(Mode mode, List<Sample> samples, boolean simulated) {
    public enum Mode { TOOTH, COMPOSITE }
    public record Sample(long timeUs, long intervalUs, int flags) {
        public boolean crank() { return (flags & 1) != 0; }
        public boolean cam() { return (flags & 2) != 0; }
        public boolean sync() { return (flags & 16) != 0; }
        public boolean secondaryTrigger() { return (flags & 8) != 0; }
    }
    public TriggerCapture { samples = List.copyOf(samples); }
    public long durationUs() { return samples.isEmpty() ? 0 : samples.get(samples.size()-1).timeUs(); }
    public long medianIntervalUs() {
        long[] times=samples.stream().mapToLong(Sample::intervalUs).filter(t -> t>0).sorted().toArray();
        return times.length==0 ? 0 : times[times.length/2];
    }
    public int longIntervals() {
        double threshold=medianIntervalUs()*1.8;
        return (int)samples.stream().filter(s -> s.intervalUs()>0 && s.intervalUs()>=threshold).count();
    }
    public int crankEdges() { return edges(1); }
    public int camEdges() { return edges(2); }
    public int syncLosses() {
        int count=0;
        for(int i=1;i<samples.size();i++) if(samples.get(i-1).sync() && !samples.get(i).sync()) count++;
        return count;
    }
    private int edges(int mask) {
        int count=0;
        for(int i=1;i<samples.size();i++) if((samples.get(i-1).flags()&mask)!=(samples.get(i).flags()&mask)) count++;
        return count;
    }
    public String toCsv() {
        StringBuilder out=new StringBuilder("Source,Mode,Index,Time (us),Interval (us),Crank,Cam,Sync,Secondary trigger,Cycle\n");
        for(int i=0;i<samples.size();i++) {
            Sample s=samples.get(i);
            out.append(String.format(Locale.ROOT,"%s,%s,%d,%d,%d,",simulated?"DEMONSTRACAO":"ECU",mode,i,s.timeUs(),s.intervalUs()));
            if(mode==Mode.COMPOSITE) out.append(s.crank()?1:0).append(',').append(s.cam()?1:0).append(',').append(s.sync()?1:0).append(',').append(s.secondaryTrigger()?1:0).append(',').append((s.flags()>>5)&1);
            else out.append(",,,,");
            out.append('\n');
        }
        return out.toString();
    }
}
