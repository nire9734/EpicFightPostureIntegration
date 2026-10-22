package dev.nire9734.epicfightposture.animation;

import net.shelmarow.combat_evolution.gameassets.animation.ExecutionHitAnimation;
import org.joml.Quaternionf;
import yesman.epicfight.api.animation.AnimationClip;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.JointTransform;
import yesman.epicfight.api.animation.Keyframe;
import yesman.epicfight.api.animation.TransformSheet;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.Vec3f;

/**
 * Original target-side execution animation for Epic Fight's standard Spider armature.
 *
 * <p>The clip is generated in code instead of copying Epic Fight assets. Any target
 * that actually uses the standard {@code SpiderArmature} can share this animation,
 * regardless of its entity ID or whether its patch comes from Epic Fight or another
 * compatibility mod.</p>
 */
public final class ProceduralSpiderExecutionAnimation extends ExecutionHitAnimation {
    private static final float[] TIMES = {
            0.00F, 0.55F, 0.78F, 1.05F, 2.80F, 3.16F, 3.55F, 5.00F
    };

    public ProceduralSpiderExecutionAnimation(
            float transitionTime,
            AnimationManager.AnimationAccessor<? extends ExecutionHitAnimation> accessor,
            AssetAccessor<? extends Armature> armature
    ) {
        super(transitionTime, accessor, armature);
    }

    @Override
    public void loadAnimation() {
        AnimationClip clip = new AnimationClip();

        clip.addJointTransform("Root", sheet(
                pose(0, 0, 0, 0.00F, 0.00F, 0.00F),
                pose(0, 0, 0, 0.00F, -0.01F, 0.00F),
                pose(0, 0, 0, 0.00F, -0.05F, 0.02F),
                pose(0, 0, 0, 0.00F, -0.02F, 0.00F),
                pose(0, 0, 0, 0.00F, -0.03F, 0.00F),
                pose(0, 0, 0, 0.00F, -0.10F, 0.04F),
                pose(0, 0, 0, 0.00F, -0.18F, 0.08F),
                pose(0, 0, 0, 0.00F, -0.18F, 0.08F)
        ));

        clip.addJointTransform("Stomach", sheet(
                pose(0, 0, 0),
                pose(-8, 0, 0),
                pose(24, 0, 0),
                pose(10, 0, 0),
                pose(-6, 0, 0),
                pose(32, 0, 0),
                pose(58, 0, 0),
                pose(58, 0, 0)
        ));
        clip.addJointTransform("Head", sheet(
                pose(0, 0, 0),
                pose(8, 0, 0),
                pose(-20, 0, 0),
                pose(-7, 0, 0),
                pose(6, 0, 0),
                pose(-26, 0, 0),
                pose(-42, 0, 0),
                pose(-42, 0, 0)
        ));

        addLeg(clip, "LL1", -8, 18, -28, -14, -10, -34, -58, -58,  8, -12,  20,  10,  8,  24,  38,  38);
        addLeg(clip, "RL1", -8, 18, -28, -14, -10, -34, -58, -58, -8,  12, -20, -10, -8, -24, -38, -38);
        addLeg(clip, "LL2", -5, 14, -24, -10,  -8, -28, -52, -52, 12, -10,  18,   8,  6,  20,  34,  34);
        addLeg(clip, "RL2", -5, 14, -24, -10,  -8, -28, -52, -52,-12,  10, -18,  -8, -6, -20, -34, -34);
        addLeg(clip, "LL3",  4, -10, 18, 8,  7, 24, 46, 46,  10, -10, 16, 7, 5, 18, 30, 30);
        addLeg(clip, "RL3",  4, -10, 18, 8,  7, 24, 46, 46, -10,  10,-16,-7,-5,-18,-30,-30);
        addLeg(clip, "LL4",  6, -14, 22, 10, 8, 28, 50, 50,   8, -12, 18, 8, 6, 20, 34, 34);
        addLeg(clip, "RL4",  6, -14, 22, 10, 8, 28, 50, 50,  -8,  12,-18,-8,-6,-20,-34,-34);

        clip.setClipTime(5.0F);
        clip.bakeKeyframes();
        this.animationClip = clip;
    }

    private static void addLeg(
            AnimationClip clip,
            String joint,
            float x0, float x1, float x2, float x3, float x4, float x5, float x6, float x7,
            float z0, float z1, float z2, float z3, float z4, float z5, float z6, float z7
    ) {
        clip.addJointTransform(joint, sheet(
                pose(x0, 0, z0), pose(x1, 0, z1), pose(x2, 0, z2), pose(x3, 0, z3),
                pose(x4, 0, z4), pose(x5, 0, z5), pose(x6, 0, z6), pose(x7, 0, z7)
        ));
    }

    private static TransformSheet sheet(JointTransform... transforms) {
        if (transforms.length != TIMES.length) {
            throw new IllegalArgumentException("Spider execution keyframe count mismatch");
        }
        Keyframe[] frames = new Keyframe[TIMES.length];
        for (int i = 0; i < TIMES.length; i++) {
            frames[i] = new Keyframe(TIMES[i], transforms[i]);
        }
        return new TransformSheet(frames);
    }

    private static JointTransform pose(float xDeg, float yDeg, float zDeg) {
        return pose(xDeg, yDeg, zDeg, 0.0F, 0.0F, 0.0F);
    }

    private static JointTransform pose(
            float xDeg, float yDeg, float zDeg,
            float x, float y, float z
    ) {
        float toRad = (float) (Math.PI / 180.0D);
        Quaternionf rotation = new Quaternionf().rotationXYZ(xDeg * toRad, yDeg * toRad, zDeg * toRad);
        return JointTransform.translationRotation(new Vec3f(x, y, z), rotation);
    }
}
