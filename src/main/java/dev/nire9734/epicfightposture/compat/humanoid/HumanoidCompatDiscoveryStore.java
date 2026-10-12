package dev.nire9734.epicfightposture.compat.humanoid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
    private static final String FILE_NAME = "humanoid-detected.json";
    private static final String LEGACY_FILE_NAME = "epicfight_posture_integration-humanoid-detected.json";

    public static Set<ResourceLocation> load() {
        migrateLegacyFile();
        Path path = path();
        if (!Files.isRegularFile(path)) {
            return Set.of();
        }

        Set<ResourceLocation> result = new HashSet<>();
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray()) {
                LOGGER.warn("Ignoring malformed Humanoid compatibility cache {}; expected JSON array", path);
                return Set.of();
            }
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
                if (id != null) {
                    result.add(id);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read Humanoid compatibility cache {}", path, e);
        }
        return Set.copyOf(result);
    }

    public static void save(Set<ResourceLocation> ids) {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            JsonArray array = new JsonArray();
            ids.stream()
                    .sorted(Comparator.comparing(ResourceLocation::toString))
                    .forEach(id -> array.add(id.toString()));
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(array, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to write Humanoid compatibility cache {}", path, e);
        }
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

    private HumanoidCompatDiscoveryStore() {
    }
}
