package io.nexstudios.compactors.database;

import io.nexstudios.nexus.bukkit.database.api.DbAsyncHelper;
import io.nexstudios.nexus.bukkit.utils.NexusLogger;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class CompactorPlayerStateDao {

    private final DbAsyncHelper db;
    private final NexusLogger logger;

    public CompletableFuture<Void> initSchemaAsync() {
        String sql = """
                CREATE TABLE IF NOT EXISTS nex_compactor_state (
                    player_uuid VARCHAR(36) NOT NULL,
                    compactor_id VARCHAR(128) NOT NULL,
                    recipe_id VARCHAR(128) NOT NULL,
                    enabled BOOLEAN NOT NULL,
                    PRIMARY KEY (player_uuid, compactor_id, recipe_id)
                );
                """;
        return db.updateAsync(sql)
                .exceptionally(ex -> {
                    logger.error("Failed to create nex_compactor_state: " + ex.getMessage());
                    return 0;
                })
                .thenApply(rows -> null);
    }

    public CompletableFuture<Map<String, Boolean>> loadStates(UUID player, String compactorId) {
        String sql = "SELECT recipe_id, enabled FROM nex_compactor_state WHERE player_uuid=? AND compactor_id=?";
        return db.queryAsync(sql, rs -> {
            try {
                // Mapper wird pro Zeile aufgerufen; wir geben hier ein Entry-ähnliches Result zurück
                return Map.entry(rs.getString("recipe_id"), rs.getBoolean("enabled"));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, player.toString(), compactorId).thenApply(list -> {
            Map<String, Boolean> map = new HashMap<>();
            list.forEach(e -> map.put(e.getKey(), e.getValue()));
            return map;
        });
    }

    public CompletableFuture<Void> saveState(UUID player, String compactorId, String recipeId, boolean enabled) {
        String updateSql = "UPDATE nex_compactor_state SET enabled=? WHERE player_uuid=? AND compactor_id=? AND recipe_id=?";
        String insertSql = "INSERT INTO nex_compactor_state (player_uuid, compactor_id, recipe_id, enabled) VALUES (?,?,?,?)";

        return db.updateAsync(updateSql, enabled, player.toString(), compactorId, recipeId)
                .thenCompose(rows -> {
                    if (rows != null && rows > 0) {
                        // explizit als Void typisieren
                        return CompletableFuture.completedFuture(null);
                    }
                    // INSERT-Zweig als CompletableFuture<Void>
                    return db.updateAsync(insertSql, player.toString(), compactorId, recipeId, enabled)
                            .thenAccept(inserted -> { /* no-op */ });
                })
                .whenComplete((ignored, ex) -> {
                    if (ex != null) {
                        logger.error("Failed to save compactor state for player=" + player
                                + ", compactor=" + compactorId
                                + ", recipe=" + recipeId + ": " + ex.getMessage());
                    }
                });
    }

}