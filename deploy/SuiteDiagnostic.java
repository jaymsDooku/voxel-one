import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import org.junit.platform.engine.*;
import org.junit.platform.engine.support.descriptor.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.*;
import static org.junit.platform.engine.discovery.ClassNameFilter.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class SuiteDiagnostic {
 public static void main(String[] args)throws Exception {
  Path progress=Path.of(args[0]),result=Path.of(args[1]);
  try(var log=new PrintWriter(Files.newBufferedWriter(progress,StandardOpenOption.CREATE_NEW))) {
   Thread owner=Thread.currentThread();var active=new AtomicReference<String>("discovery");
   var timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"suite-diagnostic");t.setDaemon(true);return t;});
   timer.scheduleAtFixedRate(()->{synchronized(log){log.println("ACTIVE "+active.get()+" STATE "+owner.getState());for(var frame:Arrays.stream(owner.getStackTrace()).limit(20).toList())log.println("FRAME "+frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber());log.flush();}},30,30,TimeUnit.SECONDS);
   var summary=new SummaryGeneratingListener();
   var listener=new TestExecutionListener(){
    public void executionStarted(TestIdentifier id){id.getSource().ifPresent(s->{String name=s instanceof MethodSource m?m.getClassName()+"."+m.getMethodName():s instanceof ClassSource c?c.getClassName():null;if(name!=null){active.set(name);synchronized(log){log.println("START "+name);log.flush();}}});}
    public void executionFinished(TestIdentifier id,TestExecutionResult r){if(id.isTest()&&r.getStatus()==TestExecutionResult.Status.FAILED)synchronized(log){log.println("FAIL "+active.get()+" "+r.getThrowable().map(t->t.getClass().getName()).orElse("unknown"));log.flush();}}
   };
   var request=LauncherDiscoveryRequestBuilder.request().selectors(selectClasspathRoots(Set.of(Path.of("target/test-classes")))).filters(includeClassNamePatterns(".*(Test|Tests|TestCase)",".*\\.Test[^.]+" )).build();
   try {LauncherFactory.create().execute(request,summary,listener);}finally{timer.shutdownNow();}
   var s=summary.getSummary();Files.writeString(result,String.format(Locale.ROOT,"{\"tests\":%d,\"succeeded\":%d,\"failed\":%d,\"aborted\":%d,\"skipped\":%d,\"containers_failed\":%d}\n",s.getTestsFoundCount(),s.getTestsSucceededCount(),s.getTestsFailedCount(),s.getTestsAbortedCount(),s.getTestsSkippedCount(),s.getContainersFailedCount()),StandardOpenOption.CREATE_NEW);
   System.exit(s.getTotalFailureCount()==0?0:1);
  }
 }
}
