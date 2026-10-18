import java.nio.file.*;
public class PrepareOwnerJfr {
 public static void main(String[] args)throws Exception {
  String xml=jdk.jfr.Configuration.getConfiguration("profile").getContents();
  StringBuilder extra=new StringBuilder();
  for(String name:new String[]{"jdk.SafepointBegin","jdk.SafepointEnd","jdk.GCPhasePause","jdk.JavaMonitorEnter","jdk.ThreadPark"}){
   xml=xml.replaceAll("(?s)<event name=\""+name.replace(".","\\.")+"\">.*?</event>","");
   extra.append("<event name=\"").append(name).append("\"><setting name=\"enabled\">true</setting><setting name=\"threshold\">0 ms</setting></event>\n");
  }
  Files.writeString(Path.of(args[0]),xml.replace("</configuration>",extra+"</configuration>"));
 }
}
