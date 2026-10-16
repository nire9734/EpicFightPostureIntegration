package dev.nire9734.epicfightposture.mixin.client;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Caches RenderLayer field discovery used by LayerPoseOverrideContext. */
@Mixin(
        targets = "dev.nire9734.epicfightposture.compat.humanoid.client.LayerPoseOverrideContext$Context",
        remap = false
)
public abstract class LayerPoseReflectionCacheMixin {
    @Unique
    private static final ClassValue<Field[]> EFPI$DECLARED_FIELDS = new ClassValue<>() {
        @Override
        protected Field[] computeValue(Class<?> type) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.trySetAccessible();
                } catch (Throwable ignored) {
                    // The original code already tolerates inaccessible optional fields.
                }
            }
            return fields;
        }
    };

    @Redirect(
            method = "registerLayerModels",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Class;getDeclaredFields()[Ljava/lang/reflect/Field;"
            ),
            remap = false
    )
    private Field[] efpi$cachedDeclaredFields(Class<?> type) {
        return EFPI$DECLARED_FIELDS.get(type);
    }
}
