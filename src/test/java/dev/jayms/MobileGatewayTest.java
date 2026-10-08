package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.mobile.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MobileGatewayTest {
    @TempDir Path temp;
    static final HttpClient HTTP=HttpClient.newHttpClient();
    HttpResponse<String> post(MobileFixtureHost host,String route,String token,Map<String,Object> body)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create(host.url()+"/mobile/v1/"+route)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(Json.write(body)));
        if(token!=null)builder.header("Authorization","Bearer "+token);
        return HTTP.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    String token(HttpResponse<String> response){var match=java.util.regex.Pattern.compile("\\\"token\\\":\\\"([A-Za-z0-9_-]+)\\\"").matcher(response.body());assertTrue(match.find());return match.group(1);}
    @Test void loginWorldEditsReachAndLogoutUseExistingAuthoritativeServer()throws Exception{
        try(var host=new MobileFixtureHost(temp.resolve("profile"))){
            assertEquals(401,post(host,"state",null,Map.of()).statusCode());
            assertEquals(400,post(host,"login",null,Map.of("game","other","username","ios_fixture","password","fixture-password-123")).statusCode());
            var login=post(host,"login",null,Map.of("game","sandbox","username","ios_fixture","password","fixture-password-123"));assertEquals(200,login.statusCode());
            String bearer=token(login);assertTrue(login.body().contains("\"cells\""));assertTrue(login.body().contains("\"game\":\"sandbox\""));
            try(var observer=new MultiplayerClient("127.0.0.1",host.sandbox.port(),"observer","observer-pass-123".toCharArray(),true,host.fingerprint)){
                var match=java.util.regex.Pattern.compile("\\\"pose\\\":\\[(.*?)\\]").matcher(login.body());assertTrue(match.find());
                var pose=match.group(1).split(",");int x=(int)Math.floor(Double.parseDouble(pose[0])),ground=(int)Math.floor(Double.parseDouble(pose[1]))-1,z=(int)Math.floor(Double.parseDouble(pose[2]));
                var edit=post(host,"action",bearer,Map.of("kind","edit","blockX",x,"blockY",ground,"blockZ",z,"type",0,"slot",0));assertEquals(200,edit.statusCode());
                long end=System.nanoTime()+5_000_000_000L;boolean seen=false;
                while(System.nanoTime()<end && !seen){seen=observer.poll().stream().anyMatch(e->e.x()==x && e.z()==z && e.type()==0);Thread.sleep(20);}
                assertTrue(seen,"Desktop protocol observer must receive the phone edit");
                var rejected=post(host,"action",bearer,Map.of("kind","edit","blockX",500,"blockY",ground,"blockZ",500,"type",0,"slot",0));
                assertEquals(200,rejected.statusCode());assertTrue(rejected.body().contains("Placement rejected"));
            }
            assertEquals(200,post(host,"logout",bearer,Map.of()).statusCode());assertEquals(401,post(host,"state",bearer,Map.of()).statusCode());
            var again=post(host,"login",null,Map.of("game","sandbox","username","ios_fixture","password","fixture-password-123"));assertEquals(200,again.statusCode());
        }
    }
    @Test void modalIdleKeepsGameConnectionButLogoutStillRevokesSession()throws Exception{
        try(var host=new MobileFixtureHost(temp.resolve("idle-profile"))){
            var login=post(host,"login",null,Map.of("game","city","username","ios_fixture","password","fixture-password-123"));
            assertEquals(200,login.statusCode());String bearer=token(login);
            Thread.sleep(17_000); // Longer than the actual game server's 15-second socket timeout; no HTTP activity.
            assertEquals(200,post(host,"state",bearer,Map.of()).statusCode(),"Planning modal must retain server connection");
            assertEquals(200,post(host,"logout",bearer,Map.of()).statusCode());
            assertEquals(401,post(host,"state",bearer,Map.of()).statusCode());
        }
    }
    @Test void closedGameReturnsFixedTransportReasonBeforeTokenRevocation()throws Exception{
        try(var host=new MobileFixtureHost(temp.resolve("closed-profile"))){
            var login=post(host,"login",null,Map.of("game","city","username","ios_fixture","password","fixture-password-123"));
            assertEquals(200,login.statusCode());String bearer=token(login);host.city.close();
            HttpResponse<String> response=null;long end=System.nanoTime()+5_000_000_000L;
            do {Thread.sleep(50);response=post(host,"state",bearer,Map.of());}while(response.statusCode()==200 && System.nanoTime()<end);
            assertEquals(401,response.statusCode());assertTrue(response.body().contains("Game server transport closed"));
            assertFalse(response.body().contains("ios_fixture"));assertFalse(response.body().contains("fixture-password"));
            assertEquals(401,post(host,"state",bearer,Map.of()).statusCode());
        }
    }
    @Test void invalidPoseIsRejectedWithoutClosingSession()throws Exception{
        try(var host=new MobileFixtureHost(temp.resolve("pose-profile"))){
            var login=post(host,"login",null,Map.of("game","city","username","ios_fixture","password","fixture-password-123"));
            assertEquals(200,login.statusCode());String bearer=token(login);
            var invalid=Map.<String,Object>of("x",0,"y",30,"z",0,"yaw",0,"pitch",1.6);
            assertEquals(400,post(host,"move",bearer,invalid).statusCode());
            assertEquals(200,post(host,"state",bearer,Map.of()).statusCode());
            assertEquals(400,post(host,"move",bearer,Map.of("x",0,"y",Terrain.MAX_Y+33,"z",0,"yaw",0,"pitch",0)).statusCode());
            assertEquals(200,post(host,"state",bearer,Map.of()).statusCode());
            assertNull(host.city.transportFailure());
        }
    }
    @Test void syntheticServerCloseReasonIsFixedAndContainsNoAccountData()throws Exception{
        try(var host=new MobileFixtureHost(temp.resolve("reason-profile"))){
            var login=post(host,"login",null,Map.of("game","city","username","ios_fixture","password","fixture-password-123"));
            assertEquals(200,login.statusCode());String bearer=token(login);
            var field=MobileGateway.class.getDeclaredField("sessions");field.setAccessible(true);
            Object session=((Map<?,?>)field.get(host.gateway)).values().iterator().next();
            var clientField=session.getClass().getDeclaredField("client");clientField.setAccessible(true);
            var client=(MultiplayerClient)clientField.get(session);
            client.move(new Protocol.Pose(client.id,0,30,0,0,90,0,0,false,0,0,false));
            long end=System.nanoTime()+5_000_000_000L;
            while(client.connected() && System.nanoTime()<end)Thread.sleep(20);
            assertFalse(client.connected());
            assertEquals(MultiplayerServer.TransportFailure.INVALID_MOVEMENT,host.city.transportFailure());
            var response=post(host,"state",bearer,Map.of());
            assertEquals(401,response.statusCode());assertTrue(response.body().contains("Synthetic server INVALID_MOVEMENT"));
            assertFalse(response.body().contains("ios_fixture"));assertFalse(response.body().contains("fixture-password"));
        }
    }
    @Test void slowSnapshotDoesNotBlockTransportHeartbeat()throws Exception{
        try(var host=new MobileFixtureHost(temp.resolve("slow-profile"))){
            var login=post(host,"login",null,Map.of("game","city","username","ios_fixture","password","fixture-password-123"));
            assertEquals(200,login.statusCode());String bearer=token(login);
            // Reproduce a slow snapshot's exclusive world-state lock without machine-speed assumptions.
            var field=MobileGateway.class.getDeclaredField("sessions");field.setAccessible(true);
            Object session=((Map<?,?>)field.get(host.gateway)).values().iterator().next();
            synchronized(session){Thread.sleep(17_000);}
            assertEquals(200,post(host,"state",bearer,Map.of()).statusCode(),"Slow world snapshot must not disconnect game transport");
            assertEquals(200,post(host,"logout",bearer,Map.of()).statusCode());
        }
    }
    @Test void phonePoseStreamSurvivesStalledCityReader()throws Exception{
        try(var host=new MobileFixtureHost(temp.resolve("pose-stream-profile"))){
            var login=post(host,"login",null,Map.of("game","city","username","ios_fixture","password","fixture-password-123"));
            assertEquals(200,login.statusCode());String bearer=token(login);
            var match=java.util.regex.Pattern.compile("\\\"pose\\\":\\[(.*?)\\]").matcher(login.body());assertTrue(match.find());
            var pose=Arrays.stream(match.group(1).split(",")).map(Double::parseDouble).toList();
            int sequence=0;
            synchronized(host.city){
                long end=System.nanoTime()+18_000_000_000L;
                while(System.nanoTime()<end){
                    var move=Map.<String,Object>of("x",pose.get(0),"y",pose.get(1),"z",pose.get(2),"yaw",sequence*0.001,"pitch",0,"sequence",++sequence);
                    assertEquals(200,post(host,"move",bearer,move).statusCode());Thread.sleep(75);
                }
            }
            assertTrue(sequence>120,"Actual phone pose requests must exceed server burst limit during stall");
            Thread.sleep(1500); // Let the actual TLS reader drain after releasing the simulation lock.
            assertNull(host.city.transportFailure(),"Coalesced poses must retain the unchanged server rate limit");
            var road=post(host,"action",bearer,Map.of("kind","city","command",1,"value",0,"points",List.of(List.of(40,10),List.of(46,10))));
            assertEquals(200,road.statusCode());assertTrue(road.body().contains("Mayor paid"),"City command must recover after the stalled tick/reader");
            assertEquals(200,post(host,"state",bearer,Map.of()).statusCode());
            assertEquals(200,post(host,"logout",bearer,Map.of()).statusCode());
        }
    }
    @Test void receiptTimeoutReportsOnlyFixedSyntheticStage()throws Exception{
        try(var host=new MobileFixtureHost(temp.resolve("city-stage-profile"))){
            var login=post(host,"login",null,Map.of("game","city","username","ios_fixture","password","fixture-password-123"));
            assertEquals(200,login.statusCode());String bearer=token(login);
            synchronized(host.city){
                var response=java.util.concurrent.CompletableFuture.supplyAsync(()->{
                    try{return post(host,"action",bearer,Map.of("kind","city","command",1,"value",0,"points",List.of(List.of(40,10),List.of(46,10))));}
                    catch(Exception e){throw new java.util.concurrent.CompletionException(e);}
                }).get(70,java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(504,response.statusCode());
                assertTrue(response.body().matches(".*City receipt pending: (NONE|WAITING|RUNNING|REPLY_QUEUED).*"));
                assertFalse(response.body().contains("ios_fixture"));assertFalse(response.body().contains("fixture-password"));
            }
            long end=System.nanoTime()+5_000_000_000L;
            while(host.city.cityCommandStage()!=MultiplayerServer.CityCommandStage.REPLY_QUEUED && System.nanoTime()<end)Thread.sleep(20);
            assertEquals(MultiplayerServer.CityCommandStage.REPLY_QUEUED,host.city.cityCommandStage());
            assertEquals(200,post(host,"state",bearer,Map.of()).statusCode());
        }
    }
    @Test void slowCityReceiptStillConfirmsWithoutRetry()throws Exception{
        try(var host=new MobileFixtureHost(temp.resolve("delayed-city-profile"))){
            var login=post(host,"login",null,Map.of("game","city","username","ios_fixture","password","fixture-password-123"));
            assertEquals(200,login.statusCode());String bearer=token(login);
            java.util.concurrent.CompletableFuture<HttpResponse<String>> request;
            // The actual server serializes commands and simulation on this monitor.
            synchronized(host.city){
                request=java.util.concurrent.CompletableFuture.supplyAsync(()->{
                    try{return post(host,"action",bearer,Map.of("kind","city","command",1,"value",0,"points",List.of(List.of(40,10),List.of(46,10))));}
                    catch(Exception e){throw new java.util.concurrent.CompletionException(e);}
                });
                Thread.sleep(9200);
            }
            var response=request.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(200,response.statusCode());assertTrue(response.body().contains("Mayor paid"));
            assertEquals(200,post(host,"state",bearer,Map.of()).statusCode());
        }
    }
    @Test void cityCommandsAndEconomyAreServerOwned()throws Exception{
        try(var host=new MobileFixtureHost(temp.resolve("city-profile"))){
            var login=post(host,"login",null,Map.of("game","city","username","ios_fixture","password","fixture-password-123"));assertEquals(200,login.statusCode());String bearer=token(login);
            assertTrue(login.body().contains("\"treasury\""));assertTrue(login.body().contains("\"citizens\""));
            var road=post(host,"action",bearer,Map.of("kind","city","command",1,"value",0,"points",List.of(List.of(40,10),List.of(46,10))));assertEquals(200,road.statusCode());
            assertTrue(road.body().contains("\"city\""));
            var bad=post(host,"action",bearer,Map.of("kind","city","command",1,"value",0,"points",List.of()));assertEquals(200,bad.statusCode());assertTrue(bad.body().contains("two endpoints"),"City endpoint rejection was not reported");
            assertEquals(400,post(host,"state",bearer,Map.of("focusX",Double.MAX_VALUE,"focusZ",0)).statusCode());
        }
    }
    @Test void codecAndTransportRejectMalformedOrBrowserRequests()throws Exception{
        assertThrows(IllegalArgumentException.class,()->Json.object("{\"a\":1,\"a\":2}"));
        assertThrows(IllegalArgumentException.class,()->Json.object("{\"a\":NaN}"));
        assertThrows(IllegalArgumentException.class,()->Json.object("{\"a\":1e999}"));
        assertThrows(IllegalArgumentException.class,()->Json.object("{\"a\":1}junk"));
        assertEquals("quote\" line\n",Json.text(Json.object(Json.write(Map.of("a","quote\" line\n"))),"a"));
        try(var host=new MobileFixtureHost(temp.resolve("limits"))){
            var request=HttpRequest.newBuilder(URI.create(host.url()+"/mobile/v1/login")).header("Origin","https://example.com").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            assertEquals(403,HTTP.send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(413,HTTP.send(HttpRequest.newBuilder(request.uri()).POST(HttpRequest.BodyPublishers.ofString("x".repeat(8193))).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }
}
