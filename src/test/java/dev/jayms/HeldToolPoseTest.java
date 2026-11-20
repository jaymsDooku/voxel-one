package dev.jayms;

import dev.jayms.net.Items;
import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.player.*;
import org.joml.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeldToolPoseTest {
    private Vector3f anchor() { return new Vector3f(HeldToolPose.ANCHOR_X,HeldToolPose.ANCHOR_Y,HeldToolPose.ANCHOR_Z); }
    @Test void swordModelsHaveThinHighlightedEdgesAndTaperToAPoint() {
        for (int item : new int[]{Items.WOODEN_SWORD, Items.STONE_SWORD}) {
            var mesh = Items.get(item).model().voxels();
            assertEquals(32, mesh.size());
            assertEquals(10, sliceArea(mesh, 20));
            assertEquals(8, sliceArea(mesh, 23));
            assertEquals(6, sliceArea(mesh, 25));
            assertEquals(4, sliceArea(mesh, 27));
            assertEquals(2, sliceArea(mesh, 30));
            assertEquals(1, sliceArea(mesh, 31));
            assertEquals(0, mesh.get(18, 20, 15), "The cutting edge is one voxel deep");
            assertEquals(mesh.get(18, 20, 16), mesh.get(16, 31, 16), "The cutting edge meets the point");
            assertNotEquals(mesh.get(16, 20, 16), mesh.get(18, 20, 16), "The blade flat contrasts with the bevel");
            assertEquals(48, sliceArea(mesh, 12));
        }
    }

    private static int sliceArea(SparseVoxelOctree mesh, int y) {
        int count = 0;
        for (int x = 0; x < mesh.size(); x++)
            for (int z = 0; z < mesh.size(); z++)
                if (mesh.get(x, y, z) != 0) count++;
        return count;
    }
    @Test void lowerHandleStaysInsidePalmThroughEveryAnimatedPose() {
        for(int id=191;id<=198;id++) for(float progress:new float[]{0,.2f,.36f,.72f,.9f,1}) {
            var pose=PlayerAnimation.pose(2,.6f,progress,true,false);
            var arm=new Matrix4f().translate(.3f,1.4f,0).rotateY(.7f).rotateX(pose.rightArm());
            Vector3f contact=HeldToolPose.world(id,arm).transformPosition(anchor());
            Vector3f local=new Matrix4f(arm).invert().transformPosition(contact);
            assertEquals(PlayerAnimation.PIXEL,local.x,1e-5);
            assertTrue(local.y > -10*PlayerAnimation.PIXEL && local.y < -8*PlayerAnimation.PIXEL);
            assertEquals(-PlayerAnimation.DEPTH/2,local.z,1e-5);
            var hand=PlayerAnimation.firstPersonHand(2,.6f,progress,false);
            local=new Matrix4f(hand).invert().transformPosition(HeldToolPose.firstPerson(id,hand).transformPosition(anchor()));
            assertTrue(local.y>.5f && local.y<.75f);
            assertTrue(java.lang.Math.abs(local.x)<PlayerAnimation.LIMB_WIDTH/2);
            assertTrue(java.lang.Math.abs(local.z)<PlayerAnimation.DEPTH/2);
        }
    }
    @Test void headsRiseAboveGripAtRestAndNeverReflect() {
        for(int id=Items.WOODEN_PICKAXE;id<=Items.STONE_AXE;id++) {
            var voxels=Items.get(id).model().voxels();
            int x=(int)(HeldToolPose.ANCHOR_X*voxels.size());
            int y=(int)(HeldToolPose.ANCHOR_Y*voxels.size());
            int z=(int)(HeldToolPose.ANCHOR_Z*voxels.size());
            assertNotEquals(0,voxels.get(x,y,z),"Anchor must lie in the actual handle mesh");
            assertEquals(voxels.get(x,2,z),voxels.get(x,y,z),"Grip stays on handle below the guard/head");
            var arm=new Matrix4f().rotateX(PlayerAnimation.pose(0,0,1,true).rightArm());
            for(var m:new Matrix4f[]{HeldToolPose.world(id,arm),HeldToolPose.firstPerson(id,PlayerAnimation.firstPersonHand(0,0,1,false))}) {
                var up=m.transformDirection(new Vector3f(0,1,0)).normalize();
                assertTrue(up.y>.8f,"Diagonal shaft rises above fist at idle");
                assertTrue(m.determinant()>0,"Head is not mirrored");
                var base=m.transformPosition(anchor());
                var head=m.transformPosition(new Vector3f(.5f,12f/16,.5f));
                assertTrue(head.y-base.y>.25f,"Head clears palm");
            }
        }
        assertThrows(IllegalArgumentException.class,()->HeldToolPose.world(Items.STICKS,new Matrix4f()));
    }
}
