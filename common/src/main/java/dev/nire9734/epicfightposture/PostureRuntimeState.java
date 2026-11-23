package dev.nire9734.epicfightposture;

/**
 * Platform-free mutable posture state shared by Forge's PostureManager and the
 * NeoForge server events adapter. Loader-specific entity access stays outside.
 */
final class PostureRuntimeState {
    // Combat Evolution may start its execution task a few ticks after the start event.
    static final int EXECUTION_START_GRACE_TICKS = 5;

    float max;
    float current;
    int brokenTicks;
    int regenDelay;
    int recoveryTicksRemaining;
    int completedBreaks;
    PostureState postureState = PostureState.NORMAL;

    /** Inputs of the last maximum-Posture evaluation; null when created from a raw value. */
    PostureInputs lastInputs;

    PostureRuntimeState(float max) {
        this.max = max;
        this.current = max;
    }

    /** Full-Posture state whose maximum is derived from, and remembers, these inputs. */
    PostureRuntimeState(PostureInputs inputs) {
        this(inputs.maximumPosture());
        this.lastInputs = inputs;
    }

    /**
     * Re-evaluates maximum Posture only when its inputs changed, and applies
     * the result only when it moved by a meaningful amount.
     */
    void refreshMaximum(PostureInputs inputs) {
        if (inputs.equals(lastInputs)) return;
        lastInputs = inputs;
        float newMax = inputs.maximumPosture();
        if (Math.abs(newMax - max) < PostureTuning.MAX_POSTURE_CHANGE_EPSILON) return;
        updateMaximum(newMax);
    }

    boolean isLocked() {
        return postureState == PostureState.BROKEN || postureState == PostureState.EXECUTING;
    }

    /** Recovery must finish before another break can be triggered. */
    boolean acceptsPostureDamage() {
        return postureState == PostureState.NORMAL;
    }

    boolean isBrokenForHud() {
        return isLocked();
    }

    /** Called for an attack or parry; only actual posture loss restarts the regen delay. */
    boolean applyDamage(float damage, float resistance, int delayTicks) {
        float reduced = PostureBalance.reducedPosture(current, damage, resistance);
        if (!(reduced < current)) return false;
        current = reduced;
        regenDelay = delayTicks;
        return current <= 0.0F;
    }

    void prepareBreak(int delayTicks) {
        current = 0.0F;
        regenDelay = delayTicks;
    }

    /** Count only confirmed guard breaks. */
    void confirmBreak() {
        if (completedBreaks < Integer.MAX_VALUE) completedBreaks++;
        brokenTicks = 0;
        postureState = PostureState.BROKEN;
    }

    void enterExecution() {
        postureState = PostureState.EXECUTING;
        current = 0.0F;
    }

    void startExecution() {
        enterExecution();
        regenDelay = 0;
        recoveryTicksRemaining = 0;
    }

    static boolean waitingForExecutionTask(int currentTick, Integer activationDeadline) {
        return activationDeadline != null && currentTick <= activationDeadline;
    }

    void beginRecovery(int totalTicks) {
        postureState = PostureState.RECOVERING;
        current = 0.0F;
        recoveryTicksRemaining = totalTicks;
        regenDelay = 0;
    }

    /** True on the final recovery tick, when the server must synchronize. */
    boolean advanceRecovery(int totalTicks) {
        recoveryTicksRemaining--;
        if (recoveryTicksRemaining <= 0) {
            recoveryTicksRemaining = 0;
            current = max;
            postureState = PostureState.NORMAL;
            return true;
        }
        current = max * (totalTicks - recoveryTicksRemaining) / totalTicks;
        return false;
    }

    void advanceRegeneration(float fractionPerSecond) {
        if (current >= max) return;
        if (regenDelay > 0) {
            regenDelay--;
            return;
        }
        float perTick = max * fractionPerSecond / 20.0F;
        current = Math.min(max, current + perTick);
    }

    void updateMaximum(float newMax) {
        float ratio = max > 0.0F ? Math.max(0.0F, Math.min(1.0F, current / max)) : 1.0F;
        max = newMax;
        if (!isLocked()) current = newMax * ratio;
    }
}

enum PostureState {
    NORMAL,
    BROKEN,
    EXECUTING,
    RECOVERING
}
