package dev.jayms;

import dev.jayms.DistantTerrainPlan.Tile;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DistantTerrainTransitionsTest {
    private static List<Tile> children(Tile parent) {
        int x = parent.x(), z = parent.z(), size = parent.size()/2;
        return List.of(new Tile(x,z,size), new Tile(x+size,z,size),
                new Tile(x,z+size,size), new Tile(x+size,z+size,size));
    }

    @Test
    void partialRootWithoutCoarseCoverageDisplaysItsReadyQuadrants() {
        var plan = new DistantTerrainPlan(-16,-16);
        var root = new Tile(-1024,-1024,1024);
        var quadrants = children(root);
        var ready = new HashSet<>(List.of(quadrants.get(0), children(quadrants.get(3)).get(3)));
        assertEquals(ready, Set.copyOf(plan.select(ready)));
        ready.add(root);
        assertEquals(List.of(root), plan.select(ready));
    }

    @Test
    void mixedLevelsInheritOneMaskAsSiblingsArriveAndCameraMoves() {
        var plan = new DistantTerrainPlan(-16,-16);
        var transitions = new DistantTerrainTransitions();
        var root = new Tile(-1024,-1024,1024);
        var quadrants = children(root);
        var near = quadrants.get(3);
        var ready = new HashSet<>(quadrants);
        ready.remove(quadrants.get(0));
        ready.add(root);
        ready.addAll(children(near));
        var selected = plan.select(ready);
        assertEquals(List.of(root), selected);
        transitions.select(selected, ready, 1);
        assertTrue(transitions.parents().isEmpty());

        // The final sibling arrives after other quadrants already have finer meshes.
        ready.add(quadrants.get(0));
        selected = plan.select(ready);
        assertEquals(7, selected.size());
        transitions.select(selected, ready, 2);
        assertEquals(Set.of(root), transitions.parents());
        assertPartition(root, selected, transitions, 2.12f);

        // Refine another level while the original parent is still fading out.
        ready.addAll(children(children(near).get(3)));
        selected = new DistantTerrainPlan(0,-16).select(ready);
        assertEquals(10, selected.size());
        transitions.select(selected, ready, 2.2f);
        assertEquals(Set.of(root), transitions.parents());
        assertEquals(.5f, transitions.fraction(root,2.2f),.00001f);
        assertPartition(root, selected, transitions, 2.2f);
        transitions.expire(2.41f);
        assertTrue(transitions.parents().isEmpty());
        assertPartition(root, selected, transitions, 2.41f);
    }

    @Test
    void selectionWithoutAnAvailablePreviouslyDrawnParentNeverStartsAFade() {
        var root = new Tile(0,0,1024);
        var children = children(root);
        var transitions = new DistantTerrainTransitions();
        var ready = new HashSet<>(children);
        ready.add(root);
        transitions.select(children, ready, 0);
        assertTrue(transitions.parents().isEmpty()); // Fresh uploads never drawn as a parent.
        transitions.select(List.of(root), ready, .05f);
        assertTrue(transitions.parents().isEmpty()); // Coarsening.
        transitions.select(children, ready, .1f);
        assertEquals(Set.of(root), transitions.parents());
        ready.remove(root);
        transitions.select(children, ready, .2f);
        assertTrue(transitions.parents().isEmpty());
        assertPartition(root, children, transitions, .2f);
    }

    private static void assertPartition(Tile root,List<Tile> tiles,DistantTerrainTransitions fade,float now) {
        for (int x=root.x()+32;x<root.x()+root.size();x+=64) {
            for (int z=root.z()+32;z<root.z()+root.size();z+=64) {
                for (float dither : new float[]{0,.25f,.5f,.75f,.9999f}) {
                    int owners = 0;
                    for (Tile tile : tiles)
                        if (contains(tile,x,z) && dither < fade.fraction(tile,now)) owners++;
                    for (Tile parent : fade.parents())
                        if (contains(parent,x,z) && dither >= fade.fraction(parent,now)) owners++;
                    assertEquals(1, owners, "Every fragment has one terrain owner");
                }
            }
        }
    }

    private static boolean contains(Tile t,int x,int z) {
        return x>=t.x() && x<t.x()+t.size() && z>=t.z() && z<t.z()+t.size();
    }
}
