package dev.jayms.launcher;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

/** Real refresh, offline fallback and detail provenance in the running Hub. */
public final class CatalogStatusPlaytest {
 public static void main(String[] args)throws Exception {
  Path profile=Path.of(args[0]);Path output=Path.of(args[1]);Files.createDirectories(profile);Files.createDirectories(output);
  System.setProperty("user.home",profile.toAbsolutePath().toString());Files.writeString(profile.resolve("hub.properties"),"updates=false\nserver=127.0.0.1\n");
  String version="1234567890abcdef1234";String template;
  try(var in=HubCatalog.class.getResourceAsStream("catalog.properties")){template=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.ISO_8859_1);}
  byte[] body=template.replaceFirst("(?m)^version=.*$","version="+version).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
  var offline=new AtomicBoolean();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/catalog",e->{if(offline.get()){e.sendResponseHeaders(503,-1);e.close();return;}e.sendResponseHeaders(200,body.length);try(var out=e.getResponseBody()){out.write(body);}});server.start();
  HubFrame[] frame=new HubFrame[1];
  try{
   URI url=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/catalog");
   SwingUtilities.invokeAndWait(()->{try{frame[0]=new HubFrame(profile,Updater.MANIFEST,new CatalogStore(profile,url),uri->{});frame[0].setSize(900,600);frame[0].setVisible(true);}catch(Exception e){throw new RuntimeException(e);}});
   PrePlayCatalogPlaytest.frame=frame[0];PrePlayCatalogPlaytest.output=output;
   SwingUtilities.invokeAndWait(frame[0]::refresh);PrePlayCatalogPlaytest.await(()->PrePlayCatalogPlaytest.text("Library up to date"));
   for(String page:new String[]{"Home","Games"}){
    SwingUtilities.invokeAndWait(()->frame[0].showPage(page));
    if(PrePlayCatalogPlaytest.text(version))throw new AssertionError("Technical catalog ID in ordinary page");
   }
   PrePlayCatalogPlaytest.capture("friendly-catalog-status");
   var entry=HubCatalog.bundled().entries().get(0);SwingUtilities.invokeAndWait(()->frame[0].detail(entry));
   if(!PrePlayCatalogPlaytest.text("Catalog: "+version))throw new AssertionError("Detail provenance lost");
   // Scroll after layout so the captured detail actually shows the version.
   Thread.sleep(250);
   SwingUtilities.invokeAndWait(()->{for(var c:PrePlayCatalogPlaytest.children(frame[0]))if(c instanceof JScrollPane scroll)scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());});
   PrePlayCatalogPlaytest.capture("catalog-version-detail");
   offline.set(true);SwingUtilities.invokeAndWait(()->frame[0].showPage("Games"));SwingUtilities.invokeAndWait(frame[0]::refresh);
   PrePlayCatalogPlaytest.await(()->PrePlayCatalogPlaytest.text("catalog service unavailable"));
   if(PrePlayCatalogPlaytest.text("Library up to date"))throw new AssertionError("Offline refresh claimed current library");
   PrePlayCatalogPlaytest.capture("catalog-offline-status");
   if(!Files.readString(profile.resolve("catalog.properties")).contains("version="+version))throw new AssertionError("Verified cache changed");
   System.out.println("Playtest: actual catalog refresh uses friendly Home/Games status; exact catalog version retained in detail; HTTP503 shows unavailable status and preserves verified cache.");
  }finally{server.stop(0);if(frame[0]!=null)SwingUtilities.invokeAndWait(frame[0]::dispose);}
 }
}
