package dev.nire9734.epicfightposture;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-authoritative Posture flow shared by Forge and NeoForge: when Posture
 * is reduced, when a guard break is attempted, and how states move through
 * NORMAL, BROKEN, EXECUTING and RECOVERING. Each loader supplies a
 * {@link PostureHost} for its own APIs and keeps only the way it detects
 * damage (Forge measures the health change on the next tick; NeoForge uses its
 * post-damage event) before calling {@link #applyAttack}.
 *
 * Runs on the logical server thread only, like the entity tick it serves.
 *
 * @param <E> the loader's living entity type
 */
public final class PostureEngine<E> {
    private final PostureHost<E> host;
    private final Map<E, PostureRuntimeState> states = new WeakHashMap<>();
    /** Server tick by which Combat Evolution must have started its execution task. */
    private final Map<E, Integer> pendingExecutionStart = new WeakHashMap<>();

    public PostureEngine(PostureHost<E> host) {
        this.host = host;
    }

    /** Snapshot sent to clients. */
    public record SyncState(float current, float max, boolean broken) {
    }

    // ---- damage ----------------------------------------------------------

    /**
     * Whether a pending hit on this entity can still reduce Posture. Creates
     * the state if needed, as a hit is about to be measured.
     */
    public boolean acceptsAttack(E entity) {
        return entity != null && host.isServerEntity(entity) && canReceive(entity)
                && host.isEligible(entity) && state(entity).acceptsPostureDamage();
    }

    /** Direct-hit pressure from HP actually lost. */
    public void applyAttack(E entity, float healthBefore, float maxHealth, float actualLoss, float impact) {
        if (entity == null || !host.isServerEntity(entity) || !canReceive(entity)
                || !host.isEligible(entity)) {
            return;
        }
        float damage = PostureBalance.attackPostureDamage(
                host.liveInputs(entity).basePosture(), maxHealth, healthBefore, actualLoss, impact,
                host.attackCoefficient(entity));
        damagePosture(entity, damage, host.settings());
    }

    /** A successful parry against this attacker. */
    public void onParried(E attacker, boolean projectile) {
        PostureSettings settings = host.settings();
        applyDefense(attacker, GuardPosturePolicy.defenseMultiplier(
                1.0F, projectile, settings.projectileDefenseMultiplier()), settings);
    }

    /** A paid normal guard (or Impact Guard) against this attacker. */
    public void onGuarded(E attacker, boolean projectile) {
        PostureSettings settings = host.settings();
        if (!settings.guardPostureEnabled()) return;
        applyDefense(attacker, GuardPosturePolicy.defenseMultiplier(
                settings.guardPostureMultiplier(), projectile, settings.projectileDefenseMultiplier()), settings);
    }

    private void applyDefense(E attacker, float multiplier, PostureSettings settings) {
        if (!Float.isFinite(multiplier) || multiplier <= 0.0F) return;
        if (attacker == null || !host.isServerEntity(attacker) || !canReceive(attacker)
                || !host.isEligible(attacker)) {
            return;
        }
        PostureRuntimeState state = state(attacker);
        if (!state.acceptsPostureDamage()) return;

        // Type-level base attributes keep parry percentages stable across level
        // mods that rewrite a mob's attribute instances.
        float damage = PostureBalance.parryDamage(
                state.max,
                settings.parryBaseMaxFraction(),
                settings.parryBaseMinFraction(),
                host.innateEffectiveHealth(attacker),
                PostureTuning.EHP_REFERENCE_HEALTH,
                (float) PostureTuning.EHP_EXPONENT);
        damage *= host.parryMultiplier(attacker);
        damage = PostureBalance.guardDamage(damage, multiplier);
        reduce(attacker, state, damage, settings);
    }

    private void damagePosture(E entity, float damage, PostureSettings settings) {
        if (!canReceive(entity) || !Float.isFinite(damage) || damage <= 0.0F) return;
        PostureRuntimeState state = state(entity);
        if (!state.acceptsPostureDamage()) return;
        reduce(entity, state, damage, settings);
    }

    private void reduce(E entity, PostureRuntimeState state, float damage, PostureSettings settings) {
        float resistance = PostureBalance.breakResistanceMultiplier(
                state.completedBreaks, settings.breakResistanceStep(), settings.breakResistanceMax());
        float previous = state.current;
        boolean broken = state.applyDamage(damage, resistance, settings.regenDelayTicks());
        if (!(state.current < previous)) return;
        if (broken) breakPosture(entity, state, settings);
        host.syncNow(entity);
    }

    private void breakPosture(E entity, PostureRuntimeState state, PostureSettings settings) {
        if (!canReceive(entity) || !state.acceptsPostureDamage()) return;
        state.prepareBreak(settings.regenDelayTicks());
        host.stopUsingItem(entity);

        PostureHost.NeutralizeResult result = host.applyNeutralize(entity);
        boolean active = result != null && result.active();
        host.observeGuardBreak(entity, active,
                active ? "none" : result == null ? "UNKNOWN" : result.failureReason());
        if (active) {
            // Combat Evolution checks the NEUTRALIZE animation identity.
            state.confirmBreak();
        } else {
            // A rejected stun does not justify dropping Posture tracking.
            state.beginRecovery(PostureTuning.BREAK_RECOVERY_TICKS);
        }
    }

    // ---- executions ------------------------------------------------------

    /** Combat Evolution started an execution or assassination on this entity. */
    public void onExecutionStart(E target) {
        if (target == null || !host.isServerEntity(target) || !canReceive(target)
                || !host.isEligible(target)) {
            return;
        }
        // An assassination can start before the target ever took Posture damage.
        PostureRuntimeState state = state(target);
        host.stopUsingItem(target);
        state.startExecution();
        host.observeExecution(target, "STARTED");
        pendingExecutionStart.put(target,
                host.tickCount(target) + PostureRuntimeState.EXECUTION_START_GRACE_TICKS);
        host.syncNow(target);
    }

    // ---- tick ------------------------------------------------------------

    /**
     * Advances one server tick. Returns true while the entity still has a
     * Posture state, so the caller can run its periodic synchronization.
     */
    public boolean tick(E entity) {
        if (entity == null || !host.isServerEntity(entity)) return false;
        if (!canReceive(entity)) {
            // A Combat Evolution execution may outlive the killing blow; keep
            // its state for rendering until the execution ends.
            if (PostureDeathSafety.shouldForgetDeadState(
                    host.isAlive(entity), host.health(entity), host.isExecuting(entity))) {
                forget(entity, true);
            }
            return false;
        }

        PostureRuntimeState state = states.get(entity);
        if (state == null) return false;
        if (host.isBlacklisted(entity)) {
            forget(entity, true);
            return false;
        }

        // Spread attribute reads over ten ticks; recalculation only on changed inputs.
        if (Math.floorMod(host.tickCount(entity) + host.entityId(entity),
                PostureTuning.MAX_POSTURE_REFRESH_INTERVAL_TICKS) == 0) {
            state.refreshMaximum(host.liveInputs(entity));
        }

        switch (state.postureState) {
            case BROKEN, EXECUTING -> tickLocked(entity, state);
            case RECOVERING -> {
                if (state.advanceRecovery(PostureTuning.BREAK_RECOVERY_TICKS)) host.syncNow(entity);
            }
            case NORMAL -> {
                state.advanceRegeneration(host.settings().regenFractionPerSecond());
                if (state.isDisposable()) {
                    // Send the final full snapshot, then stop ticking. The client keeps
                    // the full bar; the next hit recreates an identical fresh state.
                    host.syncNow(entity);
                    states.remove(entity);
                    return false;
                }
            }
        }
        return true;
    }

    private void tickLocked(E entity, PostureRuntimeState state) {
        if (host.isExecuting(entity)) {
            boolean pending = pendingExecutionStart.remove(entity) != null;
            if (pending || state.postureState != PostureState.EXECUTING) {
                host.observeExecution(entity, "CONFIRMED");
            }
            if (state.postureState != PostureState.EXECUTING) {
                state.enterExecution();
                host.syncNow(entity);
            } else {
                state.current = 0.0F;
            }
            return;
        }

        if (state.postureState == PostureState.EXECUTING) {
            // OnExecutionStartEvent precedes Combat Evolution's execution task;
            // keep the special renderer through that short gap.
            if (PostureRuntimeState.waitingForExecutionTask(
                    host.tickCount(entity), pendingExecutionStart.get(entity))) {
                return;
            }
            if (pendingExecutionStart.remove(entity) != null) {
                host.observeExecution(entity, "TIMEOUT");
            }
            state.beginRecovery(PostureTuning.BREAK_RECOVERY_TICKS);
            host.syncNow(entity);
            return;
        }

        if (host.isNeutralizeActive(entity)) {
            state.brokenTicks++;
        } else {
            state.beginRecovery(PostureTuning.BREAK_RECOVERY_TICKS);
            host.syncNow(entity);
        }
    }

    // ---- synchronization -------------------------------------------------

    /**
     * Snapshot for clients. With {@code includePristine}, an eligible entity
     * without a state reports a full bar without allocating a ticking state.
     * An existing state is reported even during death, so a viewer that starts
     * tracking mid-execution still sees it.
     */
    public SyncState snapshot(E entity, boolean includePristine) {
        if (entity == null || !host.isServerEntity(entity) || host.isBlacklisted(entity)) return null;
        PostureRuntimeState state = states.get(entity);
        if (state != null) return new SyncState(state.current, state.max, state.isBrokenForHud());
        if (!includePristine || !canReceive(entity) || !host.isEligible(entity)) return null;
        float max = host.liveInputs(entity).maximumPosture();
        return new SyncState(max, max, false);
    }

    /** Drops the entity's state, optionally telling tracking clients. */
    public void forget(E entity, boolean notifyClients) {
        if (entity == null) return;
        pendingExecutionStart.remove(entity);
        PostureRuntimeState removed = states.remove(entity);
        if (removed != null && notifyClients && host.isServerEntity(entity)) {
            host.syncRemove(entity);
        }
    }

    boolean hasState(E entity) {
        return states.containsKey(entity);
    }

    PostureRuntimeState stateForTests(E entity) {
        return states.get(entity);
    }

    private PostureRuntimeState state(E entity) {
        PostureRuntimeState existing = states.get(entity);
        if (existing != null) return existing;
        PostureRuntimeState created = new PostureRuntimeState(host.liveInputs(entity));
        states.put(entity, created);
        return created;
    }

    private boolean canReceive(E entity) {
        return PostureDeathSafety.canReceivePosture(host.isAlive(entity), host.health(entity));
    }
}
