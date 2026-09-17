package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.client.ClientPostureStore;
import dev.nire9734.epicfightposture.sync.RuntimeAccess;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.config.ClientConfig;
import yesman.epicfight.client.gui.EntityUI;
import yesman.epicfight.client.gui.HealthBar;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.effect.VisibleMobEffect;
import net.shelmarow.combat_evolution.ai.CEHumanoidPatch;

import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;

@Mixin(value = HealthBar.class, remap = false, priority = 900)
public abstract class BridgeHealthBarMixin extends EntityUI {
    @Unique
    private static final ResourceLocation EFPB$HEALTH_BAR =
            new ResourceLocation("combat_evolution", "textures/gui/bossbar/ce_health_bar.png");

    /**
     * Epic Fight's normal predicate hides an unhurt entity with no effects.
     * Let posture damage opt in only after preserving every early safety gate
     * from HealthBar.shouldDraw() in Epic Fight 20.14.17.
     */
    @Inject(method = "shouldDraw", at = @At("RETURN"), cancellable = true)
    private void efpi$showWhenPostureDamaged(
            LivingEntity entity,
            LivingEntityPatch<?> entityPatch,
            LocalPlayerPatch playerPatch,
            float partialTicks,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValueZ() || !efpi$hasDamagedPosture(entity)) {
            return;
        }

        if (ClientConfig.healthBarVisibility == ClientConfig.HealthBarVisibility.NONE
                || entity.isRemoved()) {
            return;
        }

        Player localPlayer = (Player) playerPatch.getOriginal();
        if (entity.isInvisibleTo(localPlayer) || entity == ((LocalPlayer) localPlayer).getVehicle()) {
            return;
        }

        if (Minecraft.getInstance().getCameraEntity() == null
                || entity.distanceToSqr(Minecraft.getInstance().getCameraEntity()) >= 400.0D) {
            return;
        }

        if (entity instanceof Player targetPlayer) {
            if (targetPlayer == localPlayer && playerPatch.getMaxStunShield() <= 0.0F) {
                return;
            }
            if (targetPlayer.isCreative() || targetPlayer.isSpectator()) {
                return;
            }
        }

        // TARGET and TARGET_AND_HURT are presentation modes, not safety gates.
        // Posture damage is an additional valid reason to render the HUD.
        cir.setReturnValue(true);
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void efpb$draw(
            LivingEntity entity,
            LivingEntityPatch<?> entityPatch,
            LocalPlayerPatch playerPatch,
            PoseStack poseStack,
            MultiBufferSource buffer,
            float partialTicks,
            CallbackInfo ci
    ) {
        // Combat Evolution owns its own CEHumanoidPatch HUD; do not replace it.
        if (entityPatch instanceof CEHumanoidPatch) {
            return;
        }

        int entityId = RuntimeAccess.entityId(entity);
        UUID uuid = RuntimeAccess.entityUuid(entity);
        ClientPostureStore.Entry posture = ClientPostureStore.get(entityId, uuid);
        if (posture == null) {
            return;
        }

        ci.cancel();

        Matrix4f matrix = this.getModelViewMatrixAlignedToCamera(
                poseStack,
                entity,
                0.0F,
                RuntimeAccess.bbHeight(entity) + 0.25F,
                0.0F,
                true,
                partialTicks
        );

        efpb$drawEffects(entity, playerPatch, matrix, buffer);

        float maxHealth = Math.max(RuntimeAccess.maxHealth(entity), 1.0E-4F);
        float healthPercent = efpb$clamp(RuntimeAccess.health(entity) / maxHealth);
        float posturePercent = posture.percent();
        float scale = 1.0F;

        int textureLeft = 3;
        int textureRight = 83;
        int textureWidth = textureRight - textureLeft;
        int healthLeft = 6;
        int healthRight = 80;
        int healthWidthPx = healthRight - healthLeft;

        float healthBarHeight = scale * 20.0F / textureWidth;
        float healthBarX = -scale / 2.0F;
        float healthFillWidth = scale * ((float) healthWidthPx / textureWidth) * healthPercent;
        float healthFillOffset = scale * ((float) (healthLeft - textureLeft) / textureWidth);
        float healthFillX = healthBarX + healthFillOffset;
        int healthFillEndU = (int) (healthLeft + healthWidthPx * healthPercent);

        EntityUI.drawUIAsLevelModel(
                matrix, EFPB$HEALTH_BAR, buffer,
                healthBarX, -healthBarHeight / 2.0F,
                healthBarX + scale, healthBarHeight / 2.0F,
                textureLeft, 0, textureRight, 20, 256
        );
        EntityUI.drawUIAsLevelModel(
                matrix, EFPB$HEALTH_BAR, buffer,
                healthFillX, -healthBarHeight / 2.0F,
                healthFillX + healthFillWidth, healthBarHeight / 2.0F,
                healthLeft, 21, healthFillEndU, 41, 256
        );

        int postureBgLeft = 6;
        int postureBgRight = 80;
        int postureBgWidthPx = postureBgRight - postureBgLeft;
        int postureFillLeft = 9;
        int postureFillRight = 77;
        int postureFillWidthPx = postureFillRight - postureFillLeft;

        float postureBarWidth = scale * ((float) postureBgWidthPx / textureWidth);
        float postureBarHeight = postureBarWidth * 20.0F / postureBgWidthPx;
        float postureBarX = -postureBarWidth / 2.0F;
        float postureFillWidth = postureBarWidth * ((float) postureFillWidthPx / postureBgWidthPx) * posturePercent;
        float postureFillOffset = postureBarWidth * ((float) (postureFillLeft - postureBgLeft) / postureBgWidthPx);
        float postureFillX = postureBarX + postureFillOffset;
        int postureFillEndU = (int) (postureFillLeft + postureFillWidthPx * posturePercent);

        EntityUI.drawUIAsLevelModel(
                matrix, EFPB$HEALTH_BAR, buffer,
                postureBarX, -postureBarHeight / 2.0F,
                postureBarX + postureBarWidth, postureBarHeight / 2.0F,
                postureBgLeft, 42, postureBgRight, 62, 256
        );

        if (!posture.broken) {
            EntityUI.drawUIAsLevelModel(
                    matrix, EFPB$HEALTH_BAR, buffer,
                    postureFillX, -postureBarHeight / 2.0F,
                    postureFillX + postureFillWidth, postureBarHeight / 2.0F,
                    postureFillLeft, 63, postureFillEndU, 83, 256
            );
        } else {
            float fullBrokenWidth = postureBarWidth * ((float) postureFillWidthPx / postureBgWidthPx);
            EntityUI.drawUIAsLevelModel(
                    matrix, EFPB$HEALTH_BAR, buffer,
                    postureFillX, -postureBarHeight / 2.0F,
                    postureFillX + fullBrokenWidth, postureBarHeight / 2.0F,
                    postureFillLeft, 84, postureFillRight, 104, 256
            );
        }
    }

    @Unique
    private static void efpb$drawEffects(
            LivingEntity entity,
            LocalPlayerPatch playerPatch,
            Matrix4f matrix,
            MultiBufferSource buffer
    ) {
        Collection<MobEffectInstance> effects = RuntimeAccess.activeEffects(entity);
        if (effects.isEmpty() || entity == RuntimeAccess.original(playerPatch)) {
            return;
        }

        Iterator<MobEffectInstance> iterator = effects.iterator();
        int count = effects.size();
        int columnsMinusOne = count > 1 ? 1 : 0;
        int rowsMinusOne = (count - 1) / 2;
        float baseX = -0.8F + -0.3F * columnsMinusOne;
        float baseY = -0.15F + 0.15F * rowsMinusOne;

        for (int row = 0; row <= rowsMinusOne; row++) {
            for (int column = 0; column <= columnsMinusOne; column++) {
                if (!iterator.hasNext()) {
                    return;
                }

                MobEffectInstance instance = iterator.next();
                MobEffect effect = RuntimeAccess.effect(instance);
                ResourceLocation icon = null;

                if (effect instanceof VisibleMobEffect visibleMobEffect) {
                    icon = visibleMobEffect.getIcon(instance);
                } else if (effect != null) {
                    ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(effect);
                    if (key != null) {
                        icon = new ResourceLocation(
                                key.getNamespace(),
                                "textures/mob_effect/" + key.getPath() + ".png"
                        );
                    }
                }

                float x = baseX + 0.3F * column;
                float y = baseY - 0.3F * row;
                if (icon != null) {
                    EntityUI.drawUIAsLevelModel(
                            matrix, icon, buffer,
                            x, y, x + 0.3F, y + 0.3F,
                            0, 0, 256, 256, 256
                    );
                }
            }
        }
    }

    @Unique
    private static boolean efpi$hasDamagedPosture(LivingEntity entity) {
        ClientPostureStore.Entry posture = ClientPostureStore.get(entity.getId(), entity.getUUID());
        return posture != null && (posture.current < posture.max || posture.broken);
    }

    @Unique
    private static float efpb$clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
