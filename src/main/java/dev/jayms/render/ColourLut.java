package dev.jayms.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Red-fast .cube LUT, applied to display-linear ACES output before gamma encoding. */
public record ColourLut(int size,float[] rgb) {
    public ColourLut {
        if(size<2||size>64||rgb.length!=size*size*size*3)throw new IllegalArgumentException("LUT dimensions");
        rgb=rgb.clone();for(float f:rgb)if(!Float.isFinite(f)||f<0||f>1)throw new IllegalArgumentException("LUT values must be finite in [0,1]");
    }
    @Override public float[] rgb(){return rgb.clone();}
    public static ColourLut identity(int size){
        float[] data=new float[size*size*size*3];int i=0;
        for(int b=0;b<size;b++)for(int g=0;g<size;g++)for(int r=0;r<size;r++){
            data[i++]=r/(size-1f);data[i++]=g/(size-1f);data[i++]=b/(size-1f);
        }
        return new ColourLut(size,data);
    }
    public static ColourLut load(Path path)throws IOException{
        int size=0;ArrayList<Float> values=new ArrayList<>();
        try(var reader=Files.newBufferedReader(path)){
        String line;
        while((line=reader.readLine())!=null){
            line=line.split("#",2)[0].trim();if(line.isEmpty()||line.startsWith("TITLE"))continue;
            String[] words=line.split("\\s+");
            if(words[0].equals("LUT_3D_SIZE")){size=Integer.parseInt(words[1]);continue;}
            if(words[0].equals("DOMAIN_MIN")){if(!line.matches("DOMAIN_MIN\\s+0(?:\\.0*)?\\s+0(?:\\.0*)?\\s+0(?:\\.0*)?"))throw new IOException("Only unit LUT domains are supported");continue;}
            if(words[0].equals("DOMAIN_MAX")){if(!line.matches("DOMAIN_MAX\\s+1(?:\\.0*)?\\s+1(?:\\.0*)?\\s+1(?:\\.0*)?"))throw new IOException("Only unit LUT domains are supported");continue;}
            if(words.length!=3)throw new IOException("Invalid LUT row");for(String word:words)values.add(Float.parseFloat(word));
            if(values.size()>64*64*64*3)throw new IOException("LUT too large");
        }
        } catch(NumberFormatException|IndexOutOfBoundsException e){throw new IOException("Invalid LUT text",e);}
        float[] rgb=new float[values.size()];for(int i=0;i<rgb.length;i++)rgb[i]=values.get(i);
        try{return new ColourLut(size,rgb);}catch(IllegalArgumentException e){throw new IOException("Invalid LUT",e);}
    }
}
