import dev.jayms.render.AtmosphereReference;
import dev.jayms.net.atmosphere.AtmosphereConfig;
import java.nio.file.*;
/** Higher-angular-resolution comparison of the original isotropic closure. */
public class AtmosphereAngularReference {
 public static void main(String[] args)throws Exception{
  var c=AtmosphereConfig.earth();var report=new StringBuilder("Numerical: Earth RGB isotropic closure; 16/32/64/128 vs 256 Fibonacci directions; both 256 path and 256 solar samples. No claim of full spectral transport equivalence.\n");
  double maximum=0;
  for(double height:new double[]{100,30000})for(double mu:new double[]{-.03,0,.8})for(int directions:new int[]{16,32,64,128}){
   var low=AtmosphereReference.multiple(c,height,mu,directions,256,256);var high=AtmosphereReference.multiple(c,height,mu,256,256,256);double error=0;
   for(int i=0;i<3;i++)error=Math.max(error,Math.abs(low.component(i)-high.component(i))/Math.max(.01,high.component(i)));
   maximum=Math.max(maximum,error);report.append("directions="+directions+", height="+height+" m, sun cosine="+mu+", low="+low+", reference="+high+", max RGB normalized error="+error+"\n");
  }
  report.append("Maximum normalized error="+maximum+"; denominator floor 0.01 linear radiance. Measured comparison only; shader angular convergence is a separate acceptance check.\n");
  Files.writeString(Path.of(args[0]),report.toString());System.out.println("Angular reference comparison completed");
 }
}
