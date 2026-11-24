package dev.nire9734.epicfightposture;

/**
 * Pure math for weapon-scaled execution and assassination ranges.
 *
 * Combat Evolution uses fixed ranges (4.0 blocks for guard-break executions,
 * 2.0 for assassinations). They are scaled here by the held weapon's reach,
 * taken from its Epic Fight collider, relative to a one-handed sword, so a sword
 * keeps Combat Evolution's ranges exactly and addon weapons are covered
 * automatically. The weapon factor is bounded, then the server-wide multiplier
 * (0.5-2.0) applies. When the feature is switched off, callers pass a
 * multiplier of 0.0, which leaves Combat Evolution's ranges unchanged.
 */
public final class ExecutionRangePolicy {
    /** Shoulder-to-hand reach added to the weapon collider, in blocks. */
    static final double ARM_REACH = 0.75D;
    /** Reach of Epic Fight's SWORD collider: |center| 0.35 + half length 0.7. */
    static final double SWORD_COLLIDER_REACH = 1.05D;
    static final double REFERENCE_REACH = ARM_REACH + SWORD_COLLIDER_REACH;
    /** Weapon factor bounds: 4.0 -> 3.0..6.0 blocks, 2.0 -> 1.5..3.0 blocks at multiplier 1.0. */
    static final double MIN_WEAPON_FACTOR = 0.75D;
    static final double MAX_WEAPON_FACTOR = 1.5D;
    /** Half width of a player-sized mob; Combat Evolution's ranges assume it. */
    static final double STANDARD_HALF_WIDTH = 0.3D;
    /** Cap on the large-mob allowance, in blocks. */
    static final double MAX_SIZE_BONUS = 3.0D;
    /**
     * Multiplier bounds. Below 0.5 bodies collide before an assassination range
     * is reached; above 2.0 Combat Evolution's teleport to the execution spot
     * becomes unnaturally long and it does not check line of sight.
     */
    public static final double MIN_MULTIPLIER = 0.5D;
    public static final double MAX_MULTIPLIER = 2.0D;

    /** Reach of an Epic Fight collider from the hand: distance to its center plus its longest half extent. */
    public static double colliderReach(double centerX, double centerY, double centerZ,
                                       double halfX, double halfY, double halfZ) {
        double center = Math.sqrt(centerX * centerX + centerY * centerY + centerZ * centerZ);
        double extent = Math.max(Math.abs(halfX), Math.max(Math.abs(halfY), Math.abs(halfZ)));
        double reach = center + extent;
        return Double.isFinite(reach) ? reach : Double.NaN;
    }

    /** Weapon factor relative to a sword; unknown reach keeps Combat Evolution's range. */
    public static double weaponFactor(double colliderReach) {
        if (!Double.isFinite(colliderReach) || colliderReach <= 0.0D) return 1.0D;
        double factor = (ARM_REACH + colliderReach) / REFERENCE_REACH;
        return Math.max(MIN_WEAPON_FACTOR, Math.min(MAX_WEAPON_FACTOR, factor));
    }

    public static boolean isEnabled(double multiplier) {
        return Double.isFinite(multiplier) && multiplier > 0.0D;
    }

    /** Scales one of Combat Evolution's ranges; disabled or invalid input returns it unchanged. */
    public static double range(double combatEvolutionRange, double multiplier, double colliderReach) {
        if (!isEnabled(multiplier) || !Double.isFinite(combatEvolutionRange) || combatEvolutionRange <= 0.0D) {
            return combatEvolutionRange;
        }
        double scaled = combatEvolutionRange * weaponFactor(colliderReach)
                * Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, multiplier));
        return Double.isFinite(scaled) && scaled > 0.0D ? scaled : combatEvolutionRange;
    }

    /**
     * Extra range for mobs wider than a player, because Combat Evolution measures
     * to the target's center: a large boss can be executed from its surface.
     */
    public static double sizeBonus(double targetWidth, double multiplier) {
        if (!isEnabled(multiplier) || !Double.isFinite(targetWidth)) return 0.0D;
        return Math.max(0.0D, Math.min(MAX_SIZE_BONUS, targetWidth / 2.0D - STANDARD_HALF_WIDTH));
    }

    private ExecutionRangePolicy() {
    }
}
