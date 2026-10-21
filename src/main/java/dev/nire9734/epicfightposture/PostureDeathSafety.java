package dev.nire9734.epicfightposture;

/**
 * Pure, testable death guards for posture progression.
 *
 * A lethal hit must never be converted into a new neutralize animation after
 * Epic Fight starts the death animation. However, an already-running Combat
 * Evolution execution must keep its rendering state through the finishing blow.
 */
final class PostureDeathSafety {
    static boolean canReceivePosture(boolean alive, float health) {
        return alive && Float.isFinite(health) && health > 0.0F;
    }

    static boolean shouldForgetDeadState(
            boolean alive, float health, boolean combatEvolutionExecuting) {
        return !canReceivePosture(alive, health) && !combatEvolutionExecuting;
    }

    private PostureDeathSafety() {}
}
