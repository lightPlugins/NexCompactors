package io.nexstudios.compactors.logic;

import io.nexstudios.compactors.config.MatcherConfig;
import io.nexstudios.compactors.config.RequiredConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemMatcher {
    private ItemMatcher() {}

    // Kleiner LRU-Cache für Prototypen, um parseItem(...) nicht ständig aufzurufen.
    // Key = Rezept-Item-String (z. B. "minecraft:stone" oder "mmoitems:...").
    private static final int PROTO_CACHE_MAX = 128;
    private static final Map<String, ItemStack> PROTO_CACHE = new LinkedHashMap<>(PROTO_CACHE_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
            return size() > PROTO_CACHE_MAX;
        }
    };

    static ItemStack getOrBuildProto(String key) {
        synchronized (PROTO_CACHE) {
            ItemStack cached = PROTO_CACHE.get(key);
            if (cached != null && cached.getType() != Material.AIR) return cached;
            ItemStack built = ItemUtil.parseItem(key, 1);
            // parseItem sollte nie null liefern; falls doch, schützen:
            if (built.getType() == Material.AIR) {
                // speichere nicht-AIR nicht; gib AIR zurück
                return ItemStack.of(Material.AIR);
            }
            // Amount 1 reicht; isSimilar ignoriert ohnehin die Amount.
            PROTO_CACHE.put(key, built);
            return built;
        }
    }

    public static boolean matches(ItemStack stack, RequiredConfig req) {
        if (stack == null || stack.getType() == Material.AIR) return false;

        // Standard (kein matcher): direkter Prototyp-/isSimilar-Vergleich, Amount wird ignoriert
        if (req.getMatcher() == null) {
            return baseMatch(stack, req.getItem());
        }

        MatcherConfig m = req.getMatcher();
        String type = m.getType();
        if ("MATERIAL".equalsIgnoreCase(type)) {
            // explizit nur Material
            return ItemUtil.matchesKey(stack, req.getItem());
        }

        // Für alle anderen Typen: erst Basismatch (minecraft => Material, sonst => Prototyp/isSimilar)
        boolean baseOk = baseMatchWithNamespacePreference(stack, req.getItem());
        if (!baseOk) return false;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;

        switch (type.toUpperCase()) {
            case "NAME": {
                String plain = getPlainDisplayName(meta);
                String expected = m.getName();
                return plain != null && plain.equals(expected);
            }
            case "LORE": {
                List<String> lore = meta.hasLore() ? meta.getLore() : null;
                if (lore == null) return false;
                for (String needle : m.getLoreContains()) {
                    boolean found = lore.stream().anyMatch(line -> line != null && line.contains(needle));
                    if (!found) return false;
                }
                return true;
            }
            case "CUSTOM_MODEL_DATA": {
                Integer cmd = m.getCustomModelData();
                return cmd != null && meta.hasCustomModelData() && meta.getCustomModelData() == cmd;
            }
            case "COMBINED": {
                if (m.getName() != null) {
                    String dn = getPlainDisplayName(meta);
                    if (dn == null || !dn.equals(m.getName())) return false;
                }
                if (m.getLoreContains() != null && !m.getLoreContains().isEmpty()) {
                    List<String> lore = meta.hasLore() ? meta.getLore() : null;
                    if (lore == null) return false;
                    for (String needle : m.getLoreContains()) {
                        boolean found = lore.stream().anyMatch(line -> line != null && line.contains(needle));
                        if (!found) return false;
                    }
                }
                if (m.getCustomModelData() != null) {
                    if (!meta.hasCustomModelData() || meta.getCustomModelData() != m.getCustomModelData()) return false;
                }
                // NBT: Platzhalter
                return true;
            }
            case "NBT": {
                // Platzhalter – aktuell Basismatch ausreichend
                return true;
            }
            default:
                return true;
        }
    }

    // minecraft:* oder plain Material => Materialvergleich; sonst Prototyp/isSimilar
    private static boolean baseMatchWithNamespacePreference(ItemStack stack, String key) {
        NamespacedKey nk = NamespacedKey.fromString(key);
        boolean isMinecraftNs = nk != null && "minecraft".equals(nk.getNamespace());
        if (isMinecraftNs || isPlainMaterial(key)) {
            return ItemUtil.matchesKey(stack, key);
        }
        return baseMatch(stack, key);
    }

    // Prototypenvergleich; Amount wird ignoriert, kein Clone nötig
    private static boolean baseMatch(ItemStack stack, String key) {
        ItemStack proto = getOrBuildProto(key);
        if (proto.getType() == Material.AIR) return false;
        return stack.isSimilar(proto);
    }

    private static boolean isPlainMaterial(String key) {
        try {
            return org.bukkit.Material.matchMaterial(key.toUpperCase()) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    // Robust gegen Adventure-Components und Lokalisierungen (serverseitig EN als Fallback)
    private static String getPlainDisplayName(ItemMeta meta) {
        try {
            Component c = meta.displayName(); // Paper API
            if (c == null) return null;
            Component rendered = GlobalTranslator.render(c, Locale.ENGLISH);
            return PlainTextComponentSerializer.plainText().serialize(rendered);
        } catch (Throwable ignored) {
            if (meta.hasDisplayName()) {
                return meta.getDisplayName();
            }
            return null;
        }
    }
}