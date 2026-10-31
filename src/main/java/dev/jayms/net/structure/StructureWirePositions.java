package dev.jayms.net.structure;

import dev.jayms.net.Protocol;
import dev.jayms.net.Terrain;
import java.io.IOException;
import java.util.Arrays;

/** Decode-local position index: no strings, boxed keys or adversarial hash chains. */
final class StructureWirePositions {
    private final long[] keys;
    private int size;
    StructureWirePositions(int limit) {
        if(limit<0||limit>2_000_000)throw new IllegalArgumentException("Structure cell transfer limit");
        keys=new long[limit];
    }
    void add(int x,int y,int z)throws IOException {
        // 21 bits per horizontal axis and 11 vertical bits cover every valid cell.
        // Check before packing so invalid or future wider coordinates cannot alias.
        if(Terrain.LIMIT>1_048_575||Protocol.MIN_BUILD_Y < -1024||Protocol.MAX_BUILD_Y>1023
                ||Math.abs((long)x)>Terrain.LIMIT||Math.abs((long)z)>Terrain.LIMIT
                ||y<Protocol.MIN_BUILD_Y||y>Protocol.MAX_BUILD_Y)
            throw new IOException("Invalid world cell position");
        if(size==keys.length)throw new IOException("Structure cell transfer limit");
        keys[size++]=((long)(x+1_048_576)<<32)|((long)(z+1_048_576)<<11)|(y+1024);
    }
    void validate()throws IOException {
        if(size!=keys.length)throw new IOException("Incomplete structure cell transfer");
        Arrays.sort(keys);
        for(int i=1;i<size;i++)if(keys[i]==keys[i-1])throw new IOException("Duplicate structure cell");
    }
}
