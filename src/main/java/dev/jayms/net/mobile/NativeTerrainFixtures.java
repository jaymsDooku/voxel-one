package dev.jayms.net.mobile;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Deterministic compatibility artifact, never a runtime dependency of the iOS app. */
public final class NativeTerrainFixtures {
    private NativeTerrainFixtures() {}
    private static double value(long seed,int x,int z,long salt){
        long n=seed ^ x*341873128712L ^ z*132897987541L ^ salt;
        n=(n ^ n>>>30)*0xbf58476d1ce4e5b9L;n=(n ^ n>>>27)*0x94d049bb133111ebL;
        return ((n ^ n>>>31)>>>11)*0x1.0p-53;
    }
    public static Map<String,Object> data()throws Exception {
        List<Map<String,Object>> cases=new ArrayList<>();
        for(long seed:new long[]{748291L,-42L,Long.MIN_VALUE})for(int version=1;version<=Terrain.CURRENT_VERSION;version++){
            var terrain=new Terrain(seed,version);
            List<int[]> points=new ArrayList<>(List.of(new int[]{0,0},new int[]{-1,-1},new int[]{-12,12},new int[]{-17,17},new int[]{16,16},new int[]{17,-17},new int[]{-25,-25},new int[]{64,-128},new int[]{64,128},new int[]{64,400},new int[]{0,450},new int[]{0,225},new int[]{132,-72},new int[]{210,160},new int[]{-210,160},new int[]{400,400},new int[]{580,-580},new int[]{999999,-999999},new int[]{-999999,999999}));
            for(int i=0;i<32;i++)points.add(new int[]{-800+i*97%1601,-800+i*193%1601});
            for(int a=-12;a<=12;a+=6)for(int b=-12;b<=12;b+=6){
                int x=a*12+2+(int)(value(seed,a,b,211)*8),z=b*12+2+(int)(value(seed,a,b,277)*8);
                points.add(new int[]{x,z});points.add(new int[]{x+1,z});
            }
            for(var point:points){
                int x=point[0],z=point[1],h=terrain.column(x,z).height();
                var ys=new LinkedHashSet<Integer>(List.of(-33,-32,-29,h-6,h-4,h-1,h,h+1,h+4,h+8,terrain.surfaceHeight(x,z),96));
                for(int y:ys)cases.add(new TreeMap<>(Map.of("seed",Long.toString(seed),"version",version,"x",x,"y",y,"z",z,"block",terrain.block(x,y,z),"surface",terrain.surfaceHeight(x,z),"ocean",terrain.ocean(x,z))));
            }
        }
        var terrain=new Terrain(Terrain.DEFAULT_SEED);int eye=terrain.surfaceHeight(0,0)+1;
        var pose=new Protocol.Pose(1,.5f,eye,.5f,0,0,0,0,false,0,0,false);
        var snapshot=MobileSnapshot.capture(new WorldVoxels(terrain),terrain,pose,new Inventory(),20,CityFrame.empty(GameConfig.sandbox()),"",24);
        var digest=MessageDigest.getInstance("SHA-256");int count=0;
        for(var row:(List<?>)snapshot.get("cells")){for(var number:(List<?>)row)digest.update(ByteBuffer.allocate(4).putInt(((Number)number).intValue()).array());count++;}
        return new TreeMap<>(Map.of("version",1,"cases",cases,"window",new TreeMap<>(Map.of("seed",Long.toString(Terrain.DEFAULT_SEED),"generatorVersion",Terrain.CURRENT_VERSION,"eyeY",eye,"bounds",snapshot.get("bounds"),"cellCount",count,"sha256",HexFormat.of().formatHex(digest.digest())))));
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Fixture output path required");
        Files.writeString(Path.of(args[0]),Json.write(data()));
    }
}
