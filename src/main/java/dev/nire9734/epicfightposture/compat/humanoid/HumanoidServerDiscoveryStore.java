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
 * Dedicated-server persistence for OP-supplied humanoid renderer observations.
 * Reports are keyed by client environment fingerprint, so multiple players on
 * the same mod environment count as one observation rather than multiple votes.
 */
public final class HumanoidServerDiscoveryStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCHEMA_VERSION = 2;
    private static final String FILE_NAME = "humanoid-server-discovery.json";
    private static final int MAX_REPORT_ENVIRONMENTS = 64;

    private static State state = State.fresh("");

    /**
     * Loads the previous server state. A pending list from the previous process
     * becomes active now, before Epic Fight's entity-patch registry event runs.
     */
    public static synchronized ActiveTargets bootstrap(String serverFingerprint) {
        State loaded = load();
        if (!serverFingerprint.equals(loaded.serverFingerprint)) {
            if (!loaded.serverFingerprint.isEmpty()) {
                LOGGER.info("EFPI Humanoid discovery cache is stale after a server mod-set change; clearing it");
            }
            state = State.fresh(serverFingerprint);
            save();
            return ActiveTargets.empty();
        }

        state = loaded;
        if (!state.activeVanilla.equals(state.pendingVanilla)
                || !state.activeGecko.equals(state.pendingGecko)) {
            state.activeVanilla = new HashSet<>(state.pendingVanilla);
            state.activeGecko = new HashSet<>(state.pendingGecko);
            LOGGER.info(
                    "EFPI activated {} dedicated-server Humanoid compatibility target(s): {} vanilla, {} GeckoLib",
                    state.activeVanilla.size() + state.activeGecko.size(),
                    state.activeVanilla.size(),
                    state.activeGecko.size()
            );
            save();
        }
        return new ActiveTargets(state.activeVanilla, state.activeGecko);
    }

    public static synchronized UpdateResult acceptReport(
            String environmentFingerprint,
            Set<ResourceLocation> inspected,
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> geckoLibHumanoid) {
        if (!state.reports.containsKey(environmentFingerprint)
                && state.reports.size() >= MAX_REPORT_ENVIRONMENTS) {
            LOGGER.warn("Ignoring EFPI Humanoid discovery report because {} environment reports are already stored",
                    MAX_REPORT_ENVIRONMENTS);
            return new UpdateResult(
                    false,
                    false,
                    state.pendingVanilla.size(),
                    state.pendingGecko.size(),
                    state.conflicts.size()
            );
        }

        state.reports.put(
                environmentFingerprint,
                new Report(
                        new HashSet<>(inspected),
                        new HashSet<>(vanillaHumanoid),
                        new HashSet<>(geckoLibHumanoid)
                )
        );
        rebuildPending();
        save();

        boolean restartRequired = !state.pendingVanilla.equals(state.activeVanilla)
                || !state.pendingGecko.equals(state.activeGecko);
        return new UpdateResult(
                true,
                restartRequired,
                state.pendingVanilla.size(),
                state.pendingGecko.size(),
                state.conflicts.size()
        );
    }

    public static synchronized Set<ResourceLocation> active() {
        Set<ResourceLocation> result = new HashSet<>(state.activeVanilla);
        result.addAll(state.activeGecko);
        return Set.copyOf(result);
    }

    public static synchronized Set<ResourceLocation> pending() {
        Set<ResourceLocation> result = new HashSet<>(state.pendingVanilla);
        result.addAll(state.pendingGecko);
        return Set.copyOf(result);
    }

    public static synchronized Set<ResourceLocation> conflicts() {
        return Set.copyOf(state.conflicts);
    }

    private static void rebuildPending() {
        Map<ResourceLocation, Observation> observations = new HashMap<>();

        for (Report report : state.reports.values()) {
            for (ResourceLocation id : report.inspected) {
                Observation observation = observations.computeIfAbsent(id, ignored -> new Observation());
                if (report.vanillaHumanoid.contains(id)) {
                    observation.vanilla = true;
                } else if (report.geckoLibHumanoid.contains(id)) {
                    observation.gecko = true;
                } else {
                    observation.nonHumanoid = true;
                }
            }
        }

        Set<ResourceLocation> pendingVanilla = new HashSet<>();
        Set<ResourceLocation> pendingGecko = new HashSet<>();
        Set<ResourceLocation> conflicts = new HashSet<>();
        for (Map.Entry<ResourceLocation, Observation> entry : observations.entrySet()) {
            Observation observation = entry.getValue();
            if (observation.vanilla && !observation.gecko && !observation.nonHumanoid) {
                pendingVanilla.add(entry.getKey());
            } else if (observation.gecko && !observation.vanilla && !observation.nonHumanoid) {
                pendingGecko.add(entry.getKey());
            } else if (observation.vanilla || observation.gecko) {
                conflicts.add(entry.getKey());
            }
        }

        state.pendingVanilla = pendingVanilla;
        state.pendingGecko = pendingGecko;
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
            int schema = readInt(root, "schema", -1);
            if (schema != SCHEMA_VERSION) {
                LOGGER.info(
                        "Ignoring legacy EFPI dedicated-server Humanoid discovery cache {} with schema {}",
                        path,
                        schema
                );
                return State.fresh("");
            }

            State loaded = State.fresh(readString(root, "server_fingerprint"));
            loaded.activeVanilla = readIds(root.get("active_vanilla"));
            loaded.activeGecko = readIds(root.get("active_geckolib"));
            loaded.pendingVanilla = readIds(root.get("pending_vanilla"));
            loaded.pendingGecko = readIds(root.get("pending_geckolib"));
            loaded.conflicts = readIds(root.get("conflicts"));

            JsonElement reportsElement = root.get("reports");
            if (reportsElement != null && reportsElement.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : reportsElement.getAsJsonObject().entrySet()) {
                    if (loaded.reports.size() >= MAX_REPORT_ENVIRONMENTS || !entry.getValue().isJsonObject()) {
                        break;
                    }
                    JsonObject reportObject = entry.getValue().getAsJsonObject();
                    Set<ResourceLocation> inspected = readIds(reportObject.get("inspected"));
                    Set<ResourceLocation> vanilla = readIds(reportObject.get("vanilla_humanoid"));
                    Set<ResourceLocation> gecko = readIds(reportObject.get("geckolib_humanoid"));
                    vanilla.retainAll(inspected);
                    gecko.retainAll(inspected);
                    Set<ResourceLocation> overlap = new HashSet<>(vanilla);
                    overlap.retainAll(gecko);
                    if (!overlap.isEmpty()) {
                        continue;
                    }
                    loaded.reports.put(entry.getKey(), new Report(inspected, vanilla, gecko));
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
            root.addProperty("schema", SCHEMA_VERSION);
            root.addProperty("server_fingerprint", state.serverFingerprint);
            root.add("active_vanilla", toJsonArray(state.activeVanilla));
            root.add("active_geckolib", toJsonArray(state.activeGecko));
            root.add("pending_vanilla", toJsonArray(state.pendingVanilla));
            root.add("pending_geckolib", toJsonArray(state.pendingGecko));
            root.add("conflicts", toJsonArray(state.conflicts));

            JsonObject reports = new JsonObject();
            state.reports.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        JsonObject report = new JsonObject();
                        report.add("inspected", toJsonArray(entry.getValue().inspected));
                        report.add("vanilla_humanoid", toJsonArray(entry.getValue().vanillaHumanoid));
                        report.add("geckolib_humanoid", toJsonArray(entry.getValue().geckoLibHumanoid));
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

    private static Path path() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(EpicFightPostureIntegration.MODID)
                .resolve(FILE_NAME);
    }

    public record ActiveTargets(Set<ResourceLocation> vanillaHumanoid, Set<ResourceLocation> geckoLibHumanoid) {
        public ActiveTargets {
            vanillaHumanoid = vanillaHumanoid == null ? Set.of() : Set.copyOf(vanillaHumanoid);
            geckoLibHumanoid = geckoLibHumanoid == null ? Set.of() : Set.copyOf(geckoLibHumanoid);
        }

        public static ActiveTargets empty() {
            return new ActiveTargets(Set.of(), Set.of());
        }
    }

    public record UpdateResult(
            boolean accepted,
            boolean restartRequired,
            int pendingVanillaCount,
            int pendingGeckoCount,
            int conflictCount) {
        public int pendingCount() {
            return pendingVanillaCount + pendingGeckoCount;
        }
    }

    private static final class Observation {
        boolean vanilla;
        boolean gecko;
        boolean nonHumanoid;
    }

    private static final class Report {
        final Set<ResourceLocation> inspected;
        final Set<ResourceLocation> vanillaHumanoid;
        final Set<ResourceLocation> geckoLibHumanoid;

        Report(
                Set<ResourceLocation> inspected,
                Set<ResourceLocation> vanillaHumanoid,
                Set<ResourceLocation> geckoLibHumanoid) {
            this.inspected = inspected;
            this.vanillaHumanoid = vanillaHumanoid;
            this.geckoLibHumanoid = geckoLibHumanoid;
        }
    }

    private static final class State {
        final String serverFingerprint;
        Set<ResourceLocation> activeVanilla = new HashSet<>();
        Set<ResourceLocation> activeGecko = new HashSet<>();
        Set<ResourceLocation> pendingVanilla = new HashSet<>();
        Set<ResourceLocation> pendingGecko = new HashSet<>();
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
