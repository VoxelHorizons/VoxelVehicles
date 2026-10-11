package org.voxelhorizons.vehicles;

import java.util.Arrays;
import java.util.Locale;

/**
 * Constant-time, fixed-memory telemetry for the synchronous vehicle tick.
 * No logs, timers, or per-vehicle tasks. Only /vv perf calculates percentiles.
 */
final class VehiclePerformance {
    private static final int WINDOW=200; // last 10 seconds at 20 TPS
    private final long[] nanos=new long[WINDOW];
    private final int[] drivers=new int[WINDOW];
    private final int[] decoded=new int[WINDOW];
    private final int[] reused=new int[WINDOW];
    private final int[] sent=new int[WINDOW];
    private final int[] skipped=new int[WINDOW];
    private int next,recorded;
    private int currentDrivers;

    void record(long elapsed,int riders,int shapes,int hits,int actual,int suppressed){
        nanos[next]=Math.max(0,elapsed);
        drivers[next]=Math.max(0,riders);
        decoded[next]=Math.max(0,shapes);
        reused[next]=Math.max(0,hits);
        sent[next]=Math.max(0,actual);
        skipped[next]=Math.max(0,suppressed);
        next=(next+1)%WINDOW;
        if(recorded<WINDOW)recorded++;
        currentDrivers=riders;
    }

    int sampleCount(){return recorded;}

    String[] lines(int loadedVehicles,int allVehicles){
        if(recorded==0)return new String[]{"§7No vehicle ticks recorded yet."};
        long[] durations=Arrays.copyOf(nanos,recorded);
        Arrays.sort(durations);
        long ns=0;
        long sumDrivers=0,shapes=0,hits=0,teleports=0,avoided=0;
        for(int i=0;i<recorded;i++){
            ns+=nanos[i];
            sumDrivers+=drivers[i];
            shapes+=decoded[i];
            hits+=reused[i];
            teleports+=sent[i];
            avoided+=skipped[i];
        }
        double meanMs=(ns/(double)recorded)/1_000_000.0;
        int percentile=(int)Math.ceil(recorded*0.95)-1;
        double p95Ms=durations[Math.max(0,percentile)]/1_000_000.0;
        double peakMs=durations[recorded-1]/1_000_000.0;
        double hitRatio=(shapes+hits)==0?0:100.0*hits/(shapes+hits);
        return new String[]{
            "§6VoxelVehicles performance §7(last "+recorded+" plugin ticks; includes autosaves)",
            String.format(Locale.ROOT,"§fVehicle task: avg §a%.3fms§f, p95 §e%.3fms§f, max §e%.3fms",
                    meanMs,p95Ms,peakMs),
            String.format(Locale.ROOT,"§fRiders: now §a%d§f, avg §a%.1f§f, loaded vehicles §7%d§f / total §7%d",
                    currentDrivers,sumDrivers/(double)recorded,loadedVehicles,allVehicles),
            String.format(Locale.ROOT,"§fTerrain blocks/tick: decoded §e%.1f§f, reused §a%.1f§f (§a%.1f%%§f cache reuse)",
                    shapes/(double)recorded,hits/(double)recorded,hitRatio),
            String.format(Locale.ROOT,"§fEntity teleports/tick: actual §e%.1f§f, redundant avoided §a%.1f",
                    teleports/(double)recorded,avoided/(double)recorded),
            "§7Vehicle task time is not whole-server MSPT. Compare with /spark profiler."
        };
    }
}
