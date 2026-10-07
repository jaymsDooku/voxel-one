import dev.jayms.*;
import dev.jayms.net.Blocks;
import dev.jayms.player.Player;
import org.joml.Vector3f;

/** Synthetic CLI regression for swept Player collision; no account or graphics state. */
public class RenderingPhysicsCollisionSmoke {
    static void require(boolean b,String text){if(!b)throw new AssertionError(text);}
    static float move(World world,float start,float delta)throws Exception{
        var player=new Player(new Vector3f(start,1,3.5f),0,0,new Camera());
        var method=Player.class.getDeclaredMethod("moveAxis",World.class,float.class,int.class);
        method.setAccessible(true);method.invoke(player,world,delta,0);
        return player.position().x;
    }
    public static void main(String[] args)throws Exception{
        try(var world=new World()){
            world.addChunk(new ChunkPos(0,0,0),new Chunk());
            world.setBlock(3,1,3,Blocks.STONE);world.setBlock(3,2,3,Blocks.STONE);
            float forward=move(world,2,3),reverse=move(world,5,-3);
            require(forward<=2.7f&&forward>2.69f,"Forward wall sweep: "+forward);
            require(reverse>=4.3f&&reverse<4.31f,"Reverse wall sweep: "+reverse);
            world.setBlock(3,1,3,0);world.setBlock(3,2,3,0);
            float free=move(world,2,3),boundary=move(world,15,3);
            require(Math.abs(free-5)<.0001f,"Free movement regression: "+free);
            require(boundary<=15.7f&&boundary>15.69f,"Unloaded boundary: "+boundary);
            System.out.println("Playtest: CLI PASS. Synthetic world; forward delta=3: x="+forward+" (expected <=2.7); reverse delta=-3: x="+reverse+" (expected >=4.3); empty space x="+free+" (expected 5); unloaded boundary x="+boundary+" (expected <=15.7). Browser playtesting does not apply to this collision CLI.");
        }
    }
}
