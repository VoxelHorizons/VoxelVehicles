package org.voxelhorizons.vehicles;

import org.bukkit.Chunk;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;
import java.util.function.Consumer;

/** Amortizes orphan-renderer cleanup so maintenance cannot sweep thousands of chunks in one tick. */
final class VehicleOrphanSweep {
    private final Queue<Chunk> pending=new ArrayDeque<Chunk>();
    private long cyclesCompleted;

    /** Don't restart a partial sweep when maintenance fires again. */
    boolean schedule(Collection<Chunk> loaded){
        if(!pending.isEmpty())return false;
        pending.addAll(loaded);
        return true;
    }

    /** At most budget chunks are handed to the cleanup function each server tick. */
    int drain(int budget,Consumer<Chunk> cleanup){
        int processed=0;
        while(processed<budget&&!pending.isEmpty()){
            cleanup.accept(pending.remove());
            processed++;
        }
        if(processed>0&&pending.isEmpty())cyclesCompleted++;
        return processed;
    }

    int remaining(){return pending.size();}
    long cyclesCompleted(){return cyclesCompleted;}
}
