package dev.jayms.render;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Versioned local player preferences. No world, account or simulation settings. */
public final class GraphicsProfile {
    public enum Preset {LOW,BALANCED,HIGH,ULTRA,CUSTOM}
    public enum Tab {DISPLAY,WORLD,LIGHTING,IMAGE,PERFORMANCE}
    public enum Key {
        DISPLAY_MODE(Tab.DISPLAY,"Window mode","WINDOWED","WINDOWED","BORDERLESS","FULLSCREEN"),
        RESOLUTION(Tab.DISPLAY,"Resolution / refresh","1280x720@60"),
        VSYNC(Tab.DISPLAY,"VSync","ON","OFF","ON"),
        FRAME_CAP(Tab.DISPLAY,"Frame cap (0 = uncapped)","0","0","30","60","120","144","240"),
        SCALE(Tab.DISPLAY,"Render scale","1","0.5","0.65","0.75","0.85","1"),
        DYNAMIC(Tab.DISPLAY,"Dynamic resolution","OFF","OFF","ON"),
        TARGET(Tab.DISPLAY,"Target frame time (ms)","16.67","16.67","25","33.33"),
        MIN_SCALE(Tab.DISPLAY,"Minimum scale","0.5","0.5","0.65","0.75","0.85","1"),
        MAX_SCALE(Tab.DISPLAY,"Maximum scale","1","0.5","0.65","0.75","0.85","1"),
        DETAIL(Tab.WORLD,"Detailed distance (blocks)","112","16","32","64","80","112"),
        HORIZON(Tab.WORLD,"Distant horizon (blocks)","2048","512","1024","1536","2048"),
        LOD(Tab.WORLD,"LOD screen error (pixels)","12","6","8","12","18","24"),
        SHADOW_DISTANCE(Tab.WORLD,"Shadow distance (blocks)","256","32","64","128","256"),
        SHADOWS(Tab.LIGHTING,"Shadow quality","HIGH","OFF","LOW","MEDIUM","HIGH","ULTRA"),
        AO(Tab.LIGHTING,"Ambient occlusion","ON","OFF","ON"),
        CONTACT(Tab.LIGHTING,"Contact shadows","ON","OFF","ON"),
        GI(Tab.LIGHTING,"Screen GI","ON","OFF","ON"),
        REFLECTIONS(Tab.LIGHTING,"Reflections","ON","OFF","ON"),
        WATER(Tab.LIGHTING,"Water reflections / refraction","ON","OFF","ON"),
        ATMOSPHERE(Tab.LIGHTING,"Local atmosphere quality","MEDIUM","LOW","MEDIUM","HIGH"),
        HAZE(Tab.LIGHTING,"Overview haze","1","0","0.5","1"),
        CLOUDS(Tab.LIGHTING,"Clouds / sky volume","ON","OFF","ON"),
        BLOOM(Tab.LIGHTING,"Bloom","ON","OFF","ON"),
        AUTO_EXPOSURE(Tab.LIGHTING,"Auto exposure","ON","OFF","ON"),
        EXPOSURE(Tab.LIGHTING,"Manual brightness","1","0.25","0.5","0.75","1","1.5","2","3","4"),
        AA(Tab.IMAGE,"Anti-aliasing","TAA","OFF","TAA","MSAA2","MSAA4"),
        MIPMAP(Tab.IMAGE,"Mipmaps","ON","OFF","ON"),
        TEXTURE(Tab.IMAGE,"Voxel texture style","SMOOTH","PIXEL","SMOOTH"),
        ANISOTROPY(Tab.IMAGE,"Anisotropy","8","1","2","4","8","16"),
        DIAGNOSTICS(Tab.PERFORMANCE,"Diagnostics overlay","OFF","OFF","ON");
        public final Tab tab;public final String label,initial;public final List<String> values;
        Key(Tab tab,String label,String initial,String...values){this.tab=tab;this.label=label;this.initial=initial;this.values=List.of(values);}
        public String help(){return switch(this){
            case DETAIL,HORIZON,LOD->"Visual terrain only. Loading, simulation, collisions and multiplayer do not change.";
            case SHADOWS,SHADOW_DISTANCE->"Changes shadow texture size and rendered shadow range; Low uses fewer cascades.";
            case AA->"TAA uses one sample. MSAA falls back to TAA with dynamic or fractional render scale.";
            case ATMOSPHERE,HAZE,CLOUDS->"Local image quality only. The world's physical atmosphere stays unchanged.";
            case MIN_SCALE,MAX_SCALE,TARGET,DYNAMIC,SCALE->"Scene resolution changes; HUD and text stay at native resolution.";
            case TEXTURE,ANISOTROPY->"Pixel uses nearest mip filtering. Smooth uses linear mip filtering.";
            case DISPLAY_MODE,RESOLUTION->"Apply starts a 15 second Keep / Revert test. Focus loss also reverts.";
            case EXPOSURE->"Enabled when auto exposure is off.";
            case GI->"Disables screen-space GI work. Voxel light blocking remains exact at every preset.";
            default->"Apply saves this device's preference. Cancel discards the draft.";
        };}
    }
    public record Capabilities(int maxSamples,int maxTextureSize,float maxAnisotropy,List<String> videoModes) {
        public Capabilities {videoModes=List.copyOf(videoModes);}
    }
    public record Effective(GraphicsProfile profile,List<String> reasons) {}
    public record Loaded(GraphicsProfile profile,String warning,List<String> overrides) {}
    private final EnumMap<Key,String> values;
    private final Preset preset;
    private GraphicsProfile(Preset preset,EnumMap<Key,String> values){this.preset=preset;this.values=new EnumMap<>(values);validate();}
    public String get(Key key){return values.get(key);}
    public boolean on(Key key){return get(key).equals("ON");}
    public int integer(Key key){return Integer.parseInt(get(key));}
    public float number(Key key){return Float.parseFloat(get(key));}
    public Preset preset(){return preset;}
    public String cost(){return switch(preset){case LOW->"Fewer effects; full horizon";case BALANCED->"Standard effects; full horizon";case HIGH->"Finer atmosphere; higher cost";case ULTRA->"Larger shadows and finer LOD; highest cost";case CUSTOM->"Manual quality / cost choices";};}
    public int width(){return Integer.parseInt(get(Key.RESOLUTION).split("[x@]")[0]);}
    public int height(){return Integer.parseInt(get(Key.RESOLUTION).split("[x@]")[1]);}
    public int refresh(){return Integer.parseInt(get(Key.RESOLUTION).split("[x@]")[2]);}
    public boolean riskyComparedTo(GraphicsProfile old){return !get(Key.DISPLAY_MODE).equals(old.get(Key.DISPLAY_MODE))||!get(Key.RESOLUTION).equals(old.get(Key.RESOLUTION));}
    public GraphicsProfile with(Key key,String value){var copy=new EnumMap<>(values);copy.put(key,value);return new GraphicsProfile(Preset.CUSTOM,copy);}
    private GraphicsProfile effectiveWith(Key key,String value){var copy=new EnumMap<>(values);copy.put(key,value);return new GraphicsProfile(preset,copy);}
    public static GraphicsProfile preset(Preset preset){
        var v=new EnumMap<Key,String>(Key.class);for(Key k:Key.values())v.put(k,k.initial);
        switch(preset){
            case LOW->{for(Key k:List.of(Key.AO,Key.CONTACT,Key.GI,Key.REFLECTIONS,Key.WATER,Key.CLOUDS,Key.BLOOM))v.put(k,"OFF");v.put(Key.SHADOWS,"LOW");v.put(Key.ATMOSPHERE,"LOW");v.put(Key.ANISOTROPY,"2");}
            case HIGH->{v.put(Key.ATMOSPHERE,"HIGH");}
            case ULTRA->{v.put(Key.ATMOSPHERE,"HIGH");v.put(Key.SHADOWS,"ULTRA");v.put(Key.ANISOTROPY,"16");v.put(Key.LOD,"6");}
            default->{}
        }
        return new GraphicsProfile(preset,v);
    }
    private void validate(){
        for(Key k:Key.values()){String v=values.get(k);if(k==Key.RESOLUTION){if(v==null||!v.matches("[0-9]{3,4}x[0-9]{3,4}@[0-9]{2,3}")||width()<320||width()>8192||height()<240||height()>8192||refresh()<30||refresh()>360)throw new IllegalArgumentException("Invalid display mode");}
            else if(!k.values.contains(v)) {
                float n;try{n=Float.parseFloat(v);}catch(Exception e){throw new IllegalArgumentException("Unsupported "+k.label);}
                float min,max;switch(k){case SCALE,MIN_SCALE,MAX_SCALE->{min=.5f;max=1;}case HAZE->{min=0;max=1;}case EXPOSURE->{min=.25f;max=4;}case TARGET->{min=5;max=100;}default->throw new IllegalArgumentException("Unsupported "+k.label);}
                if(!Float.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("Unsupported "+k.label);
            }}
        if(number(Key.MIN_SCALE)>number(Key.MAX_SCALE))throw new IllegalArgumentException("Minimum scale exceeds maximum scale");
    }
    public Effective effective(Capabilities c){
        GraphicsProfile p=this;var reasons=new ArrayList<String>();String aa=get(Key.AA);int samples=aa.equals("MSAA4")?4:aa.equals("MSAA2")?2:1;
        if(samples>1&&(number(Key.SCALE)<1||on(Key.DYNAMIC))){p=p.effectiveWith(Key.AA,"TAA");reasons.add("MSAA uses TAA with fractional scale or dynamic resolution.");}
        else if(samples>c.maxSamples){String fallback=c.maxSamples>=2?"MSAA2":"OFF";p=p.effectiveWith(Key.AA,fallback);reasons.add("Requested MSAA exceeds this device's sample limit.");}
        int shadow=shadowSize();if(shadow>c.maxTextureSize){String fallback=c.maxTextureSize>=2048?"HIGH":c.maxTextureSize>=1024?"MEDIUM":c.maxTextureSize>=512?"LOW":"OFF";p=p.effectiveWith(Key.SHADOWS,fallback);reasons.add("Shadow texture size exceeds the device limit.");}
        if(number(Key.ANISOTROPY)>c.maxAnisotropy){int a=1;for(int n:new int[]{2,4,8,16})if(n<=c.maxAnisotropy)a=n;p=p.effectiveWith(Key.ANISOTROPY,Integer.toString(a));reasons.add("Anisotropy is limited by this device.");}
        if(get(Key.DISPLAY_MODE).equals("FULLSCREEN")&&!c.videoModes.contains(get(Key.RESOLUTION))){p=p.effectiveWith(Key.DISPLAY_MODE,"WINDOWED");reasons.add("Requested fullscreen mode is not supported; using windowed.");}
        return new Effective(p,List.copyOf(reasons));
    }
    public int samples(){return switch(get(Key.AA)){case "MSAA2"->2;case "MSAA4"->4;default->1;};}
    public int shadowSize(){return switch(get(Key.SHADOWS)){case "LOW"->512;case "MEDIUM"->1024;case "HIGH"->2048;case "ULTRA"->4096;default->512;};}
    public Properties properties(){var p=new Properties();p.setProperty("version","2");p.setProperty("preset",preset.name());values.forEach((k,v)->p.setProperty(k.name(),v));return p;}
    public static GraphicsProfile parse(Properties p){int version=Integer.parseInt(p.getProperty("version","1"));if(version<1||version>2)throw new IllegalArgumentException("Unsupported graphics profile version");
        Preset preset=Preset.valueOf(p.getProperty("preset","BALANCED"));var values=new EnumMap<Key,String>(Key.class);for(Key k:Key.values())values.put(k,p.getProperty(k.name(),preset(preset==Preset.CUSTOM?Preset.BALANCED:preset).get(k)));
        if(version==1){if(p.containsKey("renderScale"))values.put(Key.SCALE,p.getProperty("renderScale"));if(p.containsKey("taa")){if(!List.of("true","false").contains(p.getProperty("taa")))throw new IllegalArgumentException("Invalid legacy TAA value");values.put(Key.AA,Boolean.parseBoolean(p.getProperty("taa"))?"TAA":"OFF");}}
        if(preset!=Preset.CUSTOM&&!values.equals(preset(preset).values))preset=Preset.CUSTOM;
        return new GraphicsProfile(preset,values);
    }
    public static Loaded load(Path file){GraphicsProfile p=preset(Preset.BALANCED);String warning="";
        try {if(Files.exists(file)){if(Files.size(file)>65536)throw new IOException("Profile too large");var props=new Properties();try(var reader=Files.newBufferedReader(file)){props.load(reader);}p=parse(props);}}
        catch(IOException|IllegalArgumentException e){warning="Invalid graphics profile; defaults restored.";}
        return applyOverrides(p,warning);
    }
    public static Loaded launchDefaults(){return applyOverrides(preset(Preset.BALANCED),"");}
    private static Loaded applyOverrides(GraphicsProfile p,String warning){
        var overrides=new ArrayList<String>();
        for(String property:List.of("voxel.noTaa","voxel.msaa","voxel.renderScale","voxel.dynamicResolution","voxel.atmosphereQuality","voxel.overviewHaze")) {
            String value=System.getProperty(property);if(value==null)continue;
            try {if((property.equals("voxel.noTaa")||property.equals("voxel.dynamicResolution"))&&!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException("Boolean override");if(property.equals("voxel.msaa")&&!List.of("1","2","4").contains(value))throw new IllegalArgumentException("MSAA override");Key key=switch(property){case "voxel.noTaa","voxel.msaa"->Key.AA;case "voxel.renderScale"->Key.SCALE;case "voxel.dynamicResolution"->Key.DYNAMIC;case "voxel.atmosphereQuality"->Key.ATMOSPHERE;default->Key.HAZE;};
                String mapped=switch(property){case "voxel.noTaa"->Boolean.parseBoolean(value)?"OFF":"TAA";case "voxel.dynamicResolution"->Boolean.parseBoolean(value)?"ON":"OFF";case "voxel.msaa"->value.equals("4")?"MSAA4":value.equals("2")?"MSAA2":"OFF";default->value.equals("1.0")?"1":value.toUpperCase(Locale.ROOT);};
                p=p.with(key,mapped);overrides.add(property);
            }catch(IllegalArgumentException e){warning="Invalid launch graphics override ignored.";}
        }
        return new Loaded(p,warning,List.copyOf(overrides));
    }
    public void save(Path file)throws IOException {
        Path absolute=file.toAbsolutePath();Files.createDirectories(absolute.getParent());Path tmp=Files.createTempFile(absolute.getParent(),"graphics-",".tmp");
        try {try(var writer=Files.newBufferedWriter(tmp)){properties().store(writer,"Local graphics preferences v2");}try(var ch=FileChannel.open(tmp,StandardOpenOption.WRITE)){ch.force(true);}try{Files.move(tmp,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,absolute,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(tmp);}
    }
    @Override public boolean equals(Object o){return o instanceof GraphicsProfile p&&preset==p.preset&&values.equals(p.values);}
    @Override public int hashCode(){return Objects.hash(preset,values);}
}
