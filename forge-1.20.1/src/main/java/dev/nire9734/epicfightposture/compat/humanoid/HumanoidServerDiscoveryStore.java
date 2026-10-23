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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Dedicated-server persistence for trusted client renderer observations. */
public final class HumanoidServerDiscoveryStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCHEMA_VERSION = 2;
    private static final String CACHE_DIRECTORY = "cache";
    private static final String FILE_NAME = RendererDiscoveryShared.CACHE_FILE_NAME;
    private static final int MAX_REPORT_ENVIRONMENTS = 64;

    private static State state = State.fresh("");

    /** Promotes the previous process's pending lists before Epic Fight patch registration. */
    public static synchronized ActiveTargets bootstrap(String serverFingerprint) {
        State loaded = load();
        if (!serverFingerprint.equals(loaded.serverFingerprint)) {
            if (!loaded.serverFingerprint.isEmpty()) {
                LOGGER.info("EFPI renderer discovery cache is stale after a server mod-set change; clearing it");
            }
            state = State.fresh(serverFingerprint);
            save();
            return ActiveTargets.empty();
        }

        state = loaded;
        if (!state.activeVanilla.equals(state.pendingVanilla)
                || !state.activeExtended.equals(state.pendingExtended)
                || !state.activeSpider.equals(state.pendingSpider)) {
            state.activeVanilla = new HashSet<>(state.pendingVanilla);
            state.activeExtended = new HashSet<>(state.pendingExtended);
            state.activeSpider = new HashSet<>(state.pendingSpider);
            LOGGER.info(
                    "EFPI activated {} dedicated-server renderer compatibility target(s): {} vanilla humanoid, {} extended humanoid, {} vanilla SpiderModel",
                    state.activeVanilla.size() + state.activeExtended.size() + state.activeSpider.size(),
                    state.activeVanilla.size(),
                    state.activeExtended.size(),
                    state.activeSpider.size()
            );
            save();
        }
        return new ActiveTargets(state.activeVanilla, state.activeExtended, state.activeSpider);
    }

    public static synchronized UpdateResult acceptReport(
            String environmentFingerprint,
            Set<ResourceLocation> inspected,
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> extendedHumanoid,
            Set<ResourceLocation> vanillaSpider) {
        if (!state.reports.containsKey(environmentFingerprint)
                && state.reports.size() >= MAX_REPORT_ENVIRONMENTS) {
            LOGGER.warn("Ignoring EFPI renderer discovery report because {} environment reports are already stored",
                    MAX_REPORT_ENVIRONMENTS);
            return currentResult(false);
        }

        state.reports.put(
                environmentFingerprint,
                new Report(
                        new HashSet<>(inspected),
                        new HashSet<>(vanillaHumanoid),
                        new HashSet<>(extendedHumanoid),
                        new HashSet<>(vanillaSpider)
                )
        );
        rebuildPending();
        save();

        boolean restartRequired = !state.pendingVanilla.equals(state.activeVanilla)
                || !state.pendingExtended.equals(state.activeExtended)
                || !state.pendingSpider.equals(state.activeSpider);
        return new UpdateResult(
                true,
                restartRequired,
                state.pendingVanilla.size(),
                state.pendingExtended.size(),
                state.pendingSpider.size(),
                state.conflicts.size()
        );
    }

    private static UpdateResult currentResult(boolean accepted) {
        boolean restartRequired = !state.pendingVanilla.equals(state.activeVanilla)
                || !state.pendingExtended.equals(state.activeExtended)
                || !state.pendingSpider.equals(state.activeSpider);
        return new UpdateResult(
                accepted,
                restartRequired,
                state.pendingVanilla.size(),
                state.pendingExtended.size(),
                state.pendingSpider.size(),
                state.conflicts.size()
        );
    }

    public static synchronized Set<ResourceLocation> active() {
        Set<ResourceLocation> result = new HashSet<>(state.activeVanilla);
        result.addAll(state.activeExtended);
        result.addAll(state.activeSpider);
        return Set.copyOf(result);
    }

    public static synchronized Set<ResourceLocation> pending() {
        Set<ResourceLocation> result = new HashSet<>(state.pendingVanilla);
        result.addAll(state.pendingExtended);
        result.addAll(state.pendingSpider);
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
                    observation.vanillaHumanoid = true;
                } else if (report.extendedHumanoid.contains(id)) {
                    observation.extendedHumanoid = true;
                } else if (report.vanillaSpider.contains(id)) {
                    observation.vanillaSpider = true;
                } else {
                    observation.unsupported = true;
                }
            }
        }

        Set<ResourceLocation> pendingVanilla = new HashSet<>();
        Set<ResourceLocation> pendingExtended = new HashSet<>();
        Set<ResourceLocation> pendingSpider = new HashSet<>();
        Set<ResourceLocation> conflicts = new HashSet<>();

        for (Map.Entry<ResourceLocation, Observation> entry : observations.entrySet()) {
            Observation observation = entry.getValue();
            int supportedKinds = (observation.vanillaHumanoid ? 1 : 0)
                    + (observation.extendedHumanoid ? 1 : 0)
                    + (observation.vanillaSpider ? 1 : 0);

            if (!observation.unsupported && supportedKinds == 1) {
                if (observation.vanillaHumanoid) {
                    pendingVanilla.add(entry.getKey());
                } else if (observation.extendedHumanoid) {
                    pendingExtended.add(entry.getKey());
                } else {
                    pendingSpider.add(entry.getKey());
                }
            } else if (supportedKinds > 0) {
                conflicts.add(entry.getKey());
            }
        }

        state.pendingVanilla = pendingVanilla;
        state.pendingExtended = pendingExtended;
        state.pendingSpider = pendingSpider;
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
                LOGGER.info("Ignoring incompatible EFPI dedicated-server renderer discovery cache {} with schema {}",
                        path, schema);
                return State.fresh("");
            }

            State loaded = State.fresh(readString(root, "server_fingerprint"));
            loaded.activeVanilla = readIds(root.get("active_vanilla"));
            loaded.activeExtended = readIds(root.get("active_extended_humanoid"));
            loaded.activeSpider = readIds(root.get("active_vanilla_spider"));
            loaded.pendingVanilla = readIds(root.get("pending_vanilla"));
            loaded.pendingExtended = readIds(root.get("pending_extended_humanoid"));
            loaded.pendingSpider = readIds(root.get("pending_vanilla_spider"));
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
                    Set<ResourceLocation> extended = readIds(reportObject.get("extended_humanoid"));
                    Set<ResourceLocation> spider = readIds(reportObject.get("vanilla_spider"));
                    vanilla.retainAll(inspected);
                    extended.retainAll(inspected);
                    spider.retainAll(inspected);
                    if (hasOverlap(vanilla, extended) || hasOverlap(vanilla, spider) || hasOverlap(extended, spider)) {
                        continue;
                    }
                    loaded.reports.put(entry.getKey(), new Report(inspected, vanilla, extended, spider));
                }
            }
            return loaded;
        } catch (Exception exception) {
            LOGGER.warn("Failed to read EFPI dedicated-server renderer discovery cache {}; starting empty", path, exception);
            return State.fresh("");
        }
    }

    private static void save() {
        Path path = path();
        Path temporary = null;
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("schema", SCHEMA_VERSION);
            root.addProperty("server_fingerprint", state.serverFingerprint);
            root.add("active_vanilla", toJsonArray(state.activeVanilla));
            root.add("active_extended_humanoid", toJsonArray(state.activeExtended));
            root.add("active_vanilla_spider", toJsonArray(state.activeSpider));
            root.add("pending_vanilla", toJsonArray(state.pendingVanilla));
            root.add("pending_extended_humanoid", toJsonArray(state.pendingExtended));
            root.add("pending_vanilla_spider", toJsonArray(state.pendingSpider));
            root.add("conflicts", toJsonArray(state.conflicts));

            JsonObject reports = new JsonObject();
            state.reports.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        JsonObject report = new JsonObject();
                        report.add("inspected", toJsonArray(entry.getValue().inspected));
                        report.add("vanilla_humanoid", toJsonArray(entry.getValue().vanillaHumanoid));
                        report.add("extended_humanoid", toJsonArray(entry.getValue().extendedHumanoid));
                        report.add("vanilla_spider", toJsonArray(entry.getValue().vanillaSpider));
                        reports.add(entry.getKey(), report);
                    });
            root.add("reports", reports);

            temporary = Files.createTempFile(path.getParent(), FILE_NAME + ".", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(root, writer);
            }

            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
        } catch (IOException exception) {
            LOGGER.warn("Failed to write EFPI dedicated-server renderer discovery cache {}", path, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupException) {
                    LOGGER.debug("Failed to remove temporary EFPI renderer discovery cache {}", temporary, cleanupException);
                }
            }
        }
    }

    private static boolean hasOverlap(Set<ResourceLocation> first, Set<ResourceLocation> second) {
        Set<ResourceLocation> overlap = new HashSet<>(first);
        overlap.retainAll(second);
        return !overlap.isEmpty();
    }

    private static JsonArray toJsonArray(Set<ResourceLocation> ids) {
        JsonArray array = new JsonArray();
        ids.stream().sorted(Comparator.comparing(ResourceLocation::toString)).forEach(id -> array.add(id.toString()));
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
                .resolve(CACHE_DIRECTORY)
                .resolve(FILE_NAME);
    }

    public record ActiveTargets(
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> extendedHumanoid,
            Set<ResourceLocation> vanillaSpider) {
        public ActiveTargets {
            vanillaHumanoid = vanillaHumanoid == null ? Set.of() : Set.copyOf(vanillaHumanoid);
            extendedHumanoid = extendedHumanoid == null ? Set.of() : Set.copyOf(extendedHumanoid);
            vanillaSpider = vanillaSpider == null ? Set.of() : Set.copyOf(vanillaSpider);
        }

        public static ActiveTargets empty() {
            return new ActiveTargets(Set.of(), Set.of(), Set.of());
        }
    }

    public record UpdateResult(
            boolean accepted,
            boolean restartRequired,
            int pendingVanillaCount,
            int pendingExtendedCount,
            int pendingSpiderCount,
            int conflictCount) {
        public int pendingCount() {
            return pendingVanillaCount + pendingExtendedCount + pendingSpiderCount;
        }
    }

    private static final class Observation {
        boolean vanillaHumanoid;
        boolean extendedHumanoid;
        boolean vanillaSpider;
        boolean unsupported;
    }

    private static final class Report {
        final Set<ResourceLocation> inspected;
        final Set<ResourceLocation> vanillaHumanoid;
        final Set<ResourceLocation> extendedHumanoid;
        final Set<ResourceLocation> vanillaSpider;

        Report(
                Set<ResourceLocation> inspected,
                Set<ResourceLocation> vanillaHumanoid,
                Set<ResourceLocation> extendedHumanoid,
                Set<ResourceLocation> vanillaSpider) {
            this.inspected = inspected;
            this.vanillaHumanoid = vanillaHumanoid;
            this.extendedHumanoid = extendedHumanoid;
            this.vanillaSpider = vanillaSpider;
        }
    }

    private static final class State {
        final String serverFingerprint;
        Set<ResourceLocation> activeVanilla = new HashSet<>();
        Set<ResourceLocation> activeExtended = new HashSet<>();
        Set<ResourceLocation> activeSpider = new HashSet<>();
        Set<ResourceLocation> pendingVanilla = new HashSet<>();
        Set<ResourceLocation> pendingExtended = new HashSet<>();
        Set<ResourceLocation> pendingSpider = new HashSet<>();
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
