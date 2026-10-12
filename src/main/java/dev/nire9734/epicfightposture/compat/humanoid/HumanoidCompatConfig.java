package dev.nire9734.epicfightposture.compat.humanoid;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Common startup config for automatic HumanoidModel compatibility. */
@Mod.EventBusSubscriber(modid = EpicFightPostureIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HumanoidCompatConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue AUTO_DETECT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FORCE_INCLUDE;
    public static final ForgeConfigSpec SPEC;

    private static volatile Set<ResourceLocation> blacklist = Set.of();
    private static volatile Set<ResourceLocation> forceInclude = Set.of();

    static {
        BUILDER.push("humanoid_compat");
        AUTO_DETECT = BUILDER
                .comment("Automatically detect third-party entity renderers whose active model extends vanilla HumanoidModel.")
                .define("auto_detect", true);
        ENTITY_BLACKLIST = BUILDER
                .comment(
                        "Entity IDs that must never receive EFPI's lightweight Humanoid compatibility patch.",
                        "Use namespace:entity. This is separate from posture.entity_blacklist."
                )
                .defineListAllowEmpty("entity_blacklist", List.of(), value -> value instanceof String);
        FORCE_INCLUDE = BUILDER
                .comment(
                        "Entity IDs to register as Humanoid-compatible even when automatic client detection is unavailable.",
                        "Useful on dedicated servers. Only add entities whose renderer really uses HumanoidModel."
                )
                .defineListAllowEmpty("force_include", List.of(), value -> value instanceof String);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    public static boolean autoDetect() {
        return AUTO_DETECT.get();
    }

    public static boolean isBlacklisted(ResourceLocation id) {
        return id != null && blacklist.contains(id);
    }

    public static Set<ResourceLocation> forceInclude() {
        return forceInclude;
    }

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            refresh();
        }
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            refresh();
        }
    }

    public static void refresh() {
        blacklist = parse(ENTITY_BLACKLIST.get(), "humanoid_compat.entity_blacklist");
        forceInclude = parse(FORCE_INCLUDE.get(), "humanoid_compat.force_include");
    }

    private static Set<ResourceLocation> parse(List<? extends String> values, String key) {
        Set<ResourceLocation> parsed = new HashSet<>();
        for (String raw : values) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) {
                LOGGER.warn("Ignoring invalid entity ID '{}' in {}", raw, key);
                continue;
            }
            parsed.add(id);
        }
        return Set.copyOf(parsed);
    }

    private HumanoidCompatConfig() {
    }
}
