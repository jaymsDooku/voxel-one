package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.model.*;
import dev.jayms.ui.ModelSculptor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ModelSculptorTest {
    @Test
    void emptyModelsCanBeStartedOnTheFloorAndExtendedFromVisibleFaces() {
        var tree = new SparseVoxelOctree(32);
        var origin = new Vector3f(.5f, 2, .5f);
        var ray = new Vector3f(0, -1, 0);
        var target = ModelSculptor.target(tree, origin, ray, ModelSculptor.Tool.ADD, 2);
        assertEquals(0, target.y());
        assertTrue(ModelSculptor.apply(tree, target, ModelSculptor.Tool.ADD, 0xff123456));
        assertEquals(8, tree.occupied());
        target = ModelSculptor.target(tree, origin, ray, ModelSculptor.Tool.ADD, 2);
        assertEquals(2, target.y());
        assertTrue(ModelSculptor.apply(tree, target, ModelSculptor.Tool.ADD, 0xffaabbcc));
        assertEquals(16, tree.occupied());
    }

    @Test
    void paintEraseAndPickOperateOnTheHitSurfaceWithoutFillingEmptyVoxels() {
        var tree = new SparseVoxelOctree(32);
        tree.fill(8, 0, 8, 12, 4, 12, 0xff123456);
        var origin = new Vector3f(.3f, 2, .3f);
        var ray = new Vector3f(0, -1, 0);
        var picked = ModelSculptor.target(tree, origin, ray, ModelSculptor.Tool.PICK, 1);
        assertEquals(0xff123456, picked.color());
        var painted = ModelSculptor.target(tree, origin, ray, ModelSculptor.Tool.PAINT, 8);
        assertTrue(ModelSculptor.apply(tree, painted, ModelSculptor.Tool.PAINT, 0xffaa0000));
        assertEquals(64, tree.occupied());
        assertEquals(0xffaa0000, tree.get(9, 3, 9));
        var erased = ModelSculptor.target(tree, origin, ray, ModelSculptor.Tool.ERASE, 4);
        assertTrue(ModelSculptor.apply(tree, erased, ModelSculptor.Tool.ERASE, 0));
        assertEquals(0, tree.occupied());
    }

    @Test
    void missAndOutsideBoundsDoNotEditTheModel() {
        var tree = new SparseVoxelOctree(32);
        assertNull(
                ModelSculptor.target(
                        tree,
                        new Vector3f(2, 2, 2),
                        new Vector3f(0, -1, 0),
                        ModelSculptor.Tool.ADD,
                        1));
        assertNull(
                ModelSculptor.target(
                        tree,
                        new Vector3f(.5f, 2, .5f),
                        new Vector3f(0, -1, 0),
                        ModelSculptor.Tool.ERASE,
                        1));
    }
}
