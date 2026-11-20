package dev.jayms.combat.hcf;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HcfStoreTest {
    @TempDir Path directory;
    @Test void atomicRoundTripDeathbanDtrAndLootNoTemporaryResidue() throws Exception {
        var store=new HcfStore(directory.resolve("season.hcf"));assertEquals(HcfStore.Saved.initial(),store.load());
        var state=new HcfStore.Saved(7,1,List.of(new HcfStore.Member("player",1,3,123456,true)),
            List.of(new HcfStore.Team(0,300,400,10,2,true),new HcfStore.Team(-1,500,600,3,9,true)),
            List.of(new HcfStore.Drop(1,new dev.jayms.combat.Vec3(1,1,1),999999,List.of(new dev.jayms.combat.KitProfile.Stack(dev.jayms.combat.KitProfile.Item.FOOD,8)))));
        store.save(state);assertEquals(state,store.load());store.save(state.phase(HcfSession.Phase.ENDED));assertEquals(2,store.load().phase());
        try(var files=Files.list(directory)){assertEquals(List.of("season.hcf"),files.map(p->p.getFileName().toString()).toList());}
    }
    @Test void corruptionUnsupportedVersionsOversizeAndTrailingFailClosed() throws Exception {
        Path file=directory.resolve("state");var store=new HcfStore(file);store.save(HcfStore.Saved.initial());byte[] valid=Files.readAllBytes(file);
        Files.write(file,Arrays.copyOf(valid,valid.length-1));assertThrows(IOException.class,store::load);
        Files.write(file,valid);byte[] version=valid.clone();version[7]=99;Files.write(file,version);assertThrows(IOException.class,store::load);
        Files.write(file,Arrays.copyOf(valid,valid.length+1));assertThrows(IOException.class,store::load);
        Files.write(file,new byte[16385]);assertThrows(IOException.class,store::load);
    }
    @Test void boundedStateRejectsDuplicateIdentityAndClassOverflow() {
        var teams=HcfStore.Saved.initial().teams();var one=new HcfStore.Member("same",0,0,0,false);
        assertThrows(IllegalArgumentException.class,()->new HcfStore.Saved(1,0,List.of(one,one),teams));
        assertThrows(IllegalArgumentException.class,()->new HcfStore.Saved(1,0,List.of(new HcfStore.Member("bardone",0,1,0,false),new HcfStore.Member("bardtwo",0,1,0,false)),teams));
        assertThrows(IllegalArgumentException.class,()->new HcfStore.Team(Double.NaN,0,0,16,0,false));
        assertThrows(IllegalArgumentException.class,()->new HcfStore.Member("../bad",0,0,0,false));
        assertThrows(IllegalArgumentException.class,()->new HcfStore.Team(0,0,0,33,0,false));
    }
}
