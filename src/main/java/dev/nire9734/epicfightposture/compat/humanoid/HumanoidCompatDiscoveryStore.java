package dev.nire9734.epicfightposture.compat.humanoid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/** Persists client renderer discovery for troubleshooting and next-startup registration. */
public final class HumanoidCompatDiscoveryStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCHEMA_VERSION = 2;
    private static final String FILE_NAME = "humanoid-detected.json";
    private static final String LEGACY_FILE_NAME = "epicfight_posture_integration-humanoid-detected.json";

    public static Cache load(String expectedEnvironmentFingerprint) {
        migrateLegacyFile();
        Path path = path();
        if (!Files.isRegularFile(path)) {
            return Cache.empty();
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                LOGGER.info("Ignoring legacy EFPI Humanoid compatibility cache {}; renderer discovery will run again", path);
                return Cache.empty();
            }

            JsonObject root = parsed.getAsJsonObject();
            int schema = readInt(root, "schema", -1);
            if (schema != SCHEMA_VERSION) {
                LOGGER.info(
                        "Ignoring EFPI Humanoid compatibility cache {} with schema {}; expected {}",
                        path,
                        schema,
                        SCHEMA_VERSION
                );
                return Cache.empty();
            }

            String storedFingerprint = readString(root, "environment_fingerprint");
            String expectedFingerprint = expectedEnvironmentFingerprint == null ? "" : expectedEnvironmentFingerprint;
            if (!expectedFingerprint.equals(storedFingerprint)) {
                LOGGER.info("EFPI Humanoid compatibility cache is stale after a client mod-set change; rediscovering renderers");
                return Cache.empty();
            }

            Set<ResourceLocation> vanilla = readIds(root.get("vanilla_humanoid"));
            Set<ResourceLocation> gecko = readIds(root.get("geckolib_humanoid"));
            Set<ResourceLocation> overlap = new HashSet<>(vanilla);
            overlap.retainAll(gecko);
            if (!overlap.isEmpty()) {
                LOGGER.warn("Ignoring inconsistent EFPI Humanoid compatibility cache {}; {} ID(s) have multiple renderer kinds",
                        path, overlap.size());
                return Cache.empty();
            }

            return new Cache(Set.copyOf(vanilla), Set.copyOf(gecko));
        } catch (Exception e) {
            LOGGER.warn("Failed to read Humanoid compatibility cache {}", path, e);
            return Cache.empty();
        }
    }

    public static void save(
            String environmentFingerprint,
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> geckoLibHumanoid) {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("schema", SCHEMA_VERSION);
            root.addProperty(
                    "environment_fingerprint",
                    environmentFingerprint == null ? "" : environmentFingerprint
            );
            root.add("vanilla_humanoid", toJsonArray(vanillaHumanoid));
            root.add("geckolib_humanoid", toJsonArray(geckoLibHumanoid));

            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to write Humanoid compatibility cache {}", path, e);
        }
    }

    private static JsonArray toJsonArray(Set<ResourceLocation> ids) {
        JsonArray array = new JsonArray();
        if (ids == null) {
            return array;
        }
        ids.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(id -> array.add(id.toString()));
        return array;
    }

    private static Set<ResourceLocation> readIds(JsonElement element) {
        Set<ResourceLocation> result = new HashSet<>();
        if (element == null || !element.isJsonArray()) {
            return result;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(item.getAsString());
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readString(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : "";
    }

    private static void migrateLegacyFile() {
        Path current = path();
        Path legacy = FMLPaths.CONFIGDIR.get().resolve(LEGACY_FILE_NAME);
        if (Files.exists(current) || !Files.isRegularFile(legacy)) {
            return;
        }

        try {
            Files.createDirectories(current.getParent());
            Files.move(legacy, current, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Moved EFPI Humanoid discovery cache to {}", current);
        } catch (IOException e) {
            LOGGER.warn("Failed to move legacy Humanoid compatibility cache {}", legacy, e);
        }
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(EpicFightPostureIntegration.MODID)
                .resolve(FILE_NAME);
    }

    public record Cache(Set<ResourceLocation> vanillaHumanoid, Set<ResourceLocation> geckoLibHumanoid) {
        public Cache {
            vanillaHumanoid = vanillaHumanoid == null ? Set.of() : Set.copyOf(vanillaHumanoid);
            geckoLibHumanoid = geckoLibHumanoid == null ? Set.of() : Set.copyOf(geckoLibHumanoid);
        }

        public static Cache empty() {
            return new Cache(Set.of(), Set.of());
        }
    }

    private HumanoidCompatDiscoveryStore() {
    }
}
