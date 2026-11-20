package dev.jayms.net;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import dev.jayms.net.model.*;

public class CheckSceneRevision {
    static void require(boolean b){if(!b)throw new AssertionError("Saved-scene revision check failed");}
    static int[] add(ModelLibrary models)throws Exception {
        var prepared=models.copy();int[] ids=new int[2];
        for(int i=0;i<2;i++){var tree=new SparseVoxelOctree(8);tree.fill(0,0,0,8,8,8,i==0?0xff00bfa5:0xffbc40dd);ids[i]=prepared.stageStructureModel(new ModelDefinition("Added component "+i,tree)).id();}
        long before=models.revision();models.replaceWith(prepared);require(models.revision()==before+1);require(prepared.revision()==models.revision()+1);return ids;
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),report=Path.of(args[1]);boolean expected=Boolean.parseBoolean(args[2]);Files.createDirectory(root);
        long liveRevision=0,sceneRevision=0,parsedRevision=0;boolean accepted=false;String error="";
        var tests=new ReadbackSaveSnapshotTest();tests.folder=Files.createDirectory(root.resolve("contexts"));
        try(var c=tests.new Context("probe")) {
            int[] ids=add(c.local.models);liveRevision=c.local.models.revision();
            var editor=new RegionReplayEditor(c.world,new RegionReplayJournal());editor.apply(new Protocol.Edit(8,160,0,ids[0]),()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,editor.capture(()->false),()->false);
            var old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);editor.apply(PendingReplayOverlayTest.fraction(),()->false);c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
                parsedRevision=new LocalGame(c.path,0).models.revision();require(liveRevision!=parsedRevision);
                var directory=Files.createDirectory(root.resolve("paging"));
                try(var replacement=new SavedPagingReplacement(publication,old,directory,SavedPagingFactoryTest.NEXT,1,8192,()->false)) {
                    SavedPagingReplacementTest.ready(replacement);var transfer=replacement.take(()->false);var worker=transfer.worker();
                    try(transfer) {
                        var session=transfer.session();sceneRevision=session.local().models.revision();require(session.local().models!=c.local.models);require(c.local.models.revision()==liveRevision);
                        for(var entry:c.local.models.entries()){var actual=session.local().models.get(entry.id());require(actual!=null&&actual.author().equals(entry.author())&&actual.definition().fingerprint().equals(entry.definition().fingerprint()));}
                        var job=session.binding().submitSky(0,0,RegionReplaySkyWorkerTest.LIMITS,session.world());RegionReplayWorkerTest.done(job);
                        try{session.binding().skyResult(job,session.world());accepted=true;}catch(java.io.IOException failure){error=failure.getMessage();if(!"Replay sky source changed".equals(error))throw failure;}
                    }finally{RegionReplayWorkerTest.closed(worker);}
                }
            }
        }
        require(accepted==expected);require((sceneRevision==parsedRevision)==expected);
        Files.writeString(report,"{\"live_revision\":"+liveRevision+",\"scene_revision\":"+sceneRevision+",\"parsed_revision\":"+parsedRevision+",\"sky_accepted\":"+accepted+",\"binding_data_preserved\":true,\"retired_revision_unchanged\":true,\"error\":\""+error+"\",\"qualification\":false}\n");
    }
}
