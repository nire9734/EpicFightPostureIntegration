package dev.nire9734.epicfightposture.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.client.renderer.shader.compute.ComputeShaderSetup;
import yesman.epicfight.client.renderer.shader.compute.loader.ComputeShaderProvider;

/**
 * Keeps EFPI-generated runtime meshes on Epic Fight's CPU skinning path.
 *
 * <p>Epic Fight allocates a ComputeShaderSetup from the SkinnedMesh constructor
 * whenever the GPU supports compute shaders, even when the client option is
 * currently disabled. EFPI creates many transient/runtime meshes while probing
 * compatibility, so those setups are both unnecessary for this diagnostic build
 * and a useful isolation point for the AMD/OpenGL stutter investigation.</p>
 *
 * <p>The guard is deliberately scoped to mesh construction initiated from
 * EFPI's humanoid client compatibility package. Native Epic Fight meshes and
 * meshes created by other mods keep their normal ComputeShaderSetup.</p>
 */
@Mixin(value = ComputeShaderProvider.class, remap = false, priority = 900)
public abstract class ComputeShaderProviderRuntimeMeshMixin {
    @Unique
    private static final StackWalker EFPI$STACK_WALKER = StackWalker.getInstance();

    @Unique
    private static final String EFPI$RUNTIME_MESH_PACKAGE =
            "dev.nire9734.epicfightposture.compat.humanoid.client.";

    @Inject(method = "getComputeShaderSetup", at = @At("HEAD"), cancellable = true)
    private static void efpi$suppressRuntimeMeshComputeSetup(
            SkinnedMesh mesh,
            CallbackInfoReturnable<ComputeShaderSetup> cir) {
        if (mesh != null && efpi$isRuntimeMeshBuild()) {
            cir.setReturnValue(null);
        }
    }

    @Unique
    private static boolean efpi$isRuntimeMeshBuild() {
        return EFPI$STACK_WALKER.walk(frames -> frames
                .map(StackWalker.StackFrame::getClassName)
                .anyMatch(className -> className.startsWith(EFPI$RUNTIME_MESH_PACKAGE)));
    }
}
