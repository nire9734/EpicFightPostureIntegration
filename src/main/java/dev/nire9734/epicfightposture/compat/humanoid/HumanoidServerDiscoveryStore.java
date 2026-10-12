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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Dedicated-server persistence for OP-supplied HumanoidModel observations.
 * Reports are keyed by client environment fingerprint, so multiple players on
 * the same mod environment count as one observation rather than multiple votes.
 */
public final class HumanoidServerDiscoveryStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "humanoid-server-discovery.json";
    private static final int MAX_REPORT_ENVIRONMENTS = 64;

    private static State state = State.fresh("");

    /**
     * Loads the previous server state. A pending list from the previous process
     * becomes active now, before Epic Fight's entity-patch registry event runs.
     */
    public static synchronized Set<ResourceLocation> bootstrap(String serverFingerprint) {
        State loaded = load();
        if (!serverFingerprint.equals(loaded.serverFingerprint)) {
            if (!loaded.serverFingerprint.isEmpty()) {
                LOGGER.info("EFPI Humanoid discovery cache is stale after a server mod-set change; clearing it");
            }
            state = State.fresh(serverFingerprint);
            save();
            return Set.of();
        }

        state = loaded;
        if (!state.active.equals(state.pending)) {
            state.active = new HashSet<>(state.pending);
            LOGGER.info("EFPI activated {} dedicated-server Humanoid compatibility target(s)", state.active.size());
            save();
        }
        return Set.copyOf(state.active);
    }

    public static synchronized UpdateResult acceptReport(
            String environmentFingerprint,
            Set<ResourceLocation> inspected,
            Set<ResourceLocation> humanoid) {
        if (!state.reports.containsKey(environmentFingerprint)
                && state.reports.size() >= MAX_REPORT_ENVIRONMENTS) {
            LOGGER.warn("Ignoring EFPI Humanoid discovery report because {} environment reports are already stored",
                    MAX_REPORT_ENVIRONMENTS);
            return new UpdateResult(false, false, state.pending.size(), state.conflicts.size());
        }

        state.reports.put(
                environmentFingerprint,
                new Report(new HashSet<>(inspected), new HashSet<>(humanoid))
        );
        rebuildPending();
        save();

        boolean restartRequired = !state.pending.equals(state.active);
        return new UpdateResult(true, restartRequired, state.pending.size(), state.conflicts.size());
    }

    public static synchronized Set<ResourceLocation> active() {
        return Set.copyOf(state.active);
    }

    public static synchronized Set<ResourceLocation> pending() {
        return Set.copyOf(state.pending);
    }

    public static synchronized Set<ResourceLocation> conflicts() {
        return Set.copyOf(state.conflicts);
    }

    private static void rebuildPending() {
        Map<ResourceLocation, Observation> observations = new HashMap<>();

        for (Report report : state.reports.values()) {
            for (ResourceLocation id : report.inspected) {
                Observation observation = observations.computeIfAbsent(id, ignored -> new Observation());
                if (report.humanoid.contains(id)) {
                    observation.humanoid = true;
                } else {
                    observation.nonHumanoid = true;
                }
            }
        }

        Set<ResourceLocation> pending = new HashSet<>();
        Set<ResourceLocation> conflicts = new HashSet<>();
        for (Map.Entry<ResourceLocation, Observation> entry : observations.entrySet()) {
            Observation observation = entry.getValue();
            if (observation.humanoid && observation.nonHumanoid) {
                conflicts.add(entry.getKey());
            } else if (observation.humanoid) {
                pending.add(entry.getKey());
            }
        }

        state.pending = pending;
        state.conflicts = conflicts;
    }

    private static State load() {
        Path path = path();
        if (!Files.isRegularFile(path)) {
            return State.fresh("");
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalStateException("root must be a JSON object");
            }

            JsonObject root = parsed.getAsJsonObject();
            State loaded = State.fresh(readString(root, "server_fingerprint"));
            loaded.active = readIds(root.get("active"));
            loaded.pending = readIds(root.get("pending"));
            loaded.conflicts = readIds(root.get("conflicts"));

            JsonElement reportsElement = root.get("reports");
            if (reportsElement != null && reportsElement.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : reportsElement.getAsJsonObject().entrySet()) {
                    if (loaded.reports.size() >= MAX_REPORT_ENVIRONMENTS || !entry.getValue().isJsonObject()) {
                        break;
                    }
                    JsonObject reportObject = entry.getValue().getAsJsonObject();
                    Set<ResourceLocation> inspected = readIds(reportObject.get("inspected"));
                    Set<ResourceLocation> humanoid = readIds(reportObject.get("humanoid"));
                    humanoid.retainAll(inspected);
                    loaded.reports.put(entry.getKey(), new Report(inspected, humanoid));
                }
            }
            return loaded;
        } catch (Exception exception) {
            LOGGER.warn("Failed to read EFPI dedicated-server Humanoid discovery cache {}; starting empty", path, exception);
            return State.fresh("");
        }
    }

    private static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("server_fingerprint", state.serverFingerprint);
            root.add("active", toJsonArray(state.active));
            root.add("pending", toJsonArray(state.pending));
            root.add("conflicts", toJsonArray(state.conflicts));

            JsonObject reports = new JsonObject();
            state.reports.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        JsonObject report = new JsonObject();
                        report.add("inspected", toJsonArray(entry.getValue().inspected));
                        report.add("humanoid", toJsonArray(entry.getValue().humanoid));
                        reports.add(entry.getKey(), report);
                    });
            root.add("reports", reports);

            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to write EFPI dedicated-server Humanoid discovery cache {}", path, exception);
        }
    }

    private static JsonArray toJsonArray(Set<ResourceLocation> ids) {
        JsonArray array = new JsonArray();
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

    private static String readString(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : "";
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(EpicFightPostureIntegration.MODID)
                .resolve(FILE_NAME);
    }

    public record UpdateResult(boolean accepted, boolean restartRequired, int pendingCount, int conflictCount) {
    }

    private static final class Observation {
        boolean humanoid;
        boolean nonHumanoid;
    }

    private static final class Report {
        final Set<ResourceLocation> inspected;
        final Set<ResourceLocation> humanoid;

        Report(Set<ResourceLocation> inspected, Set<ResourceLocation> humanoid) {
            this.inspected = inspected;
            this.humanoid = humanoid;
        }
    }

    private static final class State {
        final String serverFingerprint;
        Set<ResourceLocation> active = new HashSet<>();
        Set<ResourceLocation> pending = new HashSet<>();
        Set<ResourceLocation> conflicts = new HashSet<>();
        final Map<String, Report> reports = new HashMap<>();

        private State(String serverFingerprint) {
            this.serverFingerprint = serverFingerprint;
        }

        static State fresh(String serverFingerprint) {
            return new State(serverFingerprint == null ? "" : serverFingerprint);
        }
    }

    private HumanoidServerDiscoveryStore() {
    }
}
