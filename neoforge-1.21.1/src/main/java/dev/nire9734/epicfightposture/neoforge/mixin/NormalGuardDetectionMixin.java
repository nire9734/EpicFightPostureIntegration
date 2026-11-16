package dev.nire9734.epicfightposture.neoforge.mixin;

import dev.nire9734.epicfightposture.NeoForgePostureServerEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import yesman.epicfight.api.event.types.entity.TakeDamageEvent;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.guard.GuardSkill;
import yesman.epicfight.skill.guard.ParryingSkill;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

/** Paid normal blocks generate Posture pressure; parries and stamina guard-breaks do not. */
@Mixin(value = {GuardSkill.class, ParryingSkill.class}, remap = false)
public abstract class NormalGuardDetectionMixin {
    @ModifyVariable(method = "guard", at = @At("STORE"), ordinal = 0)
    private GuardSkill.BlockType efpi$onNormalGuard(
            GuardSkill.BlockType blockType, SkillContainer container,
            CapabilityItem itemCapability, ServerPlayerPatch playerPatch,
            TakeDamageEvent.Income event, float knockback, float impact, boolean advanced) {
        if (blockType != GuardSkill.BlockType.GUARD || event.isParried()) {
            return blockType;
        }
        Entity attacker = GuardSkill.getOffender(event.getDamageSource());
        if (attacker instanceof LivingEntity mob) {
            NeoForgePostureServerEvents.onGuarded(mob);
        }
        return blockType;
    }
}
