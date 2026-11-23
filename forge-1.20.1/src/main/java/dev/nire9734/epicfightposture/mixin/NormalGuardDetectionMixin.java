package dev.nire9734.epicfightposture.mixin;

import dev.nire9734.epicfightposture.HookGuard;
import dev.nire9734.epicfightposture.PostureManager;
import dev.nire9734.epicfightposture.GuardPosturePolicy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import yesman.epicfight.gameasset.EpicFightSkills;
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
        // Check the registered parrying skill, not the equipped guard skill:
        // a sword must not receive guard pressure merely by switching to basic Guard.
        // Unknown or failed capability probes fail closed (no guard pressure).
        if (blockType != GuardSkill.BlockType.GUARD || event.isParried()) return blockType;
        if (!(EpicFightSkills.PARRYING instanceof ParryingSkill parrySkill)) return blockType;
        final boolean canParry;
        try {
            canParry = parrySkill.isHoldingWeaponAvailable(
                    event.getPlayerPatch(), itemCapability, GuardSkill.BlockType.ADVANCED_GUARD);
        } catch (RuntimeException | LinkageError ignored) {
            return blockType;
        }
        if (!GuardPosturePolicy.shouldApply(true, event.isParried(), canParry)) return blockType;

        Entity attacker = GuardSkill.getOffender(event.getDamageSource());
        if (attacker instanceof LivingEntity mob) {
            HookGuard.run("normal guard", () -> PostureManager.onGuarded(mob, event.getDamageSource()));
        }
        return blockType;
    }
}
