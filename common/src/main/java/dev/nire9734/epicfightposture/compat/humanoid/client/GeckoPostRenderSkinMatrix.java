package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import org.joml.Matrix4f;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;

/** Exact Epic Fight chest skinning matrix followed by the source Gecko bind. */
public final class GeckoPostRenderSkinMatrix {
    public static Matrix4f compose(Armature armature, Matrix4f localBind) {
        if (armature == null || localBind == null) return null;
        OpenMatrix4f[] poseMatrices = armature.getPoseMatrices();
        int chest = HumanoidSkinWeightResolver.CHEST;
        if (poseMatrices == null || chest < 0 || chest >= poseMatrices.length
                || poseMatrices[chest] == null) return null;
        Joint joint = armature.searchJointById(chest);
        if (joint == null) return null;

        OpenMatrix4f skin = new OpenMatrix4f(poseMatrices[chest]);
        skin.mulBack(joint.getToOrigin());
        return new Matrix4f(OpenMatrix4f.exportToMojangMatrix(skin)).mul(localBind);
    }

    private GeckoPostRenderSkinMatrix() { }
}
