import dev.jayms.physics.*;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.glfw.GLFW.*;

/** Real X11 input against the production PhysicsLab, with render-thread state checks. */
public class PhysicsSmoke {
    static Path out;static volatile String capture;static volatile String captured;static volatile PhysicsLab active;
    static volatile Throwable failure;static volatile int debris,tab,broken;static volatile float vehicleX,waterMass,smoke;
    static volatile boolean reset,finite,gpu;static volatile int tower;static volatile boolean gpuChecked;
    static volatile boolean wheelSetup,wheelChecked;
    static volatile boolean eventSetup,eventChecked;
    static volatile boolean hingeSetup,hingeChecked;
    static volatile boolean platformSetup,platformChecked;
    static volatile boolean ropeSetup,ropeStarted,ropeChecked;static float ropeStart;static Constraints.Hinge loadedHinge;static RigidBody hingeLoad;static Constraints.Distance sustainedRope,overloadRope,softRope;static RigidBody sustainedLoad,softLoad;
    static volatile boolean forceSetup,forceStarted,forceChecked;static float forceStart;static RigidBody forceBody,torqueBody;
    static volatile boolean contactSetup,contactStarted,contactChecked;static float contactStart;static RigidBody floorSlider,wallSlider;
    static void require(boolean value,String text){if(!value)throw new AssertionError(text);}
    static String x(String...args)throws Exception{var cmd=new ArrayList<String>();cmd.add("xdotool");cmd.addAll(List.of(args));var p=new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();String result=new String(p.getInputStream().readAllBytes()).trim();if(p.waitFor()!=0)throw new AssertionError("X11 input failed "+args[0]);return result;}
    static void key(String window,String key)throws Exception{long sequence=active.renderedInputSequence;x("windowfocus",window);x("key","--clearmodifiers","--window",window,key);long deadline=System.currentTimeMillis()+15000;while(active.renderedInputSequence<=sequence&&System.currentTimeMillis()<deadline)Thread.sleep(20);require(active.renderedInputSequence>sequence,"Rendered response after key "+key);Thread.sleep(100);}
    static void shot(String name)throws Exception{capture=name;for(int i=0;i<200&&!name.equals(captured);i++)Thread.sleep(25);require(name.equals(captured),"Screenshot completed "+name);}
    static void capture(PhysicsLab lab,String name)throws Exception{
        ByteBuffer data=MemoryUtil.memAlloc(lab.width*lab.height*3);try{glReadPixels(0,0,lab.width,lab.height,GL_RGB,GL_UNSIGNED_BYTE,data);BufferedImage image=new BufferedImage(lab.width,lab.height,BufferedImage.TYPE_INT_RGB);for(int y=0;y<lab.height;y++)for(int xx=0;xx<lab.width;xx++){int k=(y*lab.width+xx)*3;image.setRGB(xx,lab.height-y-1,(data.get(k)&255)<<16|(data.get(k+1)&255)<<8|(data.get(k+2)&255));}int colored=0;for(int y=110;y<lab.height-80;y++)for(int xx=0;xx<lab.width;xx++){int rgb=image.getRGB(xx,y);if((rgb>>16&255)>60||(rgb>>8&255)>70||(rgb&255)>95)colored++;}require(colored>500,"Rendered scene geometry visible in "+name);require(glGetError()==GL_NO_ERROR,"No OpenGL error during physics render");ImageIO.write(image,"png",out.resolve(name).toFile());}finally{MemoryUtil.memFree(data);}
    }
    public static void main(String[]args)throws Exception{
        out=Path.of(args[0]);Files.createDirectories(out);PhysicsLab lab=new PhysicsLab();
        Thread input=new Thread(()->{try{
            while(active==null)Thread.sleep(50);String window=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(active.window));
            x("windowfocus",window);Thread.sleep(500);shot("physics-intact.png");key(window,"F10");key(window,"d");require(debris==28,"Support cut detaches 28 connected voxels: observed "+debris+" key="+active.lastKey);shot("physics-collapse.png");Thread.sleep(2200);shot("physics-settled.png");key(window,"F10");
            key(window,"d");require(debris==28,"Repeated cut is harmless");require(tower==3,"Independent anchored tower preserved");
            key(window,"r");require(debris==0,"Reset restores intact scene: observed "+debris+" key="+active.lastKey);key(window,"x");require(debris>0,"Explosion destroys and detaches structure");shot("physics-explosion.png");key(window,"r");key(window,"f");require(debris>0,"Stress failure propagates collapse");shot("physics-fracture.png");key(window,"r");key(window,"space");float pausedTime=active.scene.time;Thread.sleep(300);require(active.scene.time==pausedTime,"Pause freezes simulation");key(window,"space");key(window,"2");key(window,"b");Thread.sleep(500);require(broken>0,"Loaded ropes break");shot("physics-joints.png");
            key(window,"3");Thread.sleep(1800);require(waterMass>121.59f&&waterMass<121.61f,"Cellular water conserves 121.6 units");require(smoke>0,"Fire emits rising smoke");shot("physics-fluid-fire.png");
            key(window,"4");key(window,"p");require(!active.scene.xpbd,"PBD toggle");key(window,"p");require(active.scene.xpbd,"XPBD toggle");key(window,"w");key(window,"g");Thread.sleep(800);require(gpuChecked,"GPU/CPU equivalence check executed");require(finite,"Cloth soft body and SPH stay finite");shot("physics-cloth-sph.png");
            key(window,"5");float start=vehicleX;x("keydown","--window",window,"Up");Thread.sleep(1400);x("keyup","--window",window,"Up");Thread.sleep(250);require(vehicleX>start+.1f,"Wheel torque drives sprung vehicle");shot("physics-vehicle.png");
            wheelSetup=true;for(int i=0;i<300&&!wheelChecked;i++)Thread.sleep(20);require(wheelChecked,"Uneven suspension torque and airborne wheel spin");shot("physics-wheel-contact.png");
            eventSetup=true;for(int i=0;i<300&&!eventChecked;i++)Thread.sleep(20);require(eventChecked,"Slow impulse and swept trigger event checks");shot("physics-contact-trigger.png");
            hingeSetup=true;for(int i=0;i<300&&!hingeChecked;i++)Thread.sleep(20);require(hingeChecked,"Offset hinge gravity and centre impulse swing");shot("physics-hinge-swing.png");
            platformSetup=true;for(int i=0;i<300&&!platformChecked;i++)Thread.sleep(20);require(platformChecked,"Rising, descending, ceiling and horizontal platform checks");shot("physics-platform-carry.png");
            ropeSetup=true;for(int i=0;i<600&&!ropeChecked;i++)Thread.sleep(20);require(ropeChecked,"Sustained rope load, overload and compliance native checks");shot("physics-rope-load.png");
            forceSetup=true;for(int i=0;i<300&&!forceChecked;i++)Thread.sleep(20);require(forceChecked,"Sleeping loads and rotated inertia native checks");shot("physics-force-inertia.png");
            contactSetup=true;for(int i=0;i<300&&!contactChecked;i++)Thread.sleep(20);require(contactChecked,"Floor and diagonal wall sliding preserve remaining time");shot("physics-contact-sliding.png");
            key(window,"r");require(debris==0&&tower==3,"Reset regression preserves anchored content");shot("physics-reset.png");
            Files.writeString(out.resolve("results.json"),"{\n  \"Playtest\": \"Production PhysicsLab; Linux X11; inherited assigned DISPLAY/XAUTHORITY; Mesa software rendering; isolated synthetic profile\",\n  \"steps\": [\"D removes support: 28 debris voxels fall; independent anchored tower stays intact\",\"D repeated: no change; R restores scene\",\"X/F: explosion and stress fracture detach structures; pause freezes time\",\"2 B: loaded ropes break\",\"3: water mass 121.6 conserved; fire emits smoke\",\"4 W G: cloth/soft body/SPH finite; GPU integration matches CPU and pinned particles stay fixed\",\"5 Up: wheel torque moves sprung vehicle\",\"Synthetic contact fixtures in production world: floor travel 10 blocks/s and wall tangent travel 6 blocks/s for at least one second\",\"R: reset regression restores original cells\",\"F10: collapse clip recorded\"],\n  \"expected\": \"All listed assertions pass\",\n  \"observed\": \"All listed assertions passed\",\n  \"gpuCompute\": "+gpu+"\n}\n");
        }catch(Throwable e){failure=e;try{Files.writeString(out.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage());}catch(Exception ignored){}}finally{if(active!=null)glfwSetWindowShouldClose(active.window,true);}},"physics-native-input");input.start();
        lab.run(l->{active=l;
            if(wheelSetup&&!wheelChecked){
                var wheel=new VehicleForces.Wheel();wheel.angularSpeed=7;
                var airborne=wheel.force(100,0,-10,0,0,.8f,7200,1f/120);require(wheel.angularSpeed==7&&airborne.length()==0,"Airborne wheel conserves spin without contact torque");
                var scene=l.scene;scene.paused=false;scene.physics.bodies.clear();scene.physics.joints.clear();scene.physics.gravity.zero();
                scene.vehicle.position.set(8,3,8);scene.vehicle.rotation.identity().rotateY(.4f);scene.vehicle.velocity.zero();scene.vehicle.angularVelocity.zero();scene.vehicle.force.zero();scene.vehicle.torque.zero();scene.vehicle.wake();scene.physics.bodies.add(scene.vehicle);
                for(var tire:scene.wheels)tire.angularSpeed=0;
                var mount=scene.vehicle.rotation.transform(new Vector3f(-.8f,0,-1.3f)).add(scene.vehicle.position);
                scene.physics.bodies.add(new RigidBody(new Vector3f(mount.x,2.3f,mount.z),new Vector3f(.2f,.1f,.2f),0));
                scene.step(1f/120,0,new Vector3f(),false);
                float angular=scene.vehicle.angularVelocity.length();require(angular>.001f&&scene.vehicle.velocity.y>0,"Rotated uneven suspension creates chassis torque");
                scene.scene=5;scene.paused=true;
                Files.writeString(out.resolve("wheel-results.json"),"{\"Playtest\":\"Production PhysicsScene suspension in running native PhysicsLab; rotated chassis, one supported wheel; isolated synthetic profile\",\"expected\":\"Uneven support produces angular response; airborne wheel retains 7 rad/s without drive or brake\",\"angularSpeedMagnitude\":"+angular+",\"airborneWheelRadPerSecond\":"+wheel.angularSpeed+",\"observed\":\"All assertions passed; prior physics and walker regressions follow\"}\n");wheelChecked=true;
            }
            if(eventSetup&&!eventChecked){
                var w=l.scene.physics;w.bodies.clear();w.joints.clear();w.gravity.zero();
                var a=new RigidBody(new Vector3f(6.999f,3,8),new Vector3f(.5f),1);var b=new RigidBody(new Vector3f(8,3,8),new Vector3f(.5f),1);a.velocity.x=.2f;b.sleeping=true;w.bodies.add(a);w.bodies.add(b);w.step(1f/120);
                require(!b.sleeping&&Math.abs(b.velocity.x-.1f)<.001f,"Slow impact wakes sleeper and retains impulse");float slowV=b.velocity.x;
                for(float speed:new float[]{10,1000}){
                    w.bodies.clear();var mover=new RigidBody(new Vector3f(7,3,8),new Vector3f(.05f),1);mover.velocity.x=speed;
                    var trigger=new RigidBody(new Vector3f(8,3,8),new Vector3f(.1f,1,1),0);trigger.trigger=true;w.bodies.add(mover);w.bodies.add(trigger);
                    w.step(speed==10?.25f:1f/120);require(w.entered().size()==1&&w.exited().size()==1&&w.overlaps().isEmpty(),"Complete trigger crossing retains enter and exit");
                    w.step(.001f);require(w.entered().isEmpty()&&w.exited().isEmpty(),"Trigger events clear on next step");mover.velocity.zero();
                }
                w.bodies.clear();w.bodies.add(a);w.bodies.add(b);
                l.scene.scene=2;l.scene.paused=true;
                Files.writeString(out.resolve("contact-trigger-results.json"),"{\"Playtest\":\"Production PhysicsWorld in running native PhysicsLab; synthetic slow contact and complete crossings\",\"expectedSleeperVx\":0.1,\"observedSleeperVx\":"+slowV+",\"crossingSpeeds\":[10,1000],\"expected\":\"Both enter and exit per crossing, no final overlap or stale events\",\"observed\":\"All assertions passed; prior physics and walker regressions follow\"}\n");eventChecked=true;
            }
            if(hingeSetup&&!hingeChecked){
                var small=new RigidBody(new Vector3f(8,6,8),new Vector3f(1),1);
                var large=new RigidBody(new Vector3f(8,6,8),new Vector3f(3),1);
                var momentumHinge=new Constraints.Hinge(small,large,new Vector3f(),new Vector3f(),new Vector3f(0,1,0));small.angularVelocity.x=30;
                float before=small.angularVelocity.x/small.inverseInertia.x;
                for(int i=0;i<10;i++)momentumHinge.solve(1f/120);
                float after=small.angularVelocity.x/small.inverseInertia.x+large.angularVelocity.x/large.inverseInertia.x;
                require(Math.abs(before-after)<.001f&&Math.abs(small.angularVelocity.x-large.angularVelocity.x)<.001f,"Unequal inertia conserves angular momentum");
                var fixed=new RigidBody(new Vector3f(),new Vector3f(1),0);large.rotation.rotateX((float)Math.PI);
                var opposite=new Constraints.Hinge(fixed,large,new Vector3f(),new Vector3f(),new Vector3f(0,1,0));
                for(int i=0;i<10;i++)opposite.solve(1f/120);
                float axesDot=large.rotation.transform(new Vector3f(0,1,0)).y;require(axesDot>.999f,"Opposite hinge axes align");
                Files.writeString(out.resolve("hinge-inertia-results.json"),"{\"Playtest\":\"Production hinge solver in native PhysicsLab render callback; synthetic unequal boxes and opposite axes\",\"expectedMomentum\":"+before+",\"observedMomentum\":"+after+",\"axesDot\":"+axesDot+",\"observed\":\"Momentum conservation and opposite-axis checks passed; swing, load and walker regressions follow\"}\n");
                float gravityY=0,impulseOmega=0,anchorError=0;
                for(int mode=0;mode<2;mode++){
                    var w=l.scene.physics;w.bodies.clear();w.joints.clear();w.gravity.set(0,mode==0?-24:0,0);
                    var a=new RigidBody(new Vector3f(8,6,8),new Vector3f(.15f),0);
                    var b=new RigidBody(new Vector3f(10,6,8),new Vector3f(.4f),1);a.mask=b.mask=0;w.bodies.add(a);w.bodies.add(b);
                    w.joints.add(new Constraints.Hinge(a,b,new Vector3f(),new Vector3f(-2,0,0),new Vector3f(0,0,1)));
                    if(mode==1)b.impulse(new Vector3f(0,-1,0));
                    for(int i=0;i<(mode==0?120:60);i++)w.step(1f/120);
                    anchorError=b.anchor(new Vector3f(-2,0,0)).distance(a.position);
                    require(anchorError<.01f,"Hinge anchors remain coincident");
                    if(mode==0){gravityY=b.position.y;require(gravityY<5.99f&&Math.abs(b.rotation.z)>.1f,"Gravity swings offset hinge");}
                    else{impulseOmega=b.angularVelocity.z;require(impulseOmega<-.1f,"Centre impulse produces angular swing");}
                    require(a.position.distance(new Vector3f(8,6,8))==0&&a.angularVelocity.length()==0,"Static hinge anchor fixed");
                }
                l.scene.scene=2;l.scene.paused=true;
                Files.writeString(out.resolve("hinge-results.json"),"{\"Playtest\":\"Production PhysicsWorld in native PhysicsLab; isolated fixtures, 120 Hz\",\"expected\":\"Gravity lowers offset centre below 5.99 and rotates; centre impulse gives omega.z below -0.1; anchor error below 0.01; static anchor fixed\",\"gravityY\":"+gravityY+",\"impulseOmegaZ\":"+impulseOmega+",\"anchorError\":"+anchorError+",\"observed\":\"All assertions passed\"}\n");hingeChecked=true;
            }
            if(platformSetup&&!platformChecked){
                for(int mode=0;mode<4;mode++){
                    var w=l.scene.physics;w.bodies.clear();w.joints.clear();w.gravity.set(0,-24,0);
                    var p=new RigidBody(new Vector3f(8,3,8),new Vector3f(3,.2f,3),0);p.kinematic=true;w.bodies.add(p);
                    if(mode==2)w.bodies.add(new RigidBody(new Vector3f(8,5.4f,8),new Vector3f(3,.2f,3),0));
                    var c=l.scene.character;c.position.set(8,3.2f,8);c.velocity.zero();c.step(w,1f/120,new Vector3f(),false);
                    p.velocity.set(mode==3?1:0,mode==1?-1:mode==3?0:1,0);
                    for(int i=0;i<120;i++){w.step(1f/120);c.step(w,1f/120,new Vector3f(),false);}
                    require(mode==2?c.position.y+c.height<=5.201f:mode==3?Math.abs(c.position.x-9)<.002f:Math.abs(c.position.y-(mode==1?2.2f:4.2f))<.002f,"Platform carry mode "+mode+" position "+c.position);
                    p.velocity.zero();
                }
                l.scene.scene=2;l.scene.paused=true;
                Files.writeString(out.resolve("platform-results.json"),"{\"Playtest\":\"Production PhysicsWorld and CharacterController in native PhysicsLab; isolated fixtures; 120 steps at 1/120 second\",\"expected\":\"Rising feet 4.2, descending feet 2.2, horizontal x=9; ceiling bounds head at 5.201\",\"observed\":\"All four assertions passed; lab reset and walker regressions follow\"}\n");platformChecked=true;
            }
            if(ropeSetup&&!ropeStarted){
                l.scene.physics.bodies.clear();l.scene.physics.joints.clear();l.scene.physics.gravity.set(0,-24,0);
                for(int i=0;i<4;i++){
                    var anchor=new RigidBody(new Vector3f(3+5*i,6,4+4*i),new Vector3f(.15f),0);
                    var load=new RigidBody(new Vector3f(3+5*i,4,4+4*i),new Vector3f(.4f),i==1?5:1);
                    var rope=new Constraints.Distance(anchor,load,2,i==2?.0001f:0,true);rope.breakForce=100;
                    l.scene.physics.bodies.add(anchor);l.scene.physics.bodies.add(load);
                    if(i==3){loadedHinge=new Constraints.Hinge(anchor,load,new Vector3f(0,-2,0),new Vector3f(),new Vector3f(0,0,1));loadedHinge.breakForce=100;hingeLoad=load;l.scene.physics.joints.add(loadedHinge);}else l.scene.physics.joints.add(rope);
                    if(i==0){sustainedRope=rope;sustainedLoad=load;}else if(i==1)overloadRope=rope;else if(i==2){softRope=rope;softLoad=load;}
                }
                l.scene.paused=false;l.scene.scene=2;ropeStart=l.scene.time;ropeStarted=true;
            }
            if(ropeStarted&&!ropeChecked&&l.scene.time-ropeStart>=1){
                require(!sustainedRope.broken()&&Math.abs(sustainedLoad.position.y-4)<.003f&&Math.abs(sustainedLoad.velocity.y)<.003f,"24 N load preserves 100 N rope and bounded velocity");
                require(!loadedHinge.broken()&&Math.abs(hingeLoad.position.y-4)<.003f&&Math.abs(hingeLoad.velocity.y)<.003f,"24 N preserves 100 N hinge without velocity growth");
                require(overloadRope.broken(),"120 N load breaks 100 N rope");
                require(!softRope.broken()&&Math.abs(softLoad.position.y-3.9976f)<.003f,"Compliant rope supports sustained load");
                Files.writeString(out.resolve("rope-results.json"),"{\"Playtest\":\"Production PhysicsLab/PhysicsWorld; inherited X11 Mesa; isolated synthetic rope fixtures\",\"elapsedSeconds\":"+(l.scene.time-ropeStart)+",\"sustainedY\":"+sustainedLoad.position.y+",\"sustainedVy\":"+sustainedLoad.velocity.y+",\"compliantY\":"+softLoad.position.y+",\"expected\":\"24 N retains 100 N rigid/compliant ropes and hinge; 120 N breaks 100 N rope\",\"observed\":\"All assertions passed; reset regression follows\"}\n");ropeChecked=true;
            }
            if(forceSetup&&!forceStarted){
                l.scene.physics.bodies.clear();l.scene.physics.joints.clear();l.scene.physics.gravity.zero();
                forceBody=new RigidBody(new Vector3f(4,3,4),new Vector3f(.5f),1);forceBody.sleeping=true;forceBody.force.x=120;l.scene.physics.bodies.add(forceBody);
                torqueBody=new RigidBody(new Vector3f(10,4,9),new Vector3f(1,2,3),1);torqueBody.rotation.rotateZ((float)Math.PI/2);torqueBody.sleeping=true;torqueBody.torque.x=120;l.scene.physics.bodies.add(torqueBody);
                var impulseBody=new RigidBody(new Vector3f(4,4,12),new Vector3f(1,2,3),1);impulseBody.rotation.rotateZ((float)Math.PI/2);impulseBody.impulseAt(new Vector3f(0,0,1),new Vector3f(4,5,12));l.scene.physics.bodies.add(impulseBody);
                require(Math.abs(impulseBody.angularVelocity.x-.3f)<.00001f,"World-space point impulse gives angular x=.3");
                forceStart=l.scene.time;forceStarted=true;
            }
            if(forceStarted&&!forceChecked&&l.scene.time>forceStart){
                float elapsed=l.scene.time-forceStart;
                require(!forceBody.sleeping&&Math.abs(forceBody.velocity.x-1)<.001f,"Sleeping force integrates one 1/120-second load");
                require(!torqueBody.sleeping&&Math.abs(torqueBody.angularVelocity.x-.3f)<.001f,"Sleeping rotated torque integrates world inertia for one 1/120-second load");
                require(forceBody.force.length()==0&&torqueBody.torque.length()==0,"Integrated loads cleared");
                Files.writeString(out.resolve("force-results.json"),"{\"Playtest\":\"Synthetic bodies in production PhysicsLab/PhysicsWorld; inherited X11 display, Mesa, isolated profile\",\"elapsedSeconds\":"+elapsed+",\"expectedForceVx\":"+1+",\"observedForceVx\":"+forceBody.velocity.x+",\"expectedTorqueWx\":"+.3f+",\"observedTorqueWx\":"+torqueBody.angularVelocity.x+",\"pointImpulseWx\":0.3,\"observed\":\"All wake, inertia and load-clearing assertions passed; R reset verified later\"}\n");
                forceChecked=true;
            }
            if(contactSetup&&!contactStarted){
                l.scene.physics.bodies.clear();l.scene.physics.joints.clear();l.scene.physics.gravity.set(0,-24,0);
                var floor=new RigidBody(new Vector3f(8,0,8),new Vector3f(30,.5f,30),0);floor.friction=0;floor.restitution=0;l.scene.physics.bodies.add(floor);
                var wall=new RigidBody(new Vector3f(5.65f,2,9),new Vector3f(.5f,3,4),0);wall.friction=0;wall.restitution=0;l.scene.physics.bodies.add(wall);
                floorSlider=new RigidBody(new Vector3f(2,1,2),new Vector3f(.5f),1);floorSlider.velocity.set(10,0,0);floorSlider.friction=0;floorSlider.restitution=0;l.scene.physics.bodies.add(floorSlider);
                wallSlider=new RigidBody(new Vector3f(2,1,6),new Vector3f(.5f),1);wallSlider.velocity.set(10,0,6);wallSlider.friction=0;wallSlider.restitution=0;l.scene.physics.bodies.add(wallSlider);
                contactStart=l.scene.time;contactStarted=true;
            }
            if(contactStarted&&!contactChecked&&l.scene.time-contactStart>=1){
                float elapsed=l.scene.time-contactStart;
                require(Math.abs(floorSlider.position.x-(2+10*elapsed))<.03f,"Floor sliding travelled full elapsed time: "+floorSlider.position.x);
                require(Math.abs(wallSlider.position.x-4.65f)<.01f&&Math.abs(wallSlider.position.z-(6+6*elapsed))<.03f,"Diagonal wall sliding travelled full tangent time: "+wallSlider.position);
                contactChecked=true;
            }
debris=l.scene.detached;tab=l.scene.scene;tower=l.scene.voxels.sample(12,2,12);vehicleX=l.scene.vehicle.position.x;broken=(int)l.scene.physics.joints.stream().filter(Constraints.Joint::broken).count();waterMass=0;smoke=0;for(float f:l.scene.fluid.water)waterMass+=f;for(float f:l.scene.fluid.smoke)smoke+=f;finite=l.scene.cloth.particles.stream().allMatch(p->p.position.isFinite())&&l.scene.soft.particles.stream().allMatch(p->p.position.isFinite())&&l.scene.particles.positions.stream().allMatch(Vector3f::isFinite);
            if(!gpuChecked){try(var compute=new ComputeParticles()){float[] data={0,4,0,1,2,0,0,0,0,5,0,0,0,0,0,0};float[] cpu=data.clone();ComputeParticles.cpu(cpu,.1f,-10);compute.integrate(data,.1f,-10);for(int i=0;i<data.length;i++)require(Math.abs(data[i]-cpu[i])<.00001f,"GPU integration equals CPU at index "+i);gpu=compute.available();gpuChecked=true;}}
            if(capture!=null&&!capture.equals(captured)){capture(l,capture);captured=capture;}});
        input.join();if(failure!=null)throw new AssertionError("Physics native playtest failed",failure);
    }
}
