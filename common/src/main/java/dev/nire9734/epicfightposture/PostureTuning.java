package dev.nire9734.epicfightposture;

/**
 * Fixed balance and timing constants shared by the Forge and NeoForge server
 * runtimes. Keeping them in one place prevents the two loaders from drifting
 * apart; changing a value here changes gameplay on both loaders.
 */
public final class PostureTuning {
    /** Maximum HP of the reference mob used to normalize effective health. */
    public static final float EHP_REFERENCE_HEALTH = 20.0F;
    /** Reference incoming hit used when converting armor and toughness into EHP. */
    public static final float EHP_REFERENCE_DAMAGE = 8.0F;
    /** Maximum Posture of a mob whose effective health equals the reference. */
    public static final float EHP_REFERENCE_POSTURE = 3.2F;
    /**
     * Sub-linear EHP exponent for maximum Posture. Kept as a double because
     * Math.pow uses double precision; the parry curve uses its float value.
     */
    public static final double EHP_EXPONENT = 0.7D;
    /** Lower bound applied to the EHP ratio before exponentiation. */
    public static final float MIN_EHP_RATIO = 0.01F;

    /** Floor applied to maximum Posture after EHP scaling. */
    public static final float MINIMUM_POSTURE = 1.0F;
    /** Absolute numeric bounds for any posture pool. */
    public static final float MIN_SAFE_POSTURE = 0.1F;
    public static final float MAX_SAFE_POSTURE = 1_000_000.0F;

    /** Base linear HP-loss coefficient for direct hits, before config multipliers. */
    public static final float BASE_ATTACK_POSTURE_COEFFICIENT = 0.75F;

    /** Duration of the post-break or post-execution refill animation. */
    public static final int BREAK_RECOVERY_TICKS = 60;
    /** Maximum Posture inputs are re-read at most once per this many ticks. */
    public static final int MAX_POSTURE_REFRESH_INTERVAL_TICKS = 10;
    /** Periodic posture synchronization interval for changed states. */
    public static final int SYNC_INTERVAL_TICKS = 4;
    /** Ignore maximum-Posture changes smaller than this. */
    public static final float MAX_POSTURE_CHANGE_EPSILON = 0.01F;

    private PostureTuning() {
    }
}
