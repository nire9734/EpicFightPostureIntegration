package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostureCoverageReportTest {
    @Test
    void reportsCurrentEligibilityWithoutHistoricalComparison() {
        PostureCoverageReport report = new PostureCoverageReport("forge");
        report.observe("test:rejected", "UNSUPPORTED", "INVALID_ARMATURE_STRUCTURE", "DemoPatch");
        report.observe("test:supported", "SUPPORTED", "READY", "DemoPatch");
        report.observe("test:pending", "PENDING", "ANIMATOR_NOT_READY", "DemoPatch");

        String formatted = report.format();
        assertTrue(formatted.contains("# EFPI diagnostics v3"));
        assertFalse(formatted.contains("legacy_eligible"));
        assertFalse(formatted.contains("\tcomparison\t"));
        assertTrue(formatted.contains("test:rejected\tUNSUPPORTED\tINVALID_ARMATURE_STRUCTURE\tnone\tnone\tDemoPatch"));
        assertTrue(formatted.contains("test:supported\tSUPPORTED\tREADY\tnone\tnone\tDemoPatch"));
        assertTrue(formatted.contains("test:pending\tPENDING\tANIMATOR_NOT_READY\tnone\tnone\tDemoPatch"));
    }

    @Test
    void updatesPriorObservationWithoutDuplicatingMobTypes() {
        PostureCoverageReport report = new PostureCoverageReport("neoforge");
        report.observe("test:mob", "PENDING", "PATCH_NOT_INITIALIZED", "Patch");
        report.observe("test:mob", "SUPPORTED", "READY", "Patch");
        report.observe("test:mob", "SUPPORTED", "READY", "Patch");

        assertTrue(report.format().contains("test:mob\tSUPPORTED\tREADY\tnone\tnone\tPatch\ttrue\t"));
        assertEquals(1, report.format().lines().filter(line -> line.startsWith("test:mob\t")).count());
    }

    @Test
    void providesConcreteMissingPatchCluesWithoutAssumingUnknownCauses() {
        PostureCoverageReport report = new PostureCoverageReport("forge");
        report.observe("test:unsupported", "UNSUPPORTED", "MISSING_PATCH", "none");
        report.observe("test:registration", "UNSUPPORTED", "MISSING_PATCH", "none");
        report.observe("test:ready_renderer", "UNSUPPORTED", "MISSING_PATCH", "none");
        report.observe("test:unscanned", "UNSUPPORTED", "MISSING_PATCH", "none");
        report.observe("test:conflict", "UNSUPPORTED", "MISSING_PATCH", "none");
        report.observe("test:backend", "UNSUPPORTED", "MISSING_PATCH", "none");
        report.observe("test:renderer", "UNSUPPORTED", "MISSING_PATCH", "none");
        report.replaceRendererScan("client", Map.of(
                "test:unsupported", "rejected:unsupported_model",
                "test:registration", "rejected:patch_registration",
                "test:ready_renderer", "verified:vanilla",
                "test:conflict", "CONFLICT",
                "test:backend", "rejected:backend_model_not_supported",
                "test:renderer", "rejected:renderer_registration_slot"));

        String formatted = report.format();
        assertTrue(formatted.contains("test:unsupported\tUNSUPPORTED\tMISSING_PATCH\tMODEL_CLASSIFICATION\trejected:unsupported_model"));
        assertTrue(formatted.contains("test:registration\tUNSUPPORTED\tMISSING_PATCH\tPATCH_REGISTRATION\trejected:patch_registration"));
        assertTrue(formatted.contains("test:ready_renderer\tUNSUPPORTED\tMISSING_PATCH\tSERVER_PATCH\tRENDERER_VERIFIED_BUT_SERVER_PATCH_MISSING"));
        assertTrue(formatted.contains("test:unscanned\tUNSUPPORTED\tMISSING_PATCH\tUNKNOWN\tNO_RENDERER_SCAN_AVAILABLE"));
        assertTrue(formatted.contains("test:conflict\tUNSUPPORTED\tMISSING_PATCH\tOP_CONSENSUS\tOP_REPORT_CONFLICT"));
        assertTrue(formatted.contains("test:backend\tUNSUPPORTED\tMISSING_PATCH\tBACKEND_PREPARATION\trejected:backend_model_not_supported"));
        assertTrue(formatted.contains("test:renderer\tUNSUPPORTED\tMISSING_PATCH\tRENDERER_REGISTRATION\trejected:renderer_registration_slot"));
    }

    @Test
    void sanitizesUntrustedFreeTextFields() {
        PostureCoverageReport report = new PostureCoverageReport("forge");
        report.observe("test:mob", "UNSUPPORTED", "MISSING_PATCH", "Bad\tPatch\nText");

        assertTrue(report.format().contains("Bad_Patch_Text\tfalse\t"));
        assertFalse(report.format().contains("Bad\tPatch"));
    }

    @Test
    void writesAtomicallyAndResetsBetweenServerSessions(@TempDir Path temp) throws Exception {
        PostureCoverageReport report = new PostureCoverageReport("forge");
        report.observe("test:mob", "SUPPORTED", "READY", "Patch");
        assertTrue(report.flushIfDue(temp));
        Path output = temp.resolve("epicfight_posture_integration/cache/forge-diagnostics.tsv");
        assertTrue(Files.readString(output).contains("test:mob\t"));
        assertFalse(report.flushIfDue(temp));

        report.reset();
        report.flush(temp);
        assertFalse(Files.readString(output).contains("test:mob\t"));
    }

    @Test
    void combinesRegistryRendererPostureAndRealCombatOutcomes() {
        PostureCoverageReport report = new PostureCoverageReport("forge");
        report.registerEntityIds(List.of("test:unspawned", "test:mob"));
        report.setModVersions(Map.of("epicfight", "20.14.17", "combat_evolution", "2.2.8"));
        report.replaceRendererScan("client", Map.of("test:mob", "verified:vanilla"));
        report.observe("test:mob", "SUPPORTED", "READY", "DemoPatch");
        report.observeGuardBreak("test:mob", false, "STUN_NOT_APPLIED");
        report.observeGuardBreak("test:mob", true, "none");
        report.observeExecution("test:mob", "STARTED");
        report.observeExecution("test:mob", "TIMEOUT");

        String formatted = report.format();
        assertTrue(formatted.contains("# mods: combat_evolution@2.2.8;epicfight@20.14.17"));
        assertTrue(formatted.contains("test:unspawned\tNOT_OBSERVED\tNOT_OBSERVED\tnone\tnone"));
        assertTrue(formatted.contains("test:mob\tSUPPORTED\tREADY\tnone\tnone\tDemoPatch\tfalse\ttrue\tverified:vanilla\tclient\t1\t1\tSTUN_NOT_APPLIED\t1\t0\t1"));
    }

    @Test
    void resetPreservesIntegratedClientScanButDropsPriorServerOutcomes() {
        PostureCoverageReport report = new PostureCoverageReport("neoforge");
        report.replaceRendererScan("client", Map.of("test:mob", "verified:extended"));
        report.replaceRendererScan("trusted_op", Map.of("test:other", "CONFLICT"));
        report.observeGuardBreak("test:mob", false, "STUN_EXCEPTION");
        report.reset();

        String formatted = report.format();
        assertTrue(formatted.contains("test:mob\tNOT_OBSERVED"));
        assertTrue(formatted.contains("verified:extended\tclient\t0\t0"));
        assertFalse(formatted.contains("test:other\t"));
    }
}
