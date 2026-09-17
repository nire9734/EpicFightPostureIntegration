package dev.nire9734.epicfightposture;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small reflection cache retained for compatibility fallbacks.
 * Hot-path Minecraft entity access should use normal mapped methods instead.
 */
public final class FastReflection {
    private static final Map<Class<?>, Map<String, Method>> NO_ARG = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Method>> ONE_ARG = new ConcurrentHashMap<>();

    private FastReflection() {}

    public static Object invokeNamed(Object target, String[] names) {
        return invokeNoArg(target, names);
    }

    public static Object invokeNoArg(Object target, String[] names) {
        if (target == null || names == null) return null;
        Class<?> type = target.getClass();
        Map<String, Method> methods = NO_ARG.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>());
        for (String name : names) {
            try {
                Method method = methods.get(name);
                if (method == null) {
                    method = findPublicNoArg(type, name);
                    if (method != null) methods.put(name, method);
                }
                if (method != null) return method.invoke(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static float invokeAttributeNumber(Object target, Object attribute, float fallback, String[] names) {
        if (target == null || attribute == null || names == null) return fallback;
        Class<?> type = target.getClass();
        Map<String, Method> methods = ONE_ARG.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>());
        for (String name : names) {
            try {
                String key = name + "#" + attribute.getClass().getName();
                Method method = methods.get(key);
                if (method == null) {
                    for (Method candidate : type.getMethods()) {
                        if (!candidate.getName().equals(name) || candidate.getParameterCount() != 1) continue;
                        if (!candidate.getParameterTypes()[0].isAssignableFrom(attribute.getClass())) continue;
                        candidate.trySetAccessible();
                        method = candidate;
                        methods.put(key, method);
                        break;
                    }
                }
                if (method != null) {
                    Object value = method.invoke(target, attribute);
                    if (value instanceof Number number) return number.floatValue();
                }
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    public static boolean isServerEntity(Object object) {
        if (object instanceof Entity entity) {
            return entity.level() instanceof ServerLevel;
        }
        Object level = invokeNoArg(object, new String[]{"level", "getLevel", "m_9236_"});
        return level instanceof ServerLevel;
    }

    private static Method findPublicNoArg(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            method.trySetAccessible();
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
