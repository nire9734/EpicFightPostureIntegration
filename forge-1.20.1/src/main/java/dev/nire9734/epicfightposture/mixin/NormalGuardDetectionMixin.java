package dev.nire9734.epicfightposture.mixin;

import dev.nire9734.epicfightposture.PostureManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.guard.GuardSkill;
import yesman.epicfight.skill.guard.ParryingSkill;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.entity.eventlistener.TakeDamageEvent;

/**
 * Both Epic Fight guard implementations store a BlockType after spending stamina.
 * GUARD means a paid normal block; ADVANCED_GUARD is a parry and GUARD_BREAK
 * means the defender ran out of stamina. Never award Posture in the latter cases.
 */
@Mixin(value = {GuardSkill.class, ParryingSkill.class}, remap = false)
public abstract class NormalGuardDetectionMixin {
    @ModifyVariable(method = "guard", at = @At("STORE"), ordinal = 0)
    private GuardSkill.BlockType efpi$onNormalGuard(
            GuardSkill.BlockType blockType, SkillContainer container,
            CapabilityItem itemCapability, TakeDamageEvent.Attack event,
            float knockback, float impact, boolean advanced) {
        if (blockType != GuardSkill.BlockType.GUARD || event.isParried()) {
            return blockType;
        }
        Entity attacker = GuardSkill.getOffender(event.getDamageSource());
        if (attacker instanceof LivingEntity mob) {
            PostureManager.onGuarded(mob);
        }
        return blockType;
    }
}
