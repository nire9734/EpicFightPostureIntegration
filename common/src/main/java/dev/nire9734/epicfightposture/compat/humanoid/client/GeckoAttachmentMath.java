package dev.nire9734.epicfightposture.compat.humanoid.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;

/** Final skeleton-to-Gecko attachment placement, identical on both loaders. */
public final class GeckoAttachmentMath {
    public static Matrix4f attachmentMatrix(Armature armature, OpenMatrix4f[] matrices,
                                             int jointId, Matrix4f geoBind) {
        if (jointId < 0 || jointId >= matrices.length || matrices[jointId] == null) {
            return geoBind;
        }
        Joint joint = armature.searchJointById(jointId);
        if (joint == null) return geoBind;
        OpenMatrix4f skin = new OpenMatrix4f(matrices[jointId]);
        skin.mulBack(joint.getToOrigin());
        return new Matrix4f(OpenMatrix4f.exportToMojangMatrix(skin)).mul(geoBind);
    }

    public static float[] quaternionToZyxEuler(Quaternionf source) {
        Quaternionf q = new Quaternionf(source).normalize();
        float x = q.x, y = q.y, z = q.z, w = q.w;
        float sinXCosY = 2.0F * (w * x + y * z);
        float cosXCosY = 1.0F - 2.0F * (x * x + y * y);
        float rotX = (float) Math.atan2(sinXCosY, cosXCosY);
        float sinY = 2.0F * (w * y - z * x);
        float rotY = Math.abs(sinY) >= 1.0F
                ? (float) Math.copySign(Math.PI / 2.0, sinY) : (float) Math.asin(sinY);
        float sinZCosY = 2.0F * (w * z + x * y);
        float cosZCosY = 1.0F - 2.0F * (y * y + z * z);
        float rotZ = (float) Math.atan2(sinZCosY, cosZCosY);
        return new float[]{rotX, rotY, rotZ};
    }

    private GeckoAttachmentMath() { }
}
