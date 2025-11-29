package io.nexstudios.compactors.inventory;

import io.nexstudios.compactors.NexCompactors;
import io.nexstudios.compactors.config.CompactorConfig;
import io.nexstudios.compactors.register.CompactorRegistry;
import io.nexstudios.nexus.bukkit.inv.api.NexFillerEntry;
import io.nexstudios.nexus.bukkit.inv.api.NexMenuSession;
import io.nexstudios.nexus.bukkit.inv.fill.InvAlignment;
import io.nexstudios.nexus.bukkit.items.ItemHideFlag;
import io.nexstudios.nexus.bukkit.platform.NexServices;
import io.nexstudios.nexus.bukkit.utils.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public class CategoryInventory {

    public CategoryInventory() {}

    public void openInventory(Player player) {
        CompactorRegistry registry = NexCompactors.getInstance().getCompactorRegistry();
        List<CompactorConfig> compactors = new ArrayList<>(registry.getCompactors().values());
        compactors.removeIf(cfg -> cfg == null || !cfg.isEnabled());
        compactors.sort(Comparator
                .comparing(CompactorConfig::getName, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(CompactorConfig::getId));

        if (compactors.isEmpty()) {
            NexCompactors.nexusLogger.warning("Currently no compactors are registered.");
            NexCompactors.nexusLogger.warning("Create a new compactor configuration to start.");
            return;
        }

        int startSlot = 11;
        int endSlot = 35;

        // Inventar-Datei "category.yml" suchen und Filler-Slots lesen
        File file = NexCompactors.getInstance()
                .getInventoryFiles()
                .getFiles()
                .stream()
                .filter(mapFile -> mapFile.getName().equalsIgnoreCase("category.yml"))
                .findFirst()
                .orElse(null);

        if (file != null) {
            YamlConfiguration invConfig = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection fillerSection = invConfig.getConfigurationSection("content.extra-settings.filler");
            if (fillerSection != null) {
                startSlot = fillerSection.getInt("start-slot", startSlot);
                endSlot = fillerSection.getInt("end-slot", endSlot);
            }
        }

        List<ItemStack> items = new ArrayList<>(compactors.size());
        for (CompactorConfig cfg : compactors) {
            ItemStack is = buildCategoryItem(cfg, player);
            var builder = NexServices.newItemBuilder()
                    .itemStack(is)
                    .hideFlags(Set.of(ItemHideFlag.HIDE_ENCHANTS, ItemHideFlag.HIDE_ATTRIBUTES));

            items.add(builder.build());
        }

        NexMenuSession session = NexCompactors.getInstance()
                .getInvService()
                .menu("nexcompactors", "category");

        session.populateFiller(items, startSlot, endSlot, InvAlignment.LEFT);

        List<NexFillerEntry> entries = new ArrayList<>(compactors.size());
        for (int i = 0; i < compactors.size(); i++) {
            CompactorConfig cfg = compactors.get(i);
            ItemStack item = items.get(i);
            entries.add(NexFillerEntry.of(item, (event, ctx) -> {
                new CompactorInventory().openInventory(player, Optional.of(cfg));
            }));
        }

        session.populateFillerEntries(entries, startSlot, endSlot, InvAlignment.LEFT)
                .openFor(player);
    }

    private ItemStack buildCategoryItem(CompactorConfig cfg, Player player) {
        Map<String, Object> params = cfg.getCategoryItem();
        TagResolver resolver = TagResolver.resolver(
                Placeholder.unparsed("compactor_id", cfg.getId()),
                Placeholder.unparsed("compactor", cfg.getName() != null ? cfg.getName() : cfg.getId())
        );
        return StringUtils.parseConfigItem(params, resolver, player);
    }
}