package io.nexstudios.compactors.database;

import lombok.NonNull;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spielerbezogener Cache für Compactor-Recipe-States.
 * Struktur:
 *   playerUUID -> compactorId -> (recipeId -> enabled)
 *
 * Lebenszeit: bis Server-Stopp (kein Evict bei Quit).
 */
public final class CompactorPlayerCache {

    // player -> (compactorId -> states)
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>>> store = new ConcurrentHashMap<>();

    public Map<String, Boolean> getStates(UUID player, String compactorId) {
        var compactorMap = store.get(player);
        if (compactorMap == null) return null;
        var map = compactorMap.get(compactorId);
        return map == null ? null : Collections.unmodifiableMap(map);
    }

    public void putStates(UUID player, String compactorId, @NonNull Map<String, Boolean> states) {
        store.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(compactorId, new ConcurrentHashMap<>(states));
    }

    public void putState(UUID player, String compactorId, String recipeId, boolean enabled) {
        store.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(compactorId, k -> new ConcurrentHashMap<>())
                .put(recipeId, enabled);
    }

    public void clearAll() {
        store.clear();
    }
}