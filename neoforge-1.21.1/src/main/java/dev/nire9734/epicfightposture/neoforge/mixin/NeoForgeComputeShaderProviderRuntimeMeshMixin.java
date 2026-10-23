package dev.nire9734.epicfightposture.neoforge.mixin;

import dev.nire9734.epicfightposture.compat.humanoid.client.RuntimeMeshOwnership;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.client.renderer.shader.compute.loader.ComputeShaderProvider;

/**
 * Preserve Forge 3.0.2's CPU skinning contract for dynamically baked EFPI meshes.
 *
 * <p>SkinnedMesh queries this flag in its constructor, before allocating a
 * compute-shader setup or scheduling the allocation on the render thread.
 * Only construction inside an explicit EFPI ownership scope is intercepted;
 * Epic Fight's own meshes and other mods' meshes are unchanged.</p>
 */
@Mixin(value = ComputeShaderProvider.class, remap = false, priority = 900)
public abstract class NeoForgeComputeShaderProviderRuntimeMeshMixin {
    @Inject(method = "supportComputeShader", at = @At("HEAD"), cancellable = true)
    private static void efpi$disableComputeForOwnedRuntimeMesh(
            CallbackInfoReturnable<Boolean> cir) {
        if (RuntimeMeshOwnership.isOwnedConstruction()) {
            cir.setReturnValue(false);
        }
    }
}
