package dev.nire9734.epicfightposture.mixin.client;

import dev.nire9734.epicfightposture.compat.humanoid.client.RuntimeMeshOwnership;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.client.renderer.shader.compute.loader.ComputeShaderProvider;

/**
 * Keeps explicitly EFPI-owned runtime meshes on Epic Fight's CPU skinning path.
 *
 * <p>Epic Fight checks compute support from inside the SkinnedMesh constructor
 * before it either creates a ComputeShaderSetup immediately or queues that work
 * onto the render thread. EFPI wraps only its own runtime mesh bake calls in an
 * explicit ownership scope, so returning false here prevents the setup request
 * from being created at all. Native Epic Fight meshes and meshes created by other
 * mods keep their normal compute-shader behavior.</p>
 */
@Mixin(value = ComputeShaderProvider.class, remap = false, priority = 900)
public abstract class ComputeShaderProviderRuntimeMeshMixin {
    @Inject(method = "supportComputeShader", at = @At("HEAD"), cancellable = true)
    private static void efpi$disableComputeForOwnedRuntimeMesh(
            CallbackInfoReturnable<Boolean> cir) {
        if (RuntimeMeshOwnership.isOwnedConstruction()) {
            cir.setReturnValue(false);
        }
    }
}
