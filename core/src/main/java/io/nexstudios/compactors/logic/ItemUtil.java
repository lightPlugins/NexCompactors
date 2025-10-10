package io.nexstudios.compactors.logic;

import io.nexstudios.compactors.config.RequiredConfig;
import io.nexstudios.nexus.bukkit.utils.StringUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ItemUtil {
    private ItemUtil() {}

    public static ItemStack parseItem(String key, int amount) {
        // Using ItemBuilder from Nexus
        ItemStack stack = StringUtils.parseItem(key);
        stack.setAmount(Math.max(1, amount));
        return stack;
    }

    public static boolean matchesKey(ItemStack stack, String namespaced) {
        Material mat = stack.getType();
        // Accept both namespaced (minecraft:stone) and plain material name
        try {
            NamespacedKey k = NamespacedKey.fromString(namespaced);
            if (k != null && "minecraft".equals(k.getNamespace())) {
                Material m = Material.matchMaterial(k.getKey().toUpperCase());
                return m != null && m == mat;
            }
        } catch (Exception ignored) { }
        Material plain = Material.matchMaterial(namespaced.toUpperCase());
        return plain != null && plain == mat;
    }

    public static int countMatching(Inventory inv, RequiredConfig req) {
        if (inv == null || req == null) return 0;
        int total = 0;
        for (ItemStack s : inv.getContents()) {
            if (s == null || s.getType() == Material.AIR) continue;
            if (ItemMatcher.matches(s, req)) {
                total += s.getAmount(); // wichtig: getAmount()
            }
        }
        return total;
    }

    public static int removeMatching(Inventory inv, RequiredConfig req, int toRemove) {
        if (inv == null || req == null || toRemove <= 0) return 0;
        int removed = 0;

        for (int slot = 0; slot < inv.getSize() && removed < toRemove; slot++) {
            ItemStack s = inv.getItem(slot);
            if (s == null || s.getType() == Material.AIR) continue;
            if (!ItemMatcher.matches(s, req)) continue;

            int canTake = Math.min(toRemove - removed, s.getAmount());
            int remain = s.getAmount() - canTake;

            if (remain > 0) {
                s.setAmount(remain);
                inv.setItem(slot, s);
            } else {
                inv.clear(slot);
            }
            removed += canTake;
        }
        return removed;
    }

    public static int computeMaxCrafts(Inventory inv, RequiredConfig req, int requiredPerCraft) {
        if (inv == null || req == null || requiredPerCraft <= 0) return 0;
        int total = countMatching(inv, req);
        return total / requiredPerCraft;
    }

    /**
     * Prüft, ob 'totalAmount' eines bestimmten Prototyp-Items vollständig ins Inventar passt.
     * Berücksichtigt Auffüllen bestehender Stacks und freie Slots.
     */
    public static boolean canFitCompletely(Inventory inv, ItemStack proto, long totalAmount) {
        if (inv == null || proto == null || proto.getType() == Material.AIR || totalAmount <= 0) return true;

        int maxStack = Math.max(1, proto.getMaxStackSize());
        long canFit = 0;

        // 1) vorhandene gleichartige Stacks auffüllen
        for (ItemStack s : inv.getContents()) {
            if (s == null || s.getType() == Material.AIR) continue;
            if (!s.isSimilar(proto)) continue;
            canFit += Math.max(0, maxStack - s.getAmount());
            if (canFit >= totalAmount) return true;
        }

        // 2) freie Slots
        for (ItemStack s : inv.getContents()) {
            if (s == null || s.getType() == Material.AIR) {
                canFit += maxStack;
                if (canFit >= totalAmount) return true;
            }
        }

        return false;
    }

    /**
     * Entfernt die für 'crafts' benötigte Gesamtmenge. Overflow-sicher und ohne "immer false"-Zweig.
     */
    public static boolean consumeForCrafts(Inventory inv, RequiredConfig req, int requiredPerCraft, int crafts) {
        if (inv == null || req == null || requiredPerCraft <= 0 || crafts <= 0) return false;

        long need = (long) requiredPerCraft * (long) crafts;
        while (need > 0) {
            int chunk = (int) Math.min(Integer.MAX_VALUE, need);
            int removed = removeMatching(inv, req, chunk);
            if (removed < chunk) {
                return false; // nicht genug entfernt -> Abbruch
            }
            need -= removed;
        }
        return true;
    }


}