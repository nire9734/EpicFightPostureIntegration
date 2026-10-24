package dev.nire9734.epicfightposture.compat.humanoid;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Shared post-authentication discovery pipeline for Forge and NeoForge. */
public final class RendererDiscoveryReportProcessor {
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
            Supplier<RendererDiscoveryRuntime.LiveRefresh> refreshLoadedMobs) {
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
        RendererDiscoveryRuntime.ActivationUpdate activation =
                RendererDiscoveryRuntime.activateTrackingChanges(previous, current, register);
        RendererDiscoveryRuntime.LiveRefresh refresh = refreshLoadedMobs.get();
        Set<ResourceLocation> newConflicts = new HashSet<>(RendererDiscoveryStore.conflicts());
        newConflicts.removeAll(previousConflicts);
        String notice = RendererDiscoveryShared.changeNotice(
                activation.newlyReady(), RendererDiscoveryRuntime.withdrawnTargets(previous, current),
                refresh.updated(), newConflicts.size(), activation.newlySkipped(), refresh.failed());
        return new Outcome(RendererDiscoveryShared.ValidationError.NONE, report, activation, refresh, notice);
    }

    private static Set<ResourceLocation> validSubset(
            Set<ResourceLocation> candidates, Set<ResourceLocation> inspected) {
        Set<ResourceLocation> result = new HashSet<>(candidates);
        result.retainAll(inspected);
        return result;
    }

    private RendererDiscoveryReportProcessor() { }
}
