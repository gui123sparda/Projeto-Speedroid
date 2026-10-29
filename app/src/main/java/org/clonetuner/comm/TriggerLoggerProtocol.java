package org.clonetuner.comm;

import org.clonetuner.ini.IniModel;
import org.clonetuner.model.TriggerCapture;
import org.clonetuner.model.TriggerCapture.Mode;
import org.clonetuner.model.TriggerCapture.Sample;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/** Speeduino 202501 logger wire format: BE uint32, followed by flags in composite mode. */
public final class TriggerLoggerProtocol {
    private TriggerLoggerProtocol() {}
    public static final int RECORDS=127;
    public static IniModel.Logger definition(IniModel ini, Mode mode) {
        String type=mode==Mode.TOOTH?"tooth":"composite";
        String start=mode==Mode.TOOTH?"H":"J", stop=mode==Mode.TOOTH?"h":"j";
        return ini.loggers.values().stream().filter(d -> d.type.equals(type) && d.startCommand.equals(start)
            && d.stopCommand.equals(stop) && d.recordLength==(mode==Mode.TOOTH?4:5)
            && d.readCommand.startsWith("T") && !d.readyCondition.isBlank())
            .findFirst().orElseThrow(() -> new UnsupportedOperationException("O INI não define um logger Speeduino compatível: "+type));
    }
    public static int byteCount(Mode mode) { return RECORDS*(mode==Mode.TOOTH?4:5); }
    public static TriggerCapture decode(Mode mode, byte[] data, boolean simulated) {
        if(data.length!=byteCount(mode)) throw new IllegalArgumentException("Bloco de logger incompleto: "+data.length);
        ByteBuffer buffer=ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        long[] raw=new long[RECORDS]; int[] flags=new int[RECORDS];
        for(int i=0;i<RECORDS;i++) {
            raw[i]=Integer.toUnsignedLong(buffer.getInt());
            if(mode==Mode.COMPOSITE) flags[i]=Byte.toUnsignedInt(buffer.get());
        }
        int count=RECORDS;
        if(mode==Mode.TOOTH) { while(count>0 && raw[count-1]==0) count--; }
        else {
            while(count>1 && raw[count-1]==raw[count-2] && flags[count-1]==0) count--;
            if(count==1 && raw[0]==0 && flags[0]==0) count=0;
        }
        var samples=new ArrayList<Sample>(); long time=0;
        for(int i=0;i<count;i++) {
            long interval=mode==Mode.TOOTH?raw[i]:(i==0?0:(raw[i]-raw[i-1])&0xffffffffL);
            if(mode==Mode.TOOTH && interval==0) throw new IllegalArgumentException("Intervalo nulo dentro do tooth log");
            if(mode==Mode.COMPOSITE && interval>0x7fffffffL) throw new IllegalArgumentException("Timestamp fora de ordem no composite log");
            time+=interval;
            samples.add(new Sample(time,interval,flags[i]));
        }
        return new TriggerCapture(mode,samples,simulated);
    }
}
