package dev.jayms.render;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jayms.render.GraphicsProfile.*;
import static org.junit.jupiter.api.Assertions.*;
class GraphicsProfileTest {
 @TempDir Path dir;
 @Test void presetsKeepCoverageAndManualChangeSelectsCustom(){for(Preset p:Preset.values()){var g=preset(p);assertEquals(112,g.integer(Key.DETAIL));assertEquals(2048,g.integer(Key.HORIZON));assertEquals(1,g.samples());assertEquals(Preset.CUSTOM,g.with(Key.VSYNC,"OFF").preset());}assertFalse(preset(Preset.LOW).on(Key.GI));assertTrue(preset(Preset.ULTRA).on(Key.GI));}
 @Test void atomicRoundTripAndRecovery()throws Exception {Path file=dir.resolve("graphics.properties");var g=preset(Preset.HIGH).with(Key.FRAME_CAP,"120");g.save(file);assertEquals(g,load(file).profile());Files.writeString(file,"version=2\nSCALE=NaN\n");assertFalse(load(file).warning().isEmpty());assertEquals(preset(Preset.BALANCED),load(file).profile());assertEquals(1,Files.list(dir).count());}
 @Test void migrationAndInvalidDraft(){var p=new Properties();p.setProperty("version","1");p.setProperty("renderScale","0.75");p.setProperty("taa","false");var g=parse(p);assertEquals("0.75",g.get(Key.SCALE));assertEquals("OFF",g.get(Key.AA));assertThrows(IllegalArgumentException.class,()->g.with(Key.SCALE,"Infinity"));assertThrows(IllegalArgumentException.class,()->g.with(Key.MIN_SCALE,"1").with(Key.MAX_SCALE,"0.5"));}
 @Test void aaAndCapabilitiesAreHonest(){var cap=new Capabilities(2,1024,1,List.of("1920x1080@60"));var wanted=preset(Preset.ULTRA).with(Key.AA,"MSAA4");var effective=wanted.effective(cap);assertEquals("MSAA2",effective.profile().get(Key.AA));assertEquals("MEDIUM",effective.profile().get(Key.SHADOWS));assertEquals("1",effective.profile().get(Key.ANISOTROPY));assertFalse(effective.reasons().isEmpty());assertEquals("TAA",wanted.with(Key.SCALE,"0.75").effective(cap).profile().get(Key.AA));assertEquals("TAA",wanted.with(Key.DYNAMIC,"ON").effective(cap).profile().get(Key.AA));assertEquals("WINDOWED",wanted.with(Key.DISPLAY_MODE,"FULLSCREEN").effective(cap).profile().get(Key.DISPLAY_MODE));}
 @Test void legacyFileMigrationDoesNotOverrideNewPreferences()throws Exception {
  Files.writeString(dir.resolve("rendering.properties"),"renderScale=0.65\nshadows=false\naa=bad\ntaa=false\nao=false\nexposure=0.1\noverviewHaze=NaN\ndynamicResolution=not-a-boolean\n");
  Path file=dir.resolve("graphics.properties");var migrated=load(file).profile();
  assertEquals(.65f,migrated.number(Key.SCALE));assertEquals("OFF",migrated.get(Key.SHADOWS));assertEquals("OFF",migrated.get(Key.AA));assertFalse(migrated.on(Key.AO));assertEquals(.1f,migrated.number(Key.EXPOSURE));assertEquals(1,migrated.number(Key.HAZE));assertFalse(migrated.on(Key.DYNAMIC));
  var saved=preset(Preset.HIGH).with(Key.SCALE,"0.85");saved.save(file);assertEquals(saved,load(file).profile());
  var rows=new Properties();rows.setProperty("particles","false");rows.setProperty("taa","false");var msaa=preset(Preset.BALANCED).with(Key.AA,"MSAA4");assertEquals("MSAA4",fromLegacy(msaa,rows).get(Key.AA));
 }
}
