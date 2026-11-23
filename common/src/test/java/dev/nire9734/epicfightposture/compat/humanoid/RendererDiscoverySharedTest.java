package dev.nire9734.epicfightposture.compat.humanoid;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererDiscoverySharedTest {
    @Test
    void cacheNameIsIdenticalAcrossLoaders() {
        assertEquals("renderer-discovery.json", RendererDiscoveryShared.CACHE_FILE_NAME);
    }

    @Test
    void invalidFingerprintIsRejected() {
        assertEquals(RendererDiscoveryShared.ValidationError.INVALID_FINGERPRINT,
                RendererDiscoveryShared.validateReport("invalid", Set.of(), Set.of(), Set.of(), Set.of()));
    }

    @Test
    void onlyActualChangesOrProblemsTriggerOperatorMessages() {
        assertNull(RendererDiscoveryShared.changeNotice(0, 0, 0, 0, 0, 0));

        RendererDiscoveryShared.OperatorNotice success = RendererDiscoveryShared.changeNotice(2, 0, 18, 0, 0, 0);
        assertFalse(success.problems());
        assertEquals("epicfight_posture_integration.discovery.updated", success.headlineKey());
        assertEquals("[Epic Fight Posture Integration] Compatibility updated: "
                + "2 mob types enabled, 18 existing mobs patched.", success.english());

        RendererDiscoveryShared.OperatorNotice failure = RendererDiscoveryShared.changeNotice(0, 1, 0, 1, 0, 2);
        assertTrue(failure.problems());
        assertEquals("epicfight_posture_integration.discovery.attention", failure.headlineKey());
        assertEquals(List.of("disabled", "conflicts", "failures"),
                failure.details().stream().map(RendererDiscoveryShared.NoticeDetail::key).toList());
        assertTrue(failure.english().contains("1 renderer conflicts"));
        assertTrue(failure.english().contains("2 patch failures"));
        assertTrue(failure.english().endsWith("See server log."));
        assertTrue(RendererDiscoveryShared.reportErrorNotice().contains("failed"));
    }

    @Test
    void playerFacingMessagesUseThePublishedModName() {
        for (String message : new String[] {
                RendererDiscoveryShared.changeNotice(2, 0, 18, 0, 0, 0).english(),
                RendererDiscoveryShared.changeNotice(0, 1, 0, 1, 0, 2).english(),
                RendererDiscoveryShared.reportErrorNotice()}) {
            assertTrue(message.startsWith("[Epic Fight Posture Integration] "), message);
            assertFalse(message.contains("EFPI"), message);
        }
    }

    /** Each loader's lang files must translate every key the server or client sends. */
    @Test
    void langFilesTranslateEveryNoticeKey() throws IOException {
        Path lang = Path.of("src/main/resources/assets/epicfight_posture_integration/lang");
        List<String> keys = new ArrayList<>(List.of(
                RendererDiscoveryShared.FAILED_KEY,
                RendererDiscoveryShared.SEPARATOR_KEY,
                RendererDiscoveryShared.NOTICE_KEY_PREFIX + "updated",
                RendererDiscoveryShared.NOTICE_KEY_PREFIX + "attention",
                "epicfight_posture_integration.server_version_mismatch"));
        for (String detail : List.of("enabled", "patched", "disabled", "conflicts", "skipped", "failures")) {
            keys.add(RendererDiscoveryShared.NOTICE_KEY_PREFIX + detail);
        }
        for (String file : List.of("en_us.json", "ja_jp.json")) {
            String json = Files.readString(lang.resolve(file), StandardCharsets.UTF_8);
            for (String key : keys) {
                Matcher value = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                        .matcher(json);
                assertTrue(value.find(), file + " is missing " + key);
                assertFalse(value.group(1).contains("EFPI"), file + " uses EFPI in " + key);
            }
        }
    }
}
