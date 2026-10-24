package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostureCoverageReportTest {
    @Test
    void comparesLegacyAndCurrentWithoutConflatingPendingWithRegression() {
        PostureCoverageReport report = new PostureCoverageReport("forge");
        report.observe("test:new_rejection", true, "UNSUPPORTED", "INVALID_ARMATURE_STRUCTURE", "DemoPatch");
        report.observe("test:still_supported", true, "SUPPORTED", "READY", "DemoPatch");
        report.observe("test:pending", true, "PENDING", "ANIMATOR_NOT_READY", "DemoPatch");
        report.observe("test:expansion", false, "SUPPORTED", "READY", "DemoPatch");

        String formatted = report.format();
        assertTrue(formatted.contains("test:new_rejection\ttrue\tUNSUPPORTED\tINVALID_ARMATURE_STRUCTURE\tnewly_excluded\t"));
        assertTrue(formatted.contains("test:still_supported\ttrue\tSUPPORTED\tREADY\tunchanged\t"));
        assertTrue(formatted.contains("test:pending\ttrue\tPENDING\tANIMATOR_NOT_READY\tpending\t"));
        assertTrue(formatted.contains("test:expansion\tfalse\tSUPPORTED\tREADY\tnewly_supported\t"));
        assertTrue(formatted.indexOf("test:expansion\t") < formatted.indexOf("test:new_rejection\t"));
    }

    @Test
    void updatesPriorObservationWithoutClaimingTwoStableMobTypes() {
        PostureCoverageReport report = new PostureCoverageReport("neoforge");
        report.observe("test:mob", true, "PENDING", "PATCH_NOT_INITIALIZED", "Patch");
        report.observe("test:mob", true, "SUPPORTED", "READY", "Patch");
        report.observe("test:mob", true, "SUPPORTED", "READY", "Patch");

        assertTrue(report.format().contains("test:mob\ttrue\tSUPPORTED\tREADY\tunchanged\tPatch\ttrue\n"));
        assertEquals(1, report.format().lines().filter(line -> line.startsWith("test:mob\t")).count());
    }

    @Test
    void sanitizesUntrustedFreeTextFields() {
        PostureCoverageReport report = new PostureCoverageReport("forge");
        report.observe("test:mob", false, "UNSUPPORTED", "MISSING_PATCH", "Bad\tPatch\nText");

        assertTrue(report.format().contains("Bad_Patch_Text\tfalse\n"));
        assertFalse(report.format().contains("Bad\tPatch"));
    }

    @Test
    void writesAtomicallyAndResetsBetweenServerSessions(@TempDir Path temp) throws Exception {
        PostureCoverageReport report = new PostureCoverageReport("forge");
        report.observe("test:mob", true, "SUPPORTED", "READY", "Patch");
        assertTrue(report.flushIfDue(temp));
        Path output = temp.resolve("epicfight_posture_integration/cache/forge-posture-coverage.tsv");
        assertTrue(Files.readString(output).contains("test:mob\t"));
        assertFalse(report.flushIfDue(temp));

        report.reset();
        report.flush(temp);
        assertFalse(Files.readString(output).contains("test:mob\t"));
    }
}
