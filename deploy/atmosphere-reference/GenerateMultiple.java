import dev.jayms.render.AtmosphereReference;
public class GenerateMultiple {
 public static void main(String[] args){System.out.print("[");boolean first=true;for(String name:new String[]{"earth","alien","thin","hazy","airless"})for(double[] point:new double[][]{{0,.5},{5000,-.2},{20000,.9}}){var c=Generate.profile(name);var light=AtmosphereReference.multiple(c,point[0],point[1],8,8,4);if(!first)System.out.print(",");first=false;System.out.print("{\"profile\":\""+name+"\",\"altitude\":"+point[0]+",\"sunCosine\":"+point[1]+",\"radiance\":"+Generate.vec(light)+"}");}System.out.println("]");}
}
