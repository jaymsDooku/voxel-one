package dev.jayms;

import dev.jayms.music.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import javax.sound.sampled.*;
import java.io.*;

class MusicAnalysisTest {
    static float[] tone(int hz){float[] data=new float[44100*3];for(int i=0;i<data.length;i++)data[i]=(float)(.4*Math.sin(2*Math.PI*hz*i/44100));return data;}
    static MusicAnalysis.Bands run(MusicAnalysis a,float[] data){MusicAnalysis.Bands b=null;for(int i=1;i<=120;i++)b=a.sample(data,44100,i/60.0,1/60.0,true,1);return b;}
    @Test void bandsAreDistinctAndSensitivityChangesGain(){
        var bass=run(new MusicAnalysis(),tone(100));var mid=run(new MusicAnalysis(),tone(1000));var high=run(new MusicAnalysis(),tone(6000));
        assertTrue(bass.bass()>.5&&bass.mid()<.01&&bass.treble()<.01,bass.toString());
        assertTrue(mid.mid()>.5&&mid.bass()<.01&&mid.treble()<.01,mid.toString());
        assertTrue(high.treble()>.5&&high.bass()<.01&&high.mid()<.01,high.toString());
        var a=new MusicAnalysis();var low=a.sample(tone(100),44100,1,.1,true,.25);a.reset();var loud=a.sample(tone(100),44100,1,.1,true,2);assertTrue(loud.bass()>low.bass()*2);
    }
    @Test void pauseSilenceResetAndClockDoNotLeakHistory(){
        var a=new MusicAnalysis();var data=tone(100);run(a,data);
        for(int i=0;i<180;i++)assertFalse(a.sample(data,44100,2,1/60.0,false,1).beat());
        var settled=a.sample(data,44100,2,.1,false,1);assertEquals(0,settled.bass(),.0001);
        a.reset();var silence=run(a,new float[44100*3]);assertEquals(new MusicAnalysis.Bands(0,0,0,false),silence);
        a.reset();assertTrue(a.sample(data,44100,1,.016,true,1).beat());assertFalse(a.sample(data,44100,1,.016,true,1).beat());
        a.reset();assertEquals(new MusicAnalysis.Bands(0,0,0,false),a.sample(data,44100,0,0,false,1));
    }
    @Test void pulsedSignalHasBoundedOnsets(){
        float[] data=tone(100);for(int i=0;i<data.length;i++)if(i%22050>6000)data[i]=0;
        var a=new MusicAnalysis();int beats=0;for(int i=1;i<180;i++)if(a.sample(data,44100,i/60.0,1/60.0,true,1).beat())beats++;
        assertTrue(beats>=4&&beats<=7,"beats="+beats);
    }
    @Test void localDecodeAndRecoverableFailures(@TempDir Path dir)throws Exception{
        byte[] bytes=new byte[44100*2];for(int i=0;i<44100;i++){short v=(short)(10000*Math.sin(2*Math.PI*100*i/44100));bytes[i*2]=(byte)v;bytes[i*2+1]=(byte)(v>>8);}
        Path wav=dir.resolve("tone.wav");try(var in=new AudioInputStream(new ByteArrayInputStream(bytes),new AudioFormat(44100,16,1,true,false),44100)){AudioSystem.write(in,AudioFileFormat.Type.WAVE,wav.toFile());}
        var decoded=MusicTrack.decode(wav);assertEquals(1,decoded.duration(),.0001);assertEquals(88200,decoded.stereo().length);assertEquals(decoded.stereo()[400]/32768f,decoded.mono()[200],.0001);
        Path bad=dir.resolve("bad.wav");Files.writeString(bad,"not audio");assertThrows(Exception.class,()->MusicTrack.decode(bad));assertThrows(Exception.class,()->MusicTrack.decode(dir.resolve("missing.wav")));
        assertEquals("Voxel Music Visualizer",GameIdentity.parse("music").title);
    }
}
