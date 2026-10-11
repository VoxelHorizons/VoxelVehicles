package org.voxelhorizons.vehicles;

import org.junit.Test;
import static org.junit.Assert.*;

/** Measurement utilities must not allocate on every sampled vehicle tick. */
public class VehiclePerformanceTest {
    @Test public void reportsCorrectRollingWindowAndPercentiles(){
        VehiclePerformance performance=new VehiclePerformance();
        for(int i=0;i<200;i++){
            performance.record((i+1)*1_000_000L,200,4,796,600,200);
        }
        assertEquals(200,performance.sampleCount());
        String report=String.join("\n",performance.lines(200,250));
        assertTrue(report,report.contains("avg §a100.500ms"));
        assertTrue(report,report.contains("p95 §e190.000ms"));
        assertTrue(report,report.contains("max §e200.000ms"));
        assertTrue(report,report.contains("200"));
        assertTrue(report,report.contains("99.5%"));
        assertTrue(report,report.contains("600.0"));
    }

    @Test public void dropsOldSamplesWithoutUnboundedGrowth(){
        VehiclePerformance performance=new VehiclePerformance();
        for(int i=0;i<300;i++)performance.record(1_000_000L,100,5,5,0,0);
        assertEquals(200,performance.sampleCount());
        String report=String.join("\n",performance.lines(100,100));
        assertTrue(report,report.contains("avg §a1.000ms"));
        assertTrue(report,report.contains("p95 §e1.000ms"));
    }

    @Test public void emptySamplingIsSafe(){
        assertEquals(1,new VehiclePerformance().lines(0,0).length);
    }
}
