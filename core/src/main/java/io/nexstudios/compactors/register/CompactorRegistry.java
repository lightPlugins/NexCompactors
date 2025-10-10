package io.nexstudios.compactors.register;

import io.nexstudios.compactors.NexCompactors;
import io.nexstudios.compactors.command.CompactorCommandRegistrar;
import io.nexstudios.compactors.config.CompactorConfig;
import io.nexstudios.compactors.config.CompactorFileReader;
import io.nexstudios.compactors.database.CompactorPlayerCache;
import io.nexstudios.compactors.database.CompactorPlayerStateDao;
import io.nexstudios.compactors.logic.CompactorManager;
import io.nexstudios.compactors.logic.CompactorPlayerListener;
import io.nexstudios.compactors.logic.CompactorTriggerListener;
import io.nexstudios.nexus.bukkit.database.api.DbAsyncHelper;
import io.nexstudios.nexus.bukkit.database.api.NexusDatabaseService;
import io.nexstudios.nexus.bukkit.utils.NexusLogger;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class CompactorRegistry {

    private final NexCompactors plugin;
    private final NexusLogger logger;

    private final Map<String, CompactorConfig> compactors = new ConcurrentHashMap<>(); // key = compactorId (file name)
    private CompactorManager compactorManager;
    private CompactorTriggerListener triggerListener;
    private CompactorPlayerListener playerListener;
    private CompactorCommandRegistrar commandRegistrar;

    private NexusDatabaseService db;
    private DbAsyncHelper dbHelper;
    private CompactorPlayerStateDao playerStateDao;

    // Persistenter Cache über Reloads hinweg
    private final CompactorPlayerCache playerCache = new CompactorPlayerCache();

    public CompactorRegistry(NexCompactors plugin) {
        this.plugin = plugin;
        this.logger = NexCompactors.nexusLogger;
    }

    public void loadAndRegisterAll() {
        if (!initDatabase()) {
            logger.warning("Compactors disabled due to database initialization failure.");
            return;
        }
        loadFiles();
        initServicesAndEvents();
        registerCommands();
        warmupOnlinePlayers();
        logger.info("Compactors initialized: " + compactors.keySet());
    }

    public void reload() {
        if (db == null) return; // not initialized yet
        unloadCommandsAndEvents();
        compactors.clear();
        loadFiles();
        initServicesAndEvents();
        registerCommands();
        warmupOnlinePlayers();
        logger.info("Compactors reloaded: " + compactors.keySet());
    }

    public void shutdown() {
        // Absichtlich den Cache NICHT leeren (bis Server-Stopp)
        unloadCommandsAndEvents();
        compactors.clear();
        if (dbHelper != null) {
            dbHelper.shutdown();
        }
    }

    private boolean initDatabase() {
        var reg = plugin.getServer().getServicesManager().getRegistration(NexusDatabaseService.class);
        if (reg == null) {
            logger.error("Could not find NexusDatabaseService. Compactors will be disabled.");
            return false;
        }
        this.db = reg.getProvider();
        if (!db.isHealthy()) {
            logger.error("Database unhealthy for Compactors. Feature disabled.");
            return false;
        }
        try {
            db.isHealthy();
        } catch (Exception e) {
            logger.error("Database health check failed for Compactors: " + e.getMessage());
            return false;
        }
        this.dbHelper = new DbAsyncHelper(db);
        this.playerStateDao = new CompactorPlayerStateDao(dbHelper, logger);
        CompletableFuture<Void> schema = playerStateDao.initSchemaAsync();
        try {
            schema.join();
        } catch (Exception ex) {
            logger.error("Compactors schema initialization failed: " + ex.getMessage());
            return false;
        }
        return true;
    }

    private void loadFiles() {
        var reader = new CompactorFileReader(plugin, logger);
        List<CompactorConfig> configs = reader.readAll();
        Set<String> seen = new HashSet<>();
        for (CompactorConfig cfg : configs) {
            String id = cfg.getId();
            if (seen.contains(id)) {
                logger.warning("Duplicate compactor id '" + id + "' detected. Skipping this file.");
                continue;
            }
            seen.add(id);
            compactors.put(id, cfg);
        }
    }

    private void initServicesAndEvents() {
        if (playerStateDao == null) {
            logger.warning("Compactors: database not ready, skipping initialization.");
            return;
        }
        this.compactorManager = new CompactorManager(plugin, logger, compactors, playerStateDao, playerCache);
        this.triggerListener = new CompactorTriggerListener(plugin, compactorManager);
        this.playerListener = new CompactorPlayerListener(compactorManager);
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(triggerListener, plugin);
        pm.registerEvents(playerListener, plugin);
    }

    private void registerCommands() {
        if (compactorManager == null) return;
        this.commandRegistrar = new CompactorCommandRegistrar(plugin.getCommandManager(), compactorManager, logger);
        commandRegistrar.register(); // Root-Command im ACF-Stil
    }

    private void unloadCommandsAndEvents() {
        if (commandRegistrar != null) {
            commandRegistrar.unregisterAll();
            commandRegistrar = null;
        }
        if (triggerListener != null) {
            PlayerUnregisterUtil.unregisterListener(triggerListener);
            triggerListener = null;
        }
        if (playerListener != null) {
            PlayerUnregisterUtil.unregisterListener(playerListener);
            playerListener = null;
        }
    }

    // Nach (Re-)Load Online-Spieler vorwärmen, damit der Cache sofort gefüllt ist.
    private void warmupOnlinePlayers() {
        if (compactorManager == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            compactorManager.warmupPlayer(p);
        }
    }
}