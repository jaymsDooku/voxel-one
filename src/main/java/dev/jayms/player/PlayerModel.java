package dev.jayms.player;

import static dev.jayms.player.PlayerAnimation.*;

import dev.jayms.*;
import dev.jayms.net.*;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PlayerModel implements AutoCloseable {
    private final Mesh cube;
    private int cohort = -1;
    private boolean seated;
    private String activity = "";
    private double animationSeconds;

    public void renderCitizen(
            Protocol.Pose pose,
            int group,
            boolean riding,
            String activity,
            double seconds,
            ShaderProgram shader,
            VoxelModelRenderer models) {
        cohort = group;
        seated = riding;
        this.activity = activity;
        animationSeconds = seconds;
        render(pose, shader, models);
        cohort = -1;
        seated = false;
        this.activity = "";
    }

    public void renderRider(Protocol.Pose pose, ShaderProgram shader, VoxelModelRenderer models) {
        seated = true;
        render(pose, shader, models);
        seated = false;
    }

    public PlayerModel() {
        Chunk source = new Chunk();
        source.setBlock(0, 0, 0, ChunkGenerator.STONE);
        source.generateMesh();
        cube = source.getMesh();
    }

    public void render(Protocol.Pose player, ShaderProgram shader, VoxelModelRenderer models) {
        render(player, shader, models, -1);
    }

    public void render(Protocol.Pose player, ShaderProgram shader, VoxelModelRenderer models, int jerseyColor) {
        render(player, shader, models, jerseyColor, false, 0);
    }

    private void render(Protocol.Pose player, ShaderProgram shader, VoxelModelRenderer models, int jerseyColor,
                        boolean hasBow, float drawTicks) {
        shader.setInt("uVertexColor", 0);
        shader.setInt("uInstanced", 0);
        Matrix4f root = new Matrix4f().translate(player.x(), player.y(), player.z());
        if (cohort >= 0 && CitizenAnimation.sleeping(activity))
            // Align along the house's depth; a station's travel yaw could put the head in a wall.
            root.translate(0, .3f, 0).rotateX((float) Math.PI / 2);
        else root.rotateY((float) Math.toRadians(-player.yaw() - 90));
        boolean standingPlayer = cohort < 0 && !seated;
        if (standingPlayer) root.scale(PlayerSkin.PLAYER_SCALE);
        var pose =
                pose(
                        player.walkPhase(),
                        player.walkAmount(),
                        player.swingProgress(),
                        player.heldItem() != 0,
                        player.placingSwing());
        if (cohort >= 0)
            pose = CitizenAnimation.pose(pose, activity, animationSeconds, player.id());
        boolean drawingBow = hasBow && drawTicks > 0;
        Matrix4f torso = new Matrix4f(root).rotateY(drawingBow ? 0 : pose.bodyTwist());
        Matrix4f head = headPose(root, player.pitch());
        skin(shader);
        box(shader, head, HEAD, HEAD, HEAD);
        // Inflated outer skin layer: no visible face shares the base head depth plane.
        shader.setVector3("uColor", .23f, .15f, .10f);
        box(
                shader,
                new Matrix4f(head).translate(0, PlayerSkin.HAIR_BOTTOM, 0),
                PlayerSkin.HAIR_WIDTH,
                PlayerSkin.HAIR_HEIGHT,
                PlayerSkin.HAIR_WIDTH);
        float eyeBase = standingPlayer ? PlayerSkin.PLAYER_EYE_BASE : 3.6f * PIXEL;
        for (float x : new float[] {-1.7f * PIXEL, 1.7f * PIXEL}) {
            shader.setVector3("uColor", .95f, .96f, .92f);
            box(
                    shader,
                    new Matrix4f(head).translate(x, eyeBase, -HEAD / 2 - .004f),
                    1.5f * PIXEL,
                    PIXEL,
                    .008f);
            shader.setVector3("uColor", .13f, .22f, .32f);
            box(
                    shader,
                    new Matrix4f(head).translate(x, eyeBase, -HEAD / 2 - .010f),
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
        shirt(shader, jerseyColor);
        box(
                shader,
                new Matrix4f(torso).translate(0, LIMB_HEIGHT, 0),
                BODY_WIDTH,
                BODY_HEIGHT,
                DEPTH);
        Matrix4f rightArm =
                new Matrix4f(torso)
                        .translate(5 * PIXEL, 22 * PIXEL, 0)
                        .rotateY(-pose.attack() * .3f)
                        .rotateZ(-pose.attack() * .2f)
                        .rotateX(pose.rightArm());
        if (drawingBow) {
            var bowPose = PracticeBowPose.aimingAvatar(player.pitch());
            bowArm(shader, torso, jerseyColor, 6 * PIXEL, bowPose.getTranslation(new Vector3f()));
            bowArm(shader, torso, jerseyColor, -6 * PIXEL, bowPose.transformPosition(new Vector3f(
                    0, PracticeBowPose.NOCK_Y, PracticeBowPose.stringZ(drawTicks))));
            bow(shader, new Matrix4f(torso).mul(bowPose), drawTicks);
        } else {
            arm(shader, new Matrix4f(torso).translate(-5 * PIXEL, 22 * PIXEL, 0).rotateX(pose.leftArm()),
                    -1, jerseyColor);
            arm(shader, rightArm, 1, jerseyColor);
        }
        shader.setVector3("uColor", .17f, .22f, .40f);
        leg(shader, root, -2 * PIXEL, seated ? -1.25f : pose.leftLeg());
        leg(shader, root, 2 * PIXEL, seated ? -1.25f : pose.rightLeg());
        if (hasBow) {
            if (!drawingBow) bow(shader, new Matrix4f(rightArm)
                    .translate(PIXEL, -9 * PIXEL, -.11f)
                    .rotateX(-pose.rightArm() - .22f).rotateY(-.55f).rotateZ(.20f).scale(.8f), 0);
            return;
        }
        held(
                player.heldItem(),
                player.heldColor(),
                Items.tool(player.heldItem())
                        ? HeldToolPose.world(player.heldItem(), rightArm)
                        : new Matrix4f(rightArm)
                        .translate(PIXEL, -9 * PIXEL, -2 * PIXEL)
                        .rotateX(-.35f)
                        .rotateY(.4f)
                        .scale(.35f)
                        .translate(-.5f, -.1f, -.5f),
                shader,
                models);
    }

    static Matrix4f headPose(Matrix4f facing, float pitch) {
        // The face points down local -Z. Positive camera pitch looks up.
        return new Matrix4f(facing).translate(0, PlayerSkin.HEAD_BASE, 0)
                .rotateX((float) Math.toRadians(pitch));
    }

    /** Scoped overlay tint for confirmed damage to a practice opponent. */
    public void renderPracticeAvatar(Protocol.Pose player, ShaderProgram shader, VoxelModelRenderer models,
                                     int jerseyColor, float hurt, boolean bow, float drawTicks) {
        shader.setFloat("uAvatarHurt", hurt);
        try {
            render(player, shader, models, jerseyColor, bow, drawTicks);
        } finally {
            shader.setFloat("uAvatarHurt", 0);
        }
    }

    public void renderFirstPerson(Player player, ShaderProgram shader, VoxelModelRenderer models) {
        renderFirstPerson(player.walkPhase(), player.walkAmount(), player.swingProgress(), player.placingSwing(),
                player.heldItem(), player.heldColor(), shader, models);
    }

    public void renderFirstPerson(float walkPhase, float walkAmount, float swing, boolean placing,
                                  int item, int color, ShaderProgram shader, VoxelModelRenderer models) {
        Matrix4f hand =
                firstPersonHand(walkPhase, walkAmount, swing, placing);
        // Keep tool and fist together, farther from the lens and inside narrow viewports.
        // Ordinary items retain their existing first-person presentation.
        boolean tool = Items.tool(item);
        if (tool) hand = HeldToolPose.firstPersonHand(hand);
        shader.setInt("uVertexColor", 0);
        shader.setInt("uInstanced", 0);
        skin(shader);
        box(shader, new Matrix4f(hand).translate(0, .25f, 0), LIMB_WIDTH, .5f, DEPTH);
        shirt(shader, -1);
        // The farther tool camera pose can expose the sleeve base at wide FOV.
        // Extend only its lower end off screen; the fist and grip stay unchanged.
        box(shader, tool ? new Matrix4f(hand).translate(0, -1.5f, 0) : hand,
                LIMB_WIDTH, tool ? 1.75f : .25f, DEPTH);
        held(
                item,
                color,
                tool
                        ? HeldToolPose.firstPerson(item, hand)
                        : new Matrix4f(hand)
                        .translate(-.14f, .78f, .18f)
                        .rotateY(.55f)
                        .rotateX(-.2f)
                        .scale(.32f)
                        .translate(-.5f, -.1f, -.5f),
                shader,
                models);
    }

    public void renderPracticeFirstPerson(float walkPhase, float walkAmount, float swing, float equipDrop,
                                          boolean blocking, boolean hasBow, float drawTicks,
                                          int item, int color, ShaderProgram shader,
                                          VoxelModelRenderer models) {
        shader.setInt("uVertexColor", 0);
        shader.setInt("uInstanced", 0);
        if (hasBow) {
            var grip = PracticeBowPose.firstPerson(walkPhase, walkAmount, equipDrop, drawTicks);
            firstPersonBowArm(shader, grip, .84f);
            if (drawTicks > 0) {
                var pullHand = new Matrix4f(grip).translate(.045f, PracticeBowPose.NOCK_Y - .035f,
                                PracticeBowPose.stringZ(drawTicks) + .08f)
                        .rotateZ(.34f).scale(.78f);
                firstPersonBowArm(shader, pullHand, .62f);
            }
            bow(shader, grip, drawTicks);
            return;
        }
        var pose = PracticeHandPose.sample(walkPhase, walkAmount, swing, equipDrop, blocking, item);
        skin(shader);
        box(shader, new Matrix4f(pose.hand()).translate(0, PracticeHandPose.PALM_BOTTOM, 0),
                PracticeHandPose.PALM_WIDTH, PracticeHandPose.PALM_TOP - PracticeHandPose.PALM_BOTTOM,
                PracticeHandPose.PALM_DEPTH);
        shirt(shader, -1);
        box(shader, new Matrix4f(pose.hand()).translate(0, PracticeHandPose.SLEEVE_BOTTOM, 0),
                PracticeHandPose.PALM_WIDTH, PracticeHandPose.PALM_BOTTOM - PracticeHandPose.SLEEVE_BOTTOM,
                PracticeHandPose.PALM_DEPTH);
        held(item, color, pose.item(), shader, models);
    }

    private void firstPersonBowArm(ShaderProgram shader, Matrix4f grip, float sleeveLength) {
        skin(shader);
        box(shader, new Matrix4f(grip).translate(0, -.115f, .035f), .153f, .18f, .145f);
        shirt(shader, -1);
        box(shader, new Matrix4f(grip).translate(0, -.115f - sleeveLength, .05f), .153f, sleeveLength, .145f);
    }

    private void bowArm(ShaderProgram shader, Matrix4f torso, int jersey, float shoulderX, Vector3f fist) {
        var toShoulder = new Vector3f(shoulderX, 22 * PIXEL, 0).sub(fist);
        float length = toShoulder.length();
        var arm = new Matrix4f(torso).translate(fist)
                .rotate(new Quaternionf().rotationTo(0, 1, 0,
                        toShoulder.x / length, toShoulder.y / length, toShoulder.z / length));
        skin(shader);
        box(shader, new Matrix4f(arm).translate(0, -.065f, 0), LIMB_WIDTH, length * .67f + .065f, DEPTH);
        shirt(shader, jersey);
        box(shader, new Matrix4f(arm).translate(0, length * .67f, 0), LIMB_WIDTH, length * .33f + .045f, DEPTH);
    }

    private void bow(ShaderProgram shader, Matrix4f grip, float drawTicks) {
        shader.setVector3("uColor", .30f, .16f, .12f);
        box(shader, new Matrix4f(grip).translate(0, -.098f, 0), .068f, .196f, .067f);
        float tipY = PracticeBowPose.tipY(drawTicks), tipZ = PracticeBowPose.tipZ(drawTicks);
        float pull = PracticeBowPose.fraction(drawTicks);
        float innerZ = -.02f + .02f * pull, outerZ = .024f + .04f * pull;
        for (int side : new int[]{-1, 1}) {
            shader.setVector3("uColor", .60f, .33f, .17f);
            segment(shader, grip, side * .078f, 0, side * .233f, innerZ, .055f, .060f);
            shader.setVector3("uColor", .70f, .42f, .23f);
            segment(shader, grip, side * .23f, innerZ, side * .344f, outerZ, .042f, .047f);
            shader.setVector3("uColor", .78f, .54f, .32f);
            segment(shader, grip, side * .341f, outerZ, side * tipY, tipZ, .030f, .038f);
            shader.setVector3("uColor", .80f, .85f, .78f);
            segment(shader, grip, side * tipY, tipZ, PracticeBowPose.NOCK_Y,
                    PracticeBowPose.stringZ(drawTicks), .007f, .007f);
        }
        if (drawTicks > 0) {
            var tip = new Matrix4f(grip).translate(0, PracticeBowPose.NOCK_Y,
                    PracticeBowPose.stringZ(drawTicks) - PracticeBowPose.ARROW_LENGTH);
            arrow(shader, tip, false);
        }
    }

    private void segment(ShaderProgram shader, Matrix4f frame, float startY, float startZ,
                         float endY, float endZ, float width, float depth) {
        var pose = new Matrix4f(frame).translate(0, startY, startZ)
                .rotateX((float) Math.atan2(endZ - startZ, endY - startY));
        box(shader, pose, width, (float) Math.hypot(endY - startY, endZ - startZ), depth);
    }

    public void renderPracticeArrow(float x, float y, float z, float vx, float vy, float vz,
                                    boolean critical, boolean embedded, ShaderProgram shader) {
        shader.setInt("uVertexColor", 0);
        shader.setInt("uInstanced", 0);
        arrow(shader, PracticeBowPose.arrow(x, y, z, vx, vy, vz), critical && !embedded);
    }

    private void arrow(ShaderProgram shader, Matrix4f tip, boolean critical) {
        shader.setVector3("uColor", .65f, .42f, .23f);
        box(shader, new Matrix4f(tip).translate(0, -.014f, .342f), .028f, .028f, .596f);
        shader.setVector3("uColor", critical ? 1 : .68f, critical ? .81f : .77f, critical ? .42f : .79f);
        box(shader, new Matrix4f(tip).translate(0, -.02f, .035f), .052f, .04f, .058f);
        shader.setVector3("uColor", .88f, .91f, .87f);
        box(shader, new Matrix4f(tip).translate(0, -.038f, .59f), .015f, .076f, .10f);
        box(shader, new Matrix4f(tip).translate(0, -.008f, .59f), .076f, .016f, .10f);
    }

    private void held(
            int type,
            int rgb,
            Matrix4f transform,
            ShaderProgram shader,
            VoxelModelRenderer models) {
        if (type == 0) return;
        if (Blocks.isModel(type) || Items.get(type) != null && Items.get(type).model() != null) models.renderHeld(type, transform, shader);
        else {
            shader.setInt("uVertexColor", 0);
            float[] color =
                    Blocks.material(type) == Blocks.LED
                            ? new float[] {
                                (rgb >>> 16 & 255) / 255f,
                                (rgb >>> 8 & 255) / 255f,
                                (rgb & 255) / 255f
                            }
                            : Blocks.color(type);
            shader.setVector3("uColor", color[0], color[1], color[2]);
            Matrix4f item = new Matrix4f(transform);
            if (Blocks.isPiece(type))
                item.translate(.5f, 0, .5f)
                        .scale(1f / (1 << Blocks.depth(type)))
                        .translate(-.5f, 0, -.5f);
            shader.setMatrix4("uModel", item);
            shader.setFloat("uModelEmission", Blocks.material(type) == Blocks.LED ? 4 : 0);
            cube.render();
            shader.setFloat("uModelEmission", 0);
        }
    }

    private void skin(ShaderProgram shader) {
        shader.setVector3("uColor", .88f, .68f, .46f);
    }

    private void shirt(ShaderProgram shader, int jerseyColor) {
        if (jerseyColor >= 0) shader.setVector3("uColor", (jerseyColor >> 16 & 255) / 255f,
                (jerseyColor >> 8 & 255) / 255f, (jerseyColor & 255) / 255f);
        else if (cohort == 0) shader.setVector3("uColor", .65f, .4f, .18f);
        else if (cohort == 1) shader.setVector3("uColor", .2f, .6f, .4f);
        else if (cohort == 2) shader.setVector3("uColor", .55f, .25f, .65f);
        else shader.setVector3("uColor", .16f, .46f, .86f);
    }

    private void arm(ShaderProgram shader, Matrix4f pivot, int side, int jerseyColor) {
        skin(shader);
        box(
                shader,
                new Matrix4f(pivot).translate(side * PIXEL, -10 * PIXEL, 0),
                LIMB_WIDTH,
                8 * PIXEL,
                DEPTH);
        shirt(shader, jerseyColor);
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
                Math.max(.1f, .25f / (1 << Blocks.depth(drop.type()))),
                Math.max(.1f, .25f / (1 << Blocks.depth(drop.type()))),
                Math.max(.1f, .25f / (1 << Blocks.depth(drop.type()))));
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
