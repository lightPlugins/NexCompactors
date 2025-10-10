package io.nexstudios.compactors.command;

import io.nexstudios.compactors.config.CompactorCommandEntry;
import io.nexstudios.compactors.config.CompactorConfig;
import io.nexstudios.compactors.config.RecipeConfig;
import io.nexstudios.compactors.logic.CompactorManager;
import io.nexstudios.nexus.bukkit.utils.NexusLogger;
import io.nexstudios.nexus.libs.commands.PaperCommandManager;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class CompactorCommandRegistrar {

    private final PaperCommandManager cmdManager;
    private final CompactorManager manager;
    private final NexusLogger logger;

    private final AtomicBoolean registered = new AtomicBoolean(false);
    private CompactorRootCommand rootCommand;

    public void register() {
        if (registered.get()) return;

        // Completions: kompakt und API-agnostisch
        cmdManager.getCommandCompletions().registerAsyncCompletion("compactor_ids", ctx ->
                manager.getAllCompactors().stream()
                        .map(CompactorConfig::getId)
                        .sorted()
                        .toList()
        );

        // Statt "recipes_for" (abhängig von vorherigem Arg) nutzen wir eine globale "recipes"-Completion.
        // Die Validierung auf den konkreten Compactor passiert in der Command-Logik.
        cmdManager.getCommandCompletions().registerAsyncCompletion("recipes", ctx ->
                manager.getAllCompactors().stream()
                        .flatMap(cfg -> manager.getRecipesInScope(
                                cfg,
                                new CompactorCommandEntry.Scope("all", List.of())
                        ).stream())
                        .map(RecipeConfig::getId)
                        .distinct()
                        .sorted()
                        .toList()
        );

        cmdManager.getCommandCompletions().registerCompletion("enable_disable", ctx ->
                List.of("enable", "disable")
        );

        // Root command
        rootCommand = new CompactorRootCommand(manager);
        cmdManager.registerCommand(rootCommand);
        registered.set(true);
    }

    public void unregisterAll() {
        if (!registered.get()) return;
        cmdManager.unregisterCommand(rootCommand);
        registered.set(false);
    }
}