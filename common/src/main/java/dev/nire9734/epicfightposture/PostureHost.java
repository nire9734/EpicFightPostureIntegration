package dev.nire9734.epicfightposture;

/**
 * Loader-specific operations used by {@link PostureEngine}. Each loader
 * implements this once with its own Minecraft, Epic Fight and Combat Evolution
 * APIs; the engine owns every state transition and timing rule.
 *
 * @param <E> the loader's living entity type
 */
public interface PostureHost<E> {
    /** True only on the logical server. */
    boolean isServerEntity(E entity);

    boolean isAlive(E entity);

    float health(E entity);

    int tickCount(E entity);

    int entityId(E entity);

    boolean isBlacklisted(E entity);

    /** Full Posture eligibility: not a player, alive, not blacklisted, supported patch. */
    boolean isEligible(E entity);

    /** Whether Combat Evolution is currently executing this entity. */
    boolean isExecuting(E entity);

    PostureInputs liveInputs(E entity);

    /** Effective health from the entity type's registered base attributes. */
    float innateEffectiveHealth(E entity);

    void stopUsingItem(E entity);

    /** Applies Epic Fight's NEUTRALIZE stun and reports whether it is now playing. */
    NeutralizeResult applyNeutralize(E entity);

    /** Whether the NEUTRALIZE animation is still the entity's current animation. */
    boolean isNeutralizeActive(E entity);

    /** Sends the entity's current snapshot to tracking clients if it changed. */
    void syncNow(E entity);

    /** Tells tracking clients that the entity no longer has Posture. */
    void syncRemove(E entity);

    void observeExecution(E entity, String stage);

    void observeGuardBreak(E entity, boolean active, String failureReason);

    /** Global server settings, read once per event. */
    PostureSettings settings();

    /** Direct-hit coefficient for this entity, including its config multiplier. */
    float attackCoefficient(E entity);

    /** Parry multiplier for this entity (an entity override replaces the global one). */
    float parryMultiplier(E entity);

    /** Outcome of a guard-break attempt; {@code failureReason} is "none" when active. */
    record NeutralizeResult(boolean active, String failureReason) {
        public static NeutralizeResult success() {
            return new NeutralizeResult(true, "none");
        }

        public static NeutralizeResult failure(String reason) {
            return new NeutralizeResult(false, reason == null ? "UNKNOWN" : reason);
        }
    }
}
