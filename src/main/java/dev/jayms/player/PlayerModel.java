package dev.jayms.player;

import static dev.jayms.player.PlayerAnimation.*;

import dev.jayms.*;
import dev.jayms.net.*;

import org.joml.Matrix4f;

public class PlayerModel implements AutoCloseable {
    private final Mesh cube;

    public PlayerModel() {
        Chunk source = new Chunk();
        source.setBlock(0, 0, 0, ChunkGenerator.STONE);
        source.generateMesh();
        cube = source.getMesh();
    }

    public void render(Protocol.Pose player, ShaderProgram shader, VoxelModelRenderer models) {
        shader.setInt("uVertexColor", 0);
        shader.setInt("uInstanced", 0);
        Matrix4f root =
                new Matrix4f()
                        .translate(player.x(), player.y(), player.z())
                        .rotateY((float) Math.toRadians(-player.yaw() - 90));
        var pose =
                pose(
                        player.walkPhase(),
                        player.walkAmount(),
                        player.swingProgress(),
                        player.heldItem() != 0);
        Matrix4f torso = new Matrix4f(root).rotateY(pose.bodyTwist());
        Matrix4f head =
                new Matrix4f(torso)
                        .translate(0, 24 * PIXEL, 0)
                        .rotateX((float) Math.toRadians(-player.pitch()));
        skin(shader);
        box(shader, head, HEAD, HEAD, HEAD);
        // Original simple face and hair, built from colored cuboids.
        shader.setVector3("uColor", .23f, .15f, .10f);
        box(
                shader,
                new Matrix4f(head).translate(0, 6 * PIXEL, 0),
                HEAD + .002f,
                2 * PIXEL,
                HEAD + .002f);
        for (float x : new float[] {-1.7f * PIXEL, 1.7f * PIXEL}) {
            shader.setVector3("uColor", .95f, .96f, .92f);
            box(
                    shader,
                    new Matrix4f(head).translate(x, 3.6f * PIXEL, -HEAD / 2 - .004f),
                    1.5f * PIXEL,
                    PIXEL,
                    .008f);
            shader.setVector3("uColor", .13f, .22f, .32f);
            box(
                    shader,
                    new Matrix4f(head).translate(x, 3.6f * PIXEL, -HEAD / 2 - .010f),
                    .7f * PIXEL,
                    PIXEL,
                    .008f);
        }
        shader.setVector3("uColor", .38f, .23f, .15f);
        box(
                shader,
                new Matrix4f(head).translate(0, 1.8f * PIXEL, -HEAD / 2 - .004f),
                2.5f * PIXEL,
                .65f * PIXEL,
                .008f);
        shirt(shader);
        box(
                shader,
                new Matrix4f(torso).translate(0, LIMB_HEIGHT, 0),
                BODY_WIDTH,
                BODY_HEIGHT,
                DEPTH);
        arm(
                shader,
                new Matrix4f(torso).translate(-5 * PIXEL, 22 * PIXEL, 0).rotateX(pose.leftArm()),
                -1);
        Matrix4f rightArm =
                new Matrix4f(torso)
                        .translate(5 * PIXEL, 22 * PIXEL, 0)
                        .rotateY(-pose.attack() * .3f)
                        .rotateZ(-pose.attack() * .2f)
                        .rotateX(pose.rightArm());
        arm(shader, rightArm, 1);
        shader.setVector3("uColor", .17f, .22f, .40f);
        leg(shader, root, -2 * PIXEL, pose.leftLeg());
        leg(shader, root, 2 * PIXEL, pose.rightLeg());
        held(
                player.heldItem(),
                new Matrix4f(rightArm)
                        .translate(PIXEL, -9 * PIXEL, -2 * PIXEL)
                        .rotateX(-.35f)
                        .rotateY(.4f)
                        .scale(.35f)
                        .translate(-.5f, -.1f, -.5f),
                shader,
                models);
    }

    public void renderFirstPerson(Player player, ShaderProgram shader, VoxelModelRenderer models) {
        float attack = attack(player.swingProgress());
        float bob = (float) Math.sin(player.walkPhase()) * player.walkAmount() * .015f;
        Matrix4f hand =
                new Matrix4f()
                        .translate(
                                .52f - attack * .25f,
                                -.90f + bob + attack * .15f,
                                -.7f - attack * .12f)
                        .rotateY(-.15f - attack * .45f)
                        .rotateZ(-.12f - attack * .2f)
                        .rotateX(-.35f + attack * .5f);
        shader.setInt("uVertexColor", 0);
        shader.setInt("uInstanced", 0);
        skin(shader);
        box(shader, new Matrix4f(hand).translate(0, .25f, 0), LIMB_WIDTH, .5f, DEPTH);
        shirt(shader);
        box(shader, hand, LIMB_WIDTH, .25f, DEPTH);
        held(
                player.heldItem(),
                new Matrix4f(hand)
                        .translate(-.14f, .78f, .18f)
                        .rotateY(.55f)
                        .rotateX(-.2f)
                        .scale(.32f)
                        .translate(-.5f, -.1f, -.5f),
                shader,
                models);
    }

    private void held(
            int type, Matrix4f transform, ShaderProgram shader, VoxelModelRenderer models) {
        if (type == 0) return;
        if (Blocks.isModel(type)) models.renderHeld(type, transform, shader);
        else {
            shader.setInt("uVertexColor", 0);
            float[] color = Blocks.color(type);
            shader.setVector3("uColor", color[0], color[1], color[2]);
            shader.setMatrix4("uModel", transform);
            cube.render();
        }
    }

    private void skin(ShaderProgram shader) {
        shader.setVector3("uColor", .88f, .68f, .46f);
    }

    private void shirt(ShaderProgram shader) {
        shader.setVector3("uColor", .16f, .46f, .86f);
    }

    private void arm(ShaderProgram shader, Matrix4f pivot, int side) {
        skin(shader);
        box(
                shader,
                new Matrix4f(pivot).translate(side * PIXEL, -10 * PIXEL, 0),
                LIMB_WIDTH,
                8 * PIXEL,
                DEPTH);
        shirt(shader);
        box(
                shader,
                new Matrix4f(pivot).translate(side * PIXEL, -2 * PIXEL, 0),
                LIMB_WIDTH,
                4 * PIXEL,
                DEPTH);
    }

    private void leg(ShaderProgram shader, Matrix4f root, float x, float angle) {
        box(
                shader,
                new Matrix4f(root)
                        .translate(x, LIMB_HEIGHT, 0)
                        .rotateX(angle)
                        .translate(0, -LIMB_HEIGHT, 0),
                LIMB_WIDTH,
                LIMB_HEIGHT,
                DEPTH);
    }

    public void renderDrop(ItemDrop drop, float time, ShaderProgram shader) {
        shader.setInt("uVertexColor", 0);
        float[] c = Blocks.color(drop.type());
        shader.setVector3("uColor", c[0], c[1], c[2]);
        box(
                shader,
                new Matrix4f()
                        .translate(
                                drop.x(),
                                drop.y() + .06f * (float) Math.sin(time * 3 + drop.id()),
                                drop.z())
                        .rotateY(time + drop.id()),
                .25f,
                .25f,
                .25f);
    }

    private void box(ShaderProgram shader, Matrix4f root, float w, float h, float d) {
        shader.setMatrix4("uModel", new Matrix4f(root).scale(w, h, d).translate(-.5f, 0, -.5f));
        cube.render();
    }

    @Override
    public void close() {
        cube.close();
    }
}
