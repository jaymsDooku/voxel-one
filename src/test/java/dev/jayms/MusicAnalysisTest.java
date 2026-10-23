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
        for(int i=0;i<720;i++)assertFalse(a.sample(data,44100,2,1/60.0,false,1).beat());
        var settled=a.sample(data,44100,2,.1,false,1);assertEquals(0,settled.bass(),.0001);
        a.reset();var silence=run(a,new float[44100*3]);assertEquals(new MusicAnalysis.Bands(0,0,0,false),silence);
        a.reset();assertTrue(a.sample(data,44100,1,.016,true,1).beat());assertFalse(a.sample(data,44100,1,.016,true,1).beat());
        a.reset();assertEquals(new MusicAnalysis.Bands(0,0,0,false),a.sample(data,44100,0,0,false,1));
    }
    @Test void landscapeBandsUseDistinctAttackAndDecayTimes(){
        int[] frequencies={100,1000,6000};double[] attacks={.15,.10,.04},decays={.9,.6,.35};
        for(int band=0;band<3;band++){
            var data=tone(frequencies[band]);var a=new MusicAnalysis();
            var settled=new MusicAnalysis().sample(data,44100,1,20,true,1);
            var rising=a.sample(data,44100,1,attacks[band],true,1);
            double full=band==0?settled.bass():band==1?settled.mid():settled.treble();
            double value=band==0?rising.bass():band==1?rising.mid():rising.treble();
            assertEquals(full*(1-Math.exp(-1)),value,1e-8);
            var falling=a.sample(data,44100,1,decays[band],false,1);
            assertEquals(value/Math.E,band==0?falling.bass():band==1?falling.mid():falling.treble(),1e-8);
        }
    }
    @Test void pulsedSignalHasBoundedOnsets(){
        float[] data=tone(100);for(int i=0;i<data.length;i++)if(i%22050>6000)data[i]=0;
        var a=new MusicAnalysis();int beats=0;for(int i=1;i<180;i++)if(a.sample(data,44100,i/60.0,1/60.0,true,1).beat())beats++;
        assertTrue(beats>=4&&beats<=7,"beats="+beats);
    }
    static MusicTrack.Decoded stereoFixture(Path file,int hz,int mode)throws Exception {
        int rate=44100;byte[] pcm=new byte[rate*3*4];
        for(int i=0;i<rate*3;i++){
            short v=(short)(.4*32767*Math.sin(2*Math.PI*hz*i/rate));
            short left=mode==3?0:v,right=mode==1?(short)-v:mode==2?0:v;
            pcm[i*4]=(byte)left;pcm[i*4+1]=(byte)(left>>8);
            pcm[i*4+2]=(byte)right;pcm[i*4+3]=(byte)(right>>8);
        }
        try(var input=new AudioInputStream(new ByteArrayInputStream(pcm),new AudioFormat(rate,16,2,true,false),rate*3)){
            AudioSystem.write(input,AudioFileFormat.Type.WAVE,file.toFile());
        }
        var decoded=MusicTrack.decode(file);
        for(int i=0;i<decoded.stereo().length;i++)
            assertEquals((short)((pcm[i*2]&255)|(pcm[i*2+1]<<8)),decoded.stereo()[i],"Playback PCM unchanged");
        assertEquals(3,decoded.duration(),.0001);return decoded;
    }
    static MusicAnalysis.Bands stereoBands(MusicTrack.Decoded data){
        var analysis=new MusicAnalysis();MusicAnalysis.Bands result=null;
        for(int i=1;i<=120;i++)result=analysis.sample(data.stereo(),data.rate(),i/60.0,1/60.0,true,1);
        return result;
    }
    @Test void antiphaseAndSingleChannelDecodePreserveBands(@TempDir Path dir)throws Exception {
        for(int hz:new int[]{100,1000,6000}){
            var normal=stereoBands(stereoFixture(dir.resolve("normal.wav"),hz,0));
            var anti=stereoBands(stereoFixture(dir.resolve("anti.wav"),hz,1));
            var left=stereoBands(stereoFixture(dir.resolve("left.wav"),hz,2));
            var right=stereoBands(stereoFixture(dir.resolve("right.wav"),hz,3));
            double[] expected={normal.bass(),normal.mid(),normal.treble()};
            double[] opposite={anti.bass(),anti.mid(),anti.treble()};
            double[] one={left.bass(),left.mid(),left.treble()};
            double[] other={right.bass(),right.mid(),right.treble()};
            for(int band=0;band<3;band++){
                assertEquals(expected[band],opposite[band],1e-6,"Antiphase preserves frequency energy");
                assertEquals(expected[band]/Math.sqrt(2),one[band],1e-4,"One-channel RMS normalization (below 0.0001 envelope gate)");
                assertEquals(one[band],other[band],1e-6,"Either channel can drive analysis");
            }
            assertTrue(Math.max(anti.bass(),Math.max(anti.mid(),anti.treble()))>.5);
        }
    }
    @Test void antiphaseOnsetsAndStereoSilence(@TempDir Path dir)throws Exception {
        var data=stereoFixture(dir.resolve("pulses.wav"),100,1);
        for(int frame=0;frame<data.stereo().length/2;frame++)if(frame%22050>6000){data.stereo()[frame*2]=0;data.stereo()[frame*2+1]=0;}
        var a=new MusicAnalysis();int beats=0;
        for(int i=1;i<180;i++)if(a.sample(data.stereo(),44100,i/60.0,1/60.0,true,1).beat())beats++;
        assertTrue(beats>=4&&beats<=7,"Antiphase beats="+beats);
        for(int i=0;i<720;i++)a.sample(data.stereo(),44100,2.9,1/60.0,false,1);
        assertEquals(0,a.sample(data.stereo(),44100,2.9,.1,false,1).bass(),.0001);
        a.reset();assertEquals(new MusicAnalysis.Bands(0,0,0,false),a.sample(new short[88200],44100,1,1,true,1));
    }

    @Test void localDecodeAndRecoverableFailures(@TempDir Path dir)throws Exception{
        byte[] bytes=new byte[44100*2];for(int i=0;i<44100;i++){short v=(short)(10000*Math.sin(2*Math.PI*100*i/44100));bytes[i*2]=(byte)v;bytes[i*2+1]=(byte)(v>>8);}
        Path wav=dir.resolve("tone.wav");try(var in=new AudioInputStream(new ByteArrayInputStream(bytes),new AudioFormat(44100,16,1,true,false),44100)){AudioSystem.write(in,AudioFileFormat.Type.WAVE,wav.toFile());}
        var decoded=MusicTrack.decode(wav);assertEquals(1,decoded.duration(),.0001);assertEquals(88200,decoded.stereo().length);assertEquals(decoded.stereo()[400],decoded.stereo()[401]);
        Path bad=dir.resolve("bad.wav");Files.writeString(bad,"not audio");assertThrows(Exception.class,()->MusicTrack.decode(bad));assertThrows(Exception.class,()->MusicTrack.decode(dir.resolve("missing.wav")));
        assertEquals("Voxel Music Visualizer",GameIdentity.parse("music").title);
    }
}
