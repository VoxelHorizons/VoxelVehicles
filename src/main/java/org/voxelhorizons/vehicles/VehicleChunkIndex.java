package org.voxelhorizons.vehicles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-thread only spatial index for placed vehicle origin chunks.
 * Chunk lifecycle, interaction targeting and player count do not require
 * full scans of every saved vehicle.
 */
final class VehicleChunkIndex {
    private static final class Key {
        final UUID world;
        final int x,z;
        Key(UUID world,int x,int z){this.world=world;this.x=x;this.z=z;}
        @Override public boolean equals(Object value){
            if(this==value)return true;
            if(!(value instanceof Key))return false;
            Key k=(Key)value;
            return x==k.x&&z==k.z&&world.equals(k.world);
        }
        @Override public int hashCode(){
            int result=world.hashCode();
            return 31*(31*result+x)+z;
        }
    }
    private final Map<UUID,Key> locations=new HashMap<UUID,Key>();
    private final Map<Key,Set<UUID>> chunks=new HashMap<Key,Set<UUID>>();

    void update(UUID vehicle,UUID world,double x,double z){
        Key next=new Key(world,(int)Math.floor(x)>>4,(int)Math.floor(z)>>4);
        Key previous=locations.get(vehicle);
        if(next.equals(previous))return;
        if(previous!=null){
            Set<UUID> ids=chunks.get(previous);
            if(ids!=null){
                ids.remove(vehicle);
                if(ids.isEmpty())chunks.remove(previous);
            }
        }
        locations.put(vehicle,next);
        Set<UUID> ids=chunks.get(next);
        if(ids==null){
            ids=new HashSet<UUID>();
            chunks.put(next,ids);
        }
        ids.add(vehicle);
    }

    void remove(UUID vehicle){
        Key old=locations.remove(vehicle);
        if(old==null)return;
        Set<UUID> ids=chunks.get(old);
        if(ids==null)return;
        ids.remove(vehicle);
        if(ids.isEmpty())chunks.remove(old);
    }

    /** Snapshot prevents an entity/chunk callback from invalidating iteration. */
    Collection<UUID> inChunk(UUID world,int chunkX,int chunkZ){
        Set<UUID> ids=chunks.get(new Key(world,chunkX,chunkZ));
        return ids==null?Collections.<UUID>emptyList():new ArrayList<UUID>(ids);
    }

    /** At most (usually) four adjacent chunks for a five-block ray. */
    Collection<UUID> nearby(UUID world,double x,double z,double radius){
        int minX=(int)Math.floor(x-radius)>>4;
        int maxX=(int)Math.floor(x+radius)>>4;
        int minZ=(int)Math.floor(z-radius)>>4;
        int maxZ=(int)Math.floor(z+radius)>>4;
        Set<UUID> ids=new HashSet<UUID>();
        for(int cx=minX;cx<=maxX;cx++)
            for(int cz=minZ;cz<=maxZ;cz++){
                Set<UUID> bucket=chunks.get(new Key(world,cx,cz));
                if(bucket!=null)ids.addAll(bucket);
            }
        return ids;
    }

    int size(){return locations.size();}
    void clear(){locations.clear();chunks.clear();}
}
