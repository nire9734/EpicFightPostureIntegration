package dev.nire9734.epicfightposture.neoforge.mixin;

import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.1: renderer map is private; inspect it without changing ownership. */
@Mixin(EntityRenderDispatcher.class)
public interface NeoForgeEntityRenderDispatcherAccessor {
    @Accessor("renderers")
    Map<EntityType<?>, EntityRenderer<?>> efpi$getRenderers();
}
