package io.nexstudios.compactors.inventory;

import io.nexstudios.compactors.NexCompactors;
import io.nexstudios.compactors.config.CompactorConfig;
import io.nexstudios.compactors.register.CompactorRegistry;
import io.nexstudios.nexus.bukkit.inv.api.NexFillerEntry;
import io.nexstudios.nexus.bukkit.inv.api.NexMenuSession;
import io.nexstudios.nexus.bukkit.inv.fill.InvAlignment;
import io.nexstudios.nexus.bukkit.utils.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        int endSlot = 33;

        List<ItemStack> items = new ArrayList<>(compactors.size());
        for (CompactorConfig cfg : compactors) {
            items.add(buildCategoryItem(cfg, player));
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