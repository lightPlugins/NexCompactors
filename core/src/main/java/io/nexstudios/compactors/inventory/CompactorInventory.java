package io.nexstudios.compactors.inventory;

import io.nexstudios.compactors.NexCompactors;
import io.nexstudios.compactors.config.CompactorConfig;
import io.nexstudios.compactors.config.RecipeConfig;
import io.nexstudios.compactors.logic.CompactorManager;
import io.nexstudios.compactors.logic.ItemUtil;
import io.nexstudios.nexus.bukkit.inv.api.NexFillerEntry;
import io.nexstudios.nexus.bukkit.inv.api.NexMenuSession;
import io.nexstudios.nexus.bukkit.inv.fill.InvAlignment;
import io.nexstudios.nexus.bukkit.items.ItemBuilder;
import io.nexstudios.nexus.bukkit.items.ItemHideFlag;
import io.nexstudios.nexus.bukkit.platform.NexServices;
import io.nexstudios.nexus.bukkit.utils.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public final class CompactorInventory {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public CompactorInventory() {}

    /**
     * Öffnet das Compactor-Inventar für einen Spieler.
     * Neuer API-Fluss:
     * 1) Session + populateFiller(...) -> Binding
     * 2) Einträge (NexFillerEntry) mit Click-Handlern registrieren
     * 3) populateFillerEntries(...).openFor(player)
     */
    public void openInventory(Player player, Optional<CompactorConfig> compactorConfigOpt) {
        if (compactorConfigOpt.isEmpty()) {
            player.sendMessage(Component.text("No Compactor found."));
            return;
        }
        CompactorConfig cfg = compactorConfigOpt.get();

        // Manager holen (für States & Toggle)
        CompactorManager manager = NexCompactors.getInstance().getCompactorRegistry().getCompactorManager();

        // States laden (cached oder DB) und danach GUI bauen/öffnen
        manager.loadPlayerStates(player.getUniqueId(), cfg.getId()).thenAccept(states -> {
            if (states == null) states = Collections.emptyMap();
            Map<String, Boolean> stateMap = new HashMap<>(states); // lokal veränderbar

            // Sync zurück zum Main-Thread für Inventar-Aufbau
            Bukkit.getScheduler().runTask(NexCompactors.getInstance(), () -> {
                // Alle aktivierten Rezepte laut Config (nicht Spieler-Status)
                List<RecipeConfig> allRecipes = cfg.getRecipes().stream()
                        .filter(RecipeConfig::isEnabled)
                        .toList();

                if (allRecipes.isEmpty()) {
                    player.sendMessage(Component.text("No recipes configured."));
                    return;
                }

                // Standard-Layout
                int startSlot = 11;
                int endSlot = 33;

                // Lore/Name-Vorlagen (Defaults)
                String displayNameTpl = "<#ffdc73><recipe> <dark_gray>Recipe";
                List<String> loreTpl = List.of(
                        " ",
                        "<gray>Compactor Recipe",
                        "<gray>Converts <yellow>#required-amount#<gray>x <yellow>#required-item#",
                        "<gray>into <yellow>#result-amount#<gray>x <yellow>#result-item#.",
                        " ",
                        "<gray>Current Status: #status#",
                        " ",
                        "#info#"
                );
                String infoUnlocked = "<yellow>Left click to toggle status";
                String infoLocked = "<red>You need to unlock this compactor";
                String statusEnabled = "<green><bold>ENABLED<reset>";
                String statusDisabled = "<red><bold>DISABLED<reset>";

                // Filler-Items als List<ItemStack>, in derselben Reihenfolge wie recipesForBody
                List<RecipeConfig> recipesForBody = new ArrayList<>();
                List<ItemStack> fillerItems = new ArrayList<>();

                for (RecipeConfig r : allRecipes) {
                    boolean hasPerms = hasAllPermissions(player, r.getPermissions());
                    boolean isEnabledForPlayer = stateMap.getOrDefault(r.getId(), false) && hasPerms;

                    // Required/Result Werte
                    String reqKey = r.getRequired().getItem();
                    int reqAmount = r.getRequired().getAmount();
                    String resKey = r.getResult().getItem();
                    int resAmount = r.getResult().getAmount();

                    ItemBuilder requiredItem = NexServices.newItemBuilder().itemStack(StringUtils.parseItem(reqKey));
                    String reqRenderName = PlainTextComponentSerializer.plainText().serialize(
                            GlobalTranslator.render(requiredItem.build().displayName(), player.locale()));
                    String reqDisplayName = requiredItem.hasCustomName() ?
                            PlainTextComponentSerializer.plainText().serialize(requiredItem.build().displayName())
                            : reqRenderName;

                    ItemBuilder resultItem = NexServices.newItemBuilder().itemStack(StringUtils.parseItem(resKey));
                    String resRenderName = PlainTextComponentSerializer.plainText().serialize(
                            GlobalTranslator.render(resultItem.build().displayName(), player.locale()));
                    String resDisplayName = resultItem.hasCustomName() ?
                            PlainTextComponentSerializer.plainText().serialize(resultItem.build().displayName())
                            : resRenderName;

                    // Status + Info abhängig von Permission/State
                    String statusStr = isEnabledForPlayer ? statusEnabled : statusDisabled;
                    String infoStr = hasPerms ? infoUnlocked : infoLocked;

                    // Platzhalter in Lore ersetzen (#...#) und MiniMessage parsen
                    List<Component> lore = loreTpl.stream()
                            .map(line -> replaceHashes(line, reqAmount, reqDisplayName, resAmount, resDisplayName, statusStr, infoStr))
                            .map(line -> MM.deserialize(line, TagResolver.empty()).decoration(TextDecoration.ITALIC, false))
                            .collect(Collectors.toList());

                    // Displayname via TagResolver (<recipe>)
                    TagResolver tags = TagResolver.builder()
                            .resolver(Placeholder.parsed("recipe", r.getName()))
                            .build();
                    Component displayName = MM.deserialize(displayNameTpl, tags).decoration(TextDecoration.ITALIC, false);

                    // Filler-Item als required.item mit required.amount bauen
                    ItemStack baseItem = ItemUtil.parseItem(resKey, resAmount);

                    // Baue das Anzeige-Item über NexServices-Builder; verstecke Enchants immer in Lore
                    var builder = NexServices.newItemBuilder()
                            .itemStack(baseItem)
                            .amount(Math.max(1, reqAmount))
                            .displayName(displayName)
                            .lore(lore)
                            .hideFlags(Set.of(ItemHideFlag.HIDE_ENCHANTS));

                    if (isEnabledForPlayer) {
                        builder.enchantments(Map.of(Enchantment.FORTUNE, 1)); // Glow
                    }
                    ItemStack displayItem = builder.build();

                    recipesForBody.add(r);
                    fillerItems.add(displayItem);
                }

                // 1) Session erstellen
                NexMenuSession session = NexCompactors.getInstance()
                        .getInvService()
                        .menu("nexcompactors", "compactor");

                // 2) Filler-Binding mit initialen Items
                NexMenuSession.FillerBinding binding = session.populateFiller(
                        fillerItems, startSlot, endSlot, InvAlignment.LEFT
                );

                // 3) Einträge mit per-Item Click-Handlern anlegen
                List<NexFillerEntry> entries = new ArrayList<>();
                for (int i = 0; i < recipesForBody.size(); i++) {
                    final int index = i;
                    final RecipeConfig recipe = recipesForBody.get(i);
                    final ItemStack initialItem = fillerItems.get(i);

                    entries.add(NexFillerEntry.of(initialItem, (event, ctx) -> {
                        Integer bodyIdx = ctx.bodyIndex();
                        if (bodyIdx == null) return;
                        if (bodyIdx != index) return; // Sicherheit: nur korrekter Slot

                        // Permission-AND prüfen
                        if (!hasAllPermissions(player, recipe.getPermissions())) {
                            NexCompactors.getInstance().getMessageSender().send(player, "compactors.not-allowed-recipe");
                            return;
                        }

                        boolean current = stateMap.getOrDefault(recipe.getId(), false);
                        boolean next = !current;

                        // Persistiere Toggle
                        manager.setPlayerRecipeState(player.getUniqueId(), cfg.getId(), recipe.getId(), next)
                                .thenRun(() -> Bukkit.getScheduler().runTask(NexCompactors.getInstance(), () -> {
                                    // Lokal aktualisieren
                                    stateMap.put(recipe.getId(), next);

                                    // Anzeige-Texte je nach neuem Zustand
                                    String reqKey = recipe.getRequired().getItem();
                                    int reqAmount = recipe.getRequired().getAmount();
                                    String resKey = recipe.getResult().getItem();
                                    int resAmount = recipe.getResult().getAmount();

                                    ItemBuilder requiredItem = NexServices.newItemBuilder().itemStack(StringUtils.parseItem(reqKey));
                                    String reqRenderName = PlainTextComponentSerializer.plainText().serialize(
                                            GlobalTranslator.render(requiredItem.build().displayName(), player.locale()));
                                    String reqDisplayName = requiredItem.hasCustomName() ?
                                            PlainTextComponentSerializer.plainText().serialize(requiredItem.build().displayName())
                                            : reqRenderName;

                                    ItemBuilder resultItem = NexServices.newItemBuilder().itemStack(StringUtils.parseItem(resKey));
                                    String resRenderName = PlainTextComponentSerializer.plainText().serialize(
                                            GlobalTranslator.render(resultItem.build().displayName(), player.locale()));
                                    String resDisplayName = resultItem.hasCustomName() ?
                                            PlainTextComponentSerializer.plainText().serialize(resultItem.build().displayName())
                                            : resRenderName;

                                    String statusStr = next ? statusEnabled : statusDisabled;
                                    // hier bereits geklickt, also entsperrt

                                    // Lore korrekt als List<Component> neu aufbauen
                                    List<Component> lore = loreTpl.stream()
                                            .map(line -> replaceHashes(line, reqAmount, reqDisplayName, resAmount, resDisplayName, statusStr, infoUnlocked))
                                            .map(line -> MM.deserialize(line, TagResolver.empty()))
                                            .collect(Collectors.toList());

                                    TagResolver tags = TagResolver.builder()
                                            .resolver(Placeholder.parsed("recipe", recipe.getName()))
                                            .build();
                                    Component displayName = MM.deserialize(displayNameTpl, tags);

                                    // Frisches Basis-Item OHNE Verzauberungen, damit der Glow sicher verschwindet
                                    ItemStack freshBase = ItemUtil.parseItem(resKey, resAmount);

                                    binding.update(bodyIdx, ignored -> {
                                        var b = NexServices.newItemBuilder()
                                                .itemStack(freshBase)
                                                .amount(Math.max(1, reqAmount))
                                                .displayName(displayName)
                                                .lore(lore)
                                                .hideFlags(Set.of(ItemHideFlag.HIDE_ENCHANTS));
                                        if (next) {
                                            b.enchantments(Map.of(Enchantment.FORTUNE, 1)); // Glow an
                                        }
                                        // Wenn disabled -> keine Enchants setzen => Glow aus
                                        return b.build();
                                    });

                                    // Optional Spieler-Feedback (Language)
                                    TagResolver tr = TagResolver.resolver(
                                            Placeholder.unparsed("recipe", recipe.getId()),
                                            Placeholder.unparsed("state", next ? "enabled" : "disabled"));
                                    NexCompactors.getInstance().getMessageSender().send(player, "compactors.toggle-recipe", tr);
                                }));
                    }));
                }

                // title replacements (Compactor name)
                TagResolver tags = TagResolver.resolver(
                        Placeholder.unparsed("compactor", cfg.getName())
                );

                // 4) Einträge rendern und öffnen
                session.populateFillerEntries(entries, startSlot, endSlot, InvAlignment.LEFT)
                        .withTitleTags(tags)
                        .openFor(player);
            });
        });
    }

    public void openInv(Player player, Optional<CompactorConfig> compactorConfigOpt) {
        // Alias zu openInventory für evtl. bestehende Aufrufer
        openInventory(player, compactorConfigOpt);
    }

    private boolean hasAllPermissions(Player player, List<String> perms) {
        if (perms == null || perms.isEmpty()) return true;
        for (String p : perms) {
            if (p == null || p.isBlank()) continue;
            if (!player.hasPermission(p)) return false;
        }
        return true;
    }

    /**
     * Ersetzt die #...# Platzhalter im String und gibt die MiniMessage-kompatible Zeile zurück.
     * Ersetzt:
     * - #required-amount#
     * - #required-item#
     * - #result-amount#
     * - #result-item#
     * - #status#
     * - #info#
     */
    private String replaceHashes(String line,
                                 int reqAmount, String reqItem,
                                 int resAmount, String resItem,
                                 String status, String info) {
        if (line == null) return "";
        return line
                .replace("#required-amount#", String.valueOf(reqAmount))
                .replace("#required-item#", reqItem)
                .replace("#result-amount#", String.valueOf(resAmount))
                .replace("#result-item#", resItem)
                .replace("#status#", status)
                .replace("#info#", info);
    }
}