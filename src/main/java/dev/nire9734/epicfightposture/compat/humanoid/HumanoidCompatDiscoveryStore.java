package dev.nire9734.epicfightposture.compat.humanoid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/** Persists client renderer discovery so the same IDs are available during the next startup. */
public final class HumanoidCompatDiscoveryStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "epicfight_posture_integration-humanoid-detected.json";

    public static Set<ResourceLocation> load() {
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

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private HumanoidCompatDiscoveryStore() {
    }
}
