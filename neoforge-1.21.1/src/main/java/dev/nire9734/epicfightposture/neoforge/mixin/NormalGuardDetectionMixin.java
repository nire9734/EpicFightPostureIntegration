package dev.nire9734.epicfightposture.neoforge.mixin;

import dev.nire9734.epicfightposture.HookGuard;
import dev.nire9734.epicfightposture.NeoForgePostureServerEvents;
import dev.nire9734.epicfightposture.GuardPosturePolicy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import yesman.epicfight.api.event.types.entity.TakeDamageEvent;
import yesman.epicfight.registry.entries.EpicFightSkills;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.guard.GuardSkill;
import yesman.epicfight.skill.guard.ImpactGuardSkill;
import yesman.epicfight.skill.guard.ParryingSkill;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

/**
 * Paid normal blocks generate Posture pressure; parries and stamina guard-breaks do not.
 * Impact Guard delegates to GuardSkill.guard, so it is detected here as well.
 */
@Mixin(value = {GuardSkill.class, ParryingSkill.class}, remap = false)
public abstract class NormalGuardDetectionMixin {
    @ModifyVariable(method = "guard", at = @At("STORE"), ordinal = 0)
    private GuardSkill.BlockType efpi$onNormalGuard(
            GuardSkill.BlockType blockType, SkillContainer container,
            CapabilityItem itemCapability, ServerPlayerPatch playerPatch,
            TakeDamageEvent.Income event, float knockback, float impact, boolean advanced) {
        // With Guard, the default parrying skill defines weapon eligibility regardless
        // of which guard skill is equipped. Impact Guard cannot parry, so it skips
        // that check. Treat unknown capabilities conservatively.
        if (blockType != GuardSkill.BlockType.GUARD || event.isParried()) return blockType;
        boolean impactGuard = (Object) this instanceof ImpactGuardSkill;
        boolean canParry = false;
        if (!impactGuard) {
            try {
                ParryingSkill parrySkill = EpicFightSkills.PARRYING.get();
                if (parrySkill == null) return blockType;
                canParry = parrySkill.isHoldingWeaponAvailable(
                        playerPatch, itemCapability, GuardSkill.BlockType.ADVANCED_GUARD);
            } catch (RuntimeException | LinkageError ignored) {
                return blockType;
            }
        }
        if (!GuardPosturePolicy.shouldApply(true, event.isParried(), canParry, impactGuard)) return blockType;

        Entity attacker = GuardSkill.getOffender(event.getDamageSource());
        if (attacker instanceof LivingEntity mob) {
            HookGuard.run("normal guard", () -> NeoForgePostureServerEvents.onGuarded(mob, event.getDamageSource()));
        }
        return blockType;
    }
}
