package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the shared Posture flow through a fake loader host. */
class PostureEngineTest {
    private static final PostureSettings DEFAULTS = new PostureSettings(
            0.22F, 0.10F, 0.5F, 2.0F, 300, 0.02F, false, 0.25F, 0.0F);

    static final class Mob {
        boolean alive = true;
        float health = 20.0F;
        float maxHealth = 20.0F;
        int tick;
        boolean blacklisted;
        boolean eligible = true;
        boolean executing;
        boolean neutralizeSucceeds = true;
        boolean neutralizeActive;
        boolean usingItem = true;
    }

    static final class FakeHost implements PostureHost<Mob> {
        PostureSettings settings = DEFAULTS;
        final List<String> events = new ArrayList<>();
        int syncs;
        int removals;

        @Override public boolean isServerEntity(Mob m) { return true; }
        @Override public boolean isAlive(Mob m) { return m.alive; }
        @Override public float health(Mob m) { return m.health; }
        @Override public int tickCount(Mob m) { return m.tick; }
        @Override public int entityId(Mob m) { return 1; }
        @Override public boolean isBlacklisted(Mob m) { return m.blacklisted; }
        @Override public boolean isEligible(Mob m) { return m.eligible && !m.blacklisted && m.alive; }
        @Override public boolean isExecuting(Mob m) { return m.executing; }
        @Override public PostureInputs liveInputs(Mob m) {
            return PostureInputs.of(m.maxHealth, 0.0F, 0.0F, PostureTuning.MINIMUM_POSTURE, true);
        }
        @Override public float innateEffectiveHealth(Mob m) {
            return PostureBalance.referenceEffectiveHealth(m.maxHealth, 0.0F, 0.0F);
        }
        @Override public void stopUsingItem(Mob m) { m.usingItem = false; }
        @Override public NeutralizeResult applyNeutralize(Mob m) {
            m.neutralizeActive = m.neutralizeSucceeds;
            return m.neutralizeSucceeds ? NeutralizeResult.success() : NeutralizeResult.failure("STUN_NOT_APPLIED");
        }
        @Override public boolean isNeutralizeActive(Mob m) { return m.neutralizeActive; }
        @Override public void syncNow(Mob m) { syncs++; }
        @Override public void syncRemove(Mob m) { removals++; }
        @Override public void observeExecution(Mob m, String stage) { events.add("execution:" + stage); }
        @Override public void observeGuardBreak(Mob m, boolean active, String reason) {
            events.add("break:" + active + ":" + reason);
        }
        @Override public PostureSettings settings() { return settings; }
        @Override public float attackCoefficient(Mob m) { return PostureTuning.BASE_ATTACK_POSTURE_COEFFICIENT; }
        @Override public float parryMultiplier(Mob m) { return 1.0F; }
    }

    private FakeHost host;
    private PostureEngine<Mob> engine;
    private Mob mob;

    @BeforeEach
    void setUp() {
        host = new FakeHost();
        engine = new PostureEngine<>(host);
        mob = new Mob();
    }

    private void tick(int ticks) {
        for (int i = 0; i < ticks; i++) {
            mob.tick++;
            engine.tick(mob);
        }
    }

    private PostureRuntimeState state() {
        return engine.stateForTests(mob);
    }

    /** Removes the whole bar with a direct hit at Impact 3. */
    private void breakWithHits() {
        for (int i = 0; i < 20 && (state() == null || state().postureState == PostureState.NORMAL); i++) {
            engine.applyAttack(mob, 20.0F, 20.0F, 20.0F, 3.0F);
        }
    }

    @Test
    void attackReducesPostureAndSynchronizes() {
        engine.applyAttack(mob, 20.0F, 20.0F, 4.0F, 3.0F);
        assertNotNull(state());
        assertTrue(state().current < state().max);
        assertEquals(PostureState.NORMAL, state().postureState);
        assertEquals(1, host.syncs);
    }

    @Test
    void zeroPostureBreaksIntoNeutralizeThenRecovers() {
        breakWithHits();
        assertEquals(PostureState.BROKEN, state().postureState);
        assertEquals(1, state().completedBreaks);
        assertFalse(mob.usingItem, "a guard break interrupts item use");
        assertTrue(host.events.contains("break:true:none"));

        tick(5);
        assertEquals(PostureState.BROKEN, state().postureState, "stays broken while NEUTRALIZE plays");

        mob.neutralizeActive = false;
        tick(1);
        assertEquals(PostureState.RECOVERING, state().postureState);
        tick(PostureTuning.BREAK_RECOVERY_TICKS);
        assertEquals(PostureState.NORMAL, state().postureState);
        assertEquals(state().max, state().current, 1.0E-5F);
        assertTrue(engine.hasState(mob), "break resistance is kept after recovery");
    }

    @Test
    void rejectedNeutralizeRecoversWithoutCountingABreak() {
        mob.neutralizeSucceeds = false;
        breakWithHits();
        assertEquals(PostureState.RECOVERING, state().postureState);
        assertEquals(0, state().completedBreaks);
        assertTrue(host.events.contains("break:false:STUN_NOT_APPLIED"));
    }

    @Test
    void lockedStatesIgnoreFurtherDamage() {
        breakWithHits();
        float before = state().current;
        engine.applyAttack(mob, 20.0F, 20.0F, 20.0F, 3.0F);
        assertEquals(before, state().current);
        assertEquals(1, state().completedBreaks);
    }

    @Test
    void executionIsConfirmedAndRecoversAfterItEnds() {
        breakWithHits();
        engine.onExecutionStart(mob);
        assertEquals(PostureState.EXECUTING, state().postureState);
        assertTrue(host.events.contains("execution:STARTED"));

        mob.executing = true;
        tick(1);
        assertTrue(host.events.contains("execution:CONFIRMED"));
        assertEquals(PostureState.EXECUTING, state().postureState);

        mob.executing = false;
        tick(1);
        assertEquals(PostureState.RECOVERING, state().postureState);
    }

    @Test
    void executionThatNeverStartsTimesOutAfterTheGracePeriod() {
        engine.onExecutionStart(mob);
        tick(PostureRuntimeState.EXECUTION_START_GRACE_TICKS);
        assertEquals(PostureState.EXECUTING, state().postureState, "waits for the execution task");
        tick(1);
        assertEquals(PostureState.RECOVERING, state().postureState);
        assertTrue(host.events.contains("execution:TIMEOUT"));
    }

    @Test
    void assassinationOfAnUnhurtMobCreatesItsState() {
        assertFalse(engine.hasState(mob));
        engine.onExecutionStart(mob);
        assertEquals(PostureState.EXECUTING, state().postureState);
        assertEquals(0.0F, state().current);
    }

    @Test
    void deathForgetsTheStateUnlessAnExecutionIsRunning() {
        engine.applyAttack(mob, 20.0F, 20.0F, 4.0F, 3.0F);
        mob.alive = false;
        mob.health = 0.0F;
        mob.executing = true;
        tick(1);
        assertTrue(engine.hasState(mob), "an execution may outlive the killing blow");

        mob.executing = false;
        tick(1);
        assertFalse(engine.hasState(mob));
        assertEquals(1, host.removals);
    }

    @Test
    void blacklistingDropsTheState() {
        engine.applyAttack(mob, 20.0F, 20.0F, 4.0F, 3.0F);
        mob.blacklisted = true;
        tick(1);
        assertFalse(engine.hasState(mob));
        assertEquals(1, host.removals);
        assertNull(engine.snapshot(mob, true));
    }

    @Test
    void fullyRegeneratedUnbrokenStateIsReleased() {
        host.settings = new PostureSettings(0.22F, 0.10F, 0.5F, 2.0F, 0, 1.0F, false, 0.25F, 0.0F);
        engine.applyAttack(mob, 20.0F, 20.0F, 2.0F, 3.0F);
        assertTrue(engine.hasState(mob));
        int syncsBefore = host.syncs;
        tick(40);
        assertFalse(engine.hasState(mob));
        assertTrue(host.syncs > syncsBefore, "a final full snapshot was sent");
    }

    @Test
    void parryAppliesAndProjectileDefenseIsOffByDefault() {
        engine.onParried(mob, true);
        assertFalse(engine.hasState(mob) && state().current < state().max, "projectile parry: none by default");

        engine.onParried(mob, false);
        assertTrue(state().current < state().max);
    }

    @Test
    void normalGuardOnlyWhenEnabled() {
        engine.onGuarded(mob, false);
        assertFalse(engine.hasState(mob), "guard Posture is off by default");

        host.settings = new PostureSettings(0.22F, 0.10F, 0.5F, 2.0F, 300, 0.02F, true, 0.25F, 0.0F);
        engine.onGuarded(mob, false);
        assertTrue(state().current < state().max);
    }

    @Test
    void ineligibleOrDeadEntitiesAreIgnored() {
        mob.eligible = false;
        engine.applyAttack(mob, 20.0F, 20.0F, 4.0F, 3.0F);
        engine.onParried(mob, false);
        engine.onExecutionStart(mob);
        assertFalse(engine.hasState(mob));

        mob.eligible = true;
        mob.health = 0.0F;
        engine.applyAttack(mob, 20.0F, 20.0F, 4.0F, 3.0F);
        assertFalse(engine.hasState(mob));
    }

    @Test
    void snapshotsReportPristineExistingAndDeadStates() {
        PostureEngine.SyncState pristine = engine.snapshot(mob, true);
        assertNotNull(pristine);
        assertEquals(pristine.max(), pristine.current());
        assertFalse(engine.hasState(mob), "a pristine snapshot allocates no state");
        assertNull(engine.snapshot(mob, false));

        engine.onExecutionStart(mob);
        mob.alive = false;
        mob.health = 0.0F;
        PostureEngine.SyncState dead = engine.snapshot(mob, true);
        assertNotNull(dead, "a viewer joining mid-execution still sees the state");
        assertTrue(dead.broken());
    }

    @Test
    void acceptsAttackOnlyForUnlockedEligibleEntities() {
        assertTrue(engine.acceptsAttack(mob));
        breakWithHits();
        assertFalse(engine.acceptsAttack(mob));
        Mob ineligible = new Mob();
        ineligible.eligible = false;
        assertFalse(engine.acceptsAttack(ineligible));
    }
}
