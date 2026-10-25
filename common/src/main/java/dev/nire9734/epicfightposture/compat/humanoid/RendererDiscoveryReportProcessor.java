package dev.nire9734.epicfightposture.compat.humanoid;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/** Shared post-authentication discovery pipeline for Forge and NeoForge. */
public final class RendererDiscoveryReportProcessor {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Reset on server/bootstrap registry rebuild. Never carry registration assurances across worlds. */
    private static final AtomicLong registryGeneration = new AtomicLong();
    private static RendererDiscoveryStore.ActiveTargets lastVerified;
    private static boolean retryRequired;
    private static int lastReady;
    private static long lastBootstrapGeneration = -1;
    private static long lastRegistryGeneration = -1;

    /** Can be called from a registry event without acquiring the report processing lock. */
    public static void invalidateRuntimeValidation() {
        registryGeneration.incrementAndGet();
    }

    private static void forgetLastSuccess() {
        lastVerified = null;
        retryRequired = false;
        lastReady = 0;
    }

    public record Outcome(
            RendererDiscoveryShared.ValidationError validationError,
            RendererDiscoveryStore.UpdateResult report,
            RendererDiscoveryRuntime.ActivationUpdate activation,
            RendererDiscoveryRuntime.LiveRefresh refresh,
            String operatorNotice) {
        public boolean accepted() {
            return report != null && report.accepted();
        }

        /** Identical server-log summary on both loaders. */
        public String acceptanceSummary(String operatorName) {
            if (!accepted()) throw new IllegalStateException("Renderer report was not accepted");
            return "EFPI accepted renderer discovery from " + operatorName + ": "
                    + report.pendingVanillaCount() + " vanilla, "
                    + report.pendingExtendedCount() + " extended, "
                    + report.pendingSpiderCount() + " spiders, "
                    + report.conflictCount() + " conflicts; "
                    + activation.ready() + " runtime types ready, "
                    + activation.skipped() + " skipped; "
                    + refresh.updated() + " existing mobs updated, "
                    + refresh.failed() + " failed";
        }
    }

    /** The loader must authenticate the OP and consume their nonce before calling this method. */
    public static synchronized Outcome process(
            UUID operator,
            String fingerprint,
            Set<ResourceLocation> inspected,
            Set<ResourceLocation> vanilla,
            Set<ResourceLocation> extended,
            Set<ResourceLocation> spider,
            Predicate<ResourceLocation> validEntity,
            BiPredicate<ResourceLocation, RendererDiscoveryRuntime.Kind> register,
            Function<Set<ResourceLocation>, RendererDiscoveryRuntime.LiveRefresh> refreshLoadedMobs) {
        long bootstrapGeneration = RendererDiscoveryStore.bootstrapGeneration();
        long currentRegistryGeneration = registryGeneration.get();
        if (lastBootstrapGeneration != bootstrapGeneration
                || lastRegistryGeneration != currentRegistryGeneration) {
            forgetLastSuccess();
            lastBootstrapGeneration = bootstrapGeneration;
            lastRegistryGeneration = currentRegistryGeneration;
        }
        RendererDiscoveryShared.ValidationError error = RendererDiscoveryShared.validateReport(
                fingerprint, inspected, vanilla, extended, spider);
        if (error != RendererDiscoveryShared.ValidationError.NONE) {
            return new Outcome(error, null, null, null, null);
        }

        Set<ResourceLocation> validInspected = new HashSet<>();
        for (ResourceLocation id : inspected) {
            if (validEntity.test(id)) validInspected.add(id);
        }
        Set<ResourceLocation> validVanilla = validSubset(vanilla, validInspected);
        Set<ResourceLocation> validExtended = validSubset(extended, validInspected);
        Set<ResourceLocation> validSpider = validSubset(spider, validInspected);

        RendererDiscoveryStore.ActiveTargets previous = RendererDiscoveryStore.pendingTargets();
        Set<ResourceLocation> previousConflicts = RendererDiscoveryStore.conflicts();
        RendererDiscoveryStore.UpdateResult report = RendererDiscoveryStore.acceptOperatorReport(
                operator, fingerprint, validInspected, validVanilla, validExtended, validSpider);
        if (!report.accepted()) {
            return new Outcome(RendererDiscoveryShared.ValidationError.NONE, report, null, null, null);
        }

        RendererDiscoveryStore.ActiveTargets current = RendererDiscoveryStore.pendingTargets();
        Set<ResourceLocation> newConflicts = new HashSet<>(RendererDiscoveryStore.conflicts());
        newConflicts.removeAll(previousConflicts);
        // An identical approved set needs no registration or world-wide entity walk.
        // Still accept and persist the individual operator's observation for conflict safety.
        if (!retryRequired && lastVerified != null && previous.equals(current)
                && lastVerified.equals(current)) {
            return new Outcome(RendererDiscoveryShared.ValidationError.NONE, report,
                    new RendererDiscoveryRuntime.ActivationUpdate(lastReady, 0, 0, 0),
                    new RendererDiscoveryRuntime.LiveRefresh(0, 0),
                    RendererDiscoveryShared.changeNotice(0, 0, 0, newConflicts.size(), 0, 0));
        }

        boolean canReuse = lastVerified != null && !retryRequired;
        RendererDiscoveryStore.ActiveTargets trustedBefore = lastVerified;
        RendererDiscoveryRuntime.ActivationUpdate activation =
                RendererDiscoveryRuntime.activateTrackingChanges(previous, current, (id, kind) ->
                        (canReuse && RendererDiscoveryRuntime.contains(trustedBefore, id, kind))
                                || safelyRegister(register, id, kind));

        // Retry all approved types after a previous failure or a registry rebuild;
        // otherwise touch only types that became newly approved in this update.
        Set<ResourceLocation> refreshTypes = lastVerified == null || retryRequired
                ? RendererDiscoveryRuntime.newlyApprovedTypes(RendererDiscoveryStore.ActiveTargets.empty(), current)
                : RendererDiscoveryRuntime.newlyApprovedTypes(previous, current);
        RendererDiscoveryRuntime.LiveRefresh refresh = refreshTypes.isEmpty()
                ? new RendererDiscoveryRuntime.LiveRefresh(0, 0)
                : safelyRefresh(refreshLoadedMobs, refreshTypes);
        retryRequired = activation.skipped() > 0 || refresh.failed() > 0;
        lastVerified = current;
        lastReady = activation.ready();
        String notice = RendererDiscoveryShared.changeNotice(
                activation.newlyReady(), RendererDiscoveryRuntime.withdrawnTargets(previous, current),
                refresh.updated(), newConflicts.size(), activation.newlySkipped(), refresh.failed());
        return new Outcome(RendererDiscoveryShared.ValidationError.NONE, report, activation, refresh, notice);
    }

    private static boolean safelyRegister(
            BiPredicate<ResourceLocation, RendererDiscoveryRuntime.Kind> register,
            ResourceLocation id, RendererDiscoveryRuntime.Kind kind) {
        try {
            return register.test(id, kind);
        } catch (RuntimeException exception) {
            LOGGER.warn("EFPI failed to register trusted renderer compatibility for {} ({})", id, kind, exception);
            return false;
        }
    }

    private static RendererDiscoveryRuntime.LiveRefresh safelyRefresh(
            Function<Set<ResourceLocation>, RendererDiscoveryRuntime.LiveRefresh> refresh,
            Set<ResourceLocation> affected) {
        try {
            RendererDiscoveryRuntime.LiveRefresh result = refresh.apply(affected);
            if (result == null) throw new IllegalStateException("Live compatibility refresh returned no result");
            return result;
        } catch (RuntimeException exception) {
            LOGGER.warn("EFPI could not complete the existing-mob compatibility refresh", exception);
            return new RendererDiscoveryRuntime.LiveRefresh(0, 1);
        }
    }

    private static Set<ResourceLocation> validSubset(
            Set<ResourceLocation> candidates, Set<ResourceLocation> inspected) {
        Set<ResourceLocation> result = new HashSet<>(candidates);
        result.retainAll(inspected);
        return result;
    }

    /** Read-only, conflict-aware diagnosis of the currently accepted OP scan. */
    public static Map<String, String> diagnosticSnapshot(Set<ResourceLocation> recentlyInspected) {
        RendererDiscoveryStore.ActiveTargets approved = RendererDiscoveryStore.pendingTargets();
        Set<ResourceLocation> conflicts = RendererDiscoveryStore.conflicts();
        Set<ResourceLocation> keys = new HashSet<>(recentlyInspected);
        keys.addAll(approved.vanillaHumanoid());
        keys.addAll(approved.extendedHumanoid());
        keys.addAll(approved.vanillaSpider());
        keys.addAll(conflicts);
        Map<String, String> summary = new TreeMap<>();
        for (ResourceLocation key : keys) {
            String status = conflicts.contains(key) ? "CONFLICT"
                    : approved.vanillaHumanoid().contains(key) ? "verified:vanilla_humanoid"
                    : approved.extendedHumanoid().contains(key) ? "verified:extended_humanoid"
                    : approved.vanillaSpider().contains(key) ? "verified:spider"
                    : "scanned_not_approved";
            summary.put(key.toString(), status);
        }
        return summary;
    }

    private RendererDiscoveryReportProcessor() { }
}
