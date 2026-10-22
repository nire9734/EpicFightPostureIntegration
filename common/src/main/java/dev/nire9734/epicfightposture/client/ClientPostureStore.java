package dev.nire9734.epicfightposture.client;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ClientPostureStore {
    private static final ConcurrentMap<Integer, Entry> STATES = new ConcurrentHashMap<>();

    public static void update(int entityId, UUID uuid, float current, float max, boolean broken) {
        if (uuid == null || max <= 0.0F) {
            STATES.remove(entityId);
            return;
        }
        STATES.put(entityId, new Entry(uuid, current, max, broken));
    }

    public static void remove(int entityId, UUID uuid) {
        Entry entry = STATES.get(entityId);
        if (entry != null && (uuid == null || uuid.equals(entry.uuid))) {
            STATES.remove(entityId, entry);
        }
    }

    public static Entry get(int entityId, UUID uuid) {
        Entry entry = STATES.get(entityId);
        return entry != null && uuid != null && uuid.equals(entry.uuid) ? entry : null;
    }

    public static void clear() {
        STATES.clear();
    }

    private ClientPostureStore() {}

    public static final class Entry {
        public final UUID uuid;
        public final float current;
        public final float max;
        public final boolean broken;

        Entry(UUID uuid, float current, float max, boolean broken) {
            this.uuid = uuid;
            this.current = current;
            this.max = max;
            this.broken = broken;
        }

        public float percent() {
            if (max <= 0.0F) return 0.0F;
            return Math.max(0.0F, Math.min(1.0F, current / max));
        }
    }
}
