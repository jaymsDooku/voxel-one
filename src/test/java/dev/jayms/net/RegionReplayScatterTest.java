package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayScatterTest {
    @TempDir Path folder;
    RegionReplayCellsTest fixture(){var test=new RegionReplayCellsTest();test.folder=folder;return test;}
    List<Protocol.Edit> rows() {
        var result=new ArrayList<Protocol.Edit>();
        for(int i=0;i<32768;i++)result.add(RegionReplayWindowTest.whole(i%4096,(i&1)==0?Blocks.STONE:Blocks.GLASS));
        return result;
    }
    @Test void allWindowsRetainOriginalOrderAcrossPageSplitsEvictionsAndReuse()throws Exception {
        var rows=rows();var test=fixture();
        try(var source=test.spool(rows);var cells=RegionReplayCells.build(folder,source,RegionReplayCellsTest.REGION,rows.size(),e->true,()->false)) {
            assertTrue(cells.encodedBytes()>4L*64*1024);
            for(int start=0;start<4096;start+=64) {
                final int first=start;
                var expected=rows.stream().filter(e->{int cell=(e.x()&15)|((e.z()&15)<<4)|((e.y()&15)<<8);return cell>=first&&cell<first+64;}).toList();
                assertEquals(expected,cells.readWindow(new RegionReplayPage.CellRange(start,start+64),4097,e->true,()->false));
            }
        }
        try(var paths=Files.list(folder)){assertEquals(0,paths.count());}
    }
    @Test void cancellationAfterDirtyPageFlushDeletesCandidateAndRawCopyAndCanRetry()throws Exception {
        var rows=rows();var test=fixture();var copied=new AtomicInteger();var checks=new AtomicInteger();var candidate=new AtomicReference<Path>();var partial=new AtomicBoolean();
        try(var source=test.spool(rows)) {
            assertThrows(CancellationException.class,()->RegionReplayCells.build(folder,source,RegionReplayCellsTest.REGION,rows.size(),e->{copied.incrementAndGet();return true;},()->{
                if(copied.get()!=rows.size())return false;
                try {
                    if(candidate.get()==null)try(var paths=Files.list(folder)){paths.filter(p->p.getFileName().toString().startsWith(".replay-cells-")).findFirst().ifPresent(candidate::set);}
                    if(candidate.get()!=null&&checks.incrementAndGet()==15000) {
                        var prefix=new byte[28];try(var input=Files.newInputStream(candidate.get())){assertEquals(28,input.readNBytes(prefix,0,28));}
                        for(byte value:prefix)if(value!=0)partial.set(true);
                        return true;
                    }
                }catch(IOException failure){throw new UncheckedIOException(failure);}return false;
            }));assertTrue(partial.get(),"Dirty first page must have flushed before cancellation");
            try(var paths=Files.list(folder)){assertFalse(paths.anyMatch(p->p.getFileName().toString().startsWith(".replay-cells-")||p.getFileName().toString().startsWith(".replay-count-")));}
            try(var cells=RegionReplayCells.build(folder,source,RegionReplayCellsTest.REGION,rows.size(),e->true,()->false)){assertEquals(rows.size(),cells.size());}
        }
    }
}
