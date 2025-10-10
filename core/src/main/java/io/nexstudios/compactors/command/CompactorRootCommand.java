package io.nexstudios.compactors.command;

import io.nexstudios.compactors.NexCompactors;
import io.nexstudios.compactors.config.CompactorCommandEntry;
import io.nexstudios.compactors.config.RecipeConfig;
import io.nexstudios.compactors.inventory.CompactorInventory;
import io.nexstudios.compactors.logic.CompactorManager;
import io.nexstudios.nexus.libs.commands.BaseCommand;
import io.nexstudios.nexus.libs.commands.annotation.*;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@CommandAlias("compactor")
@Description("Manage Compactor recipes per player")
public class CompactorRootCommand extends BaseCommand {

    private final CompactorManager manager;

    @Subcommand("list")
    @Description("List recipes for a compactor and your current states")
    @CommandCompletion("@compactor_ids")
    public void onList(CommandSender sender, String compactorId) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only Players can exclude this command!");
            return;
        }
        var opt = manager.getCompactor(compactorId);
        if (opt.isEmpty()) {
            TagResolver tagResolver = Placeholder.parsed("compactor", compactorId);
            NexCompactors.getInstance().getMessageSender().send(sender, "compactors.unknown-compactor", tagResolver);
            return;
        }
        var cfg = opt.get();
        var scopeAll = new CompactorCommandEntry.Scope("all", List.of());
        List<RecipeConfig> inScope = manager.getRecipesInScope(cfg, scopeAll);
        List<RecipeConfig> permitted = inScope.stream().filter(r -> hasAllPermissions(p, r)).toList();
        List<RecipeConfig> locked = inScope.stream().filter(r -> !hasAllPermissions(p, r)).toList();

        manager.loadPlayerStates(p.getUniqueId(), cfg.getId()).thenAccept(states -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Compactor Recipes (").append(cfg.getId()).append("):\n"); // TODO language
            for (RecipeConfig r : permitted) {
                boolean on = states.getOrDefault(r.getId(), false);
                sb.append(on ? "  [ON] " : "  [OFF] ").append(r.getId()).append(" (prio ").append(r.getPriority()).append(")\n");
            }
            for (RecipeConfig r : locked) {
                sb.append("  [LOCKED] ").append(r.getId()).append(" (prio ").append(r.getPriority()).append(")\n");
            }
            p.sendMessage(sb.toString());
        });
    }

    @Subcommand("toggle")
    @Description("Enable or disable a recipe for the current player")
    @CommandCompletion("@compactor_ids @recipes @enable_disable")
    public void onToggle(CommandSender sender, String compactorId, String recipeId, String action) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only Players can execute this command!");
            return;
        }
        var opt = manager.getCompactor(compactorId);
        if (opt.isEmpty()) {
            TagResolver tagResolver = Placeholder.parsed("compactor", compactorId);
            NexCompactors.getInstance().getMessageSender().send(sender, "compactors.unknown-compactor", tagResolver);
            p.sendMessage("Unknown compactor: " + compactorId); // TODO language
            return;
        }
        var cfg = opt.get();
        var scopeAll = new CompactorCommandEntry.Scope("all", List.of());
        Optional<RecipeConfig> rc = manager.getRecipesInScope(cfg, scopeAll).stream()
                .filter(r -> r.getId().equalsIgnoreCase(recipeId))
                .findFirst();
        if (rc.isEmpty()) {
            TagResolver tagResolver = Placeholder.parsed("recipe", recipeId);
            NexCompactors.getInstance().getMessageSender().send(sender, "compactors.unknown-recipe", tagResolver);
            return;
        }
        if (!hasAllPermissions(p, rc.get())) {
            NexCompactors.getInstance().getMessageSender().send(sender, "not-allowed-recipe");
            return;
        }
        boolean enable = action.equalsIgnoreCase("enable");
        if (!enable && !action.equalsIgnoreCase("disable")) {
            TagResolver tagResolver = Placeholder.parsed("usage", "/compactor toggle <compactorId> <recipeId> <enable|disable>");
            NexCompactors.getInstance().getMessageSender().send(sender, "general.wrong-command", tagResolver);
            return;
        }
        manager.setPlayerRecipeState(p.getUniqueId(), cfg.getId(), rc.get().getId(), enable).thenRun(() -> {
            TagResolver tagResolver = TagResolver.resolver(
                    Placeholder.unparsed("recipe", recipeId),
                    Placeholder.unparsed("state", enable ? "enabled" : "disabled")
            );
            NexCompactors.getInstance().getMessageSender().send(sender, "compactors.toggle-recipe", tagResolver);
        });
    }

    @Subcommand("open")
    @Description("Open the compactor inventory")
    @CommandCompletion("@compactor_ids")
    public void onOpen(CommandSender sender, String compactorId) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only Players can exclude this command!");
            return;
        }

        var opt = manager.getCompactor(compactorId);
        if (opt.isEmpty()) {
            TagResolver tagResolver = Placeholder.parsed("compactor", compactorId);
            NexCompactors.getInstance().getMessageSender().send(sender, "compactors.unknown-compactor", tagResolver);
            return;
        }

        CompactorInventory inv = new CompactorInventory();
        //inv.openInventory(p, opt);
        inv.openInv(p, opt);

    }

    private boolean hasAllPermissions(Player p, RecipeConfig r) {
        List<String> perms = r.getPermissions();
        if (perms == null || perms.isEmpty()) return true;
        for (String node : perms) {
            if (node == null || node.isBlank()) continue;
            if (!p.hasPermission(node)) return false;
        }
        return true;
    }
}