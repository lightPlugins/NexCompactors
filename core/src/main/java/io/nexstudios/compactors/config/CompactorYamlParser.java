package io.nexstudios.compactors.config;

import io.nexstudios.nexus.bukkit.utils.NexusLogger;
import io.nexstudios.nexus.bukkit.utils.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public final class CompactorYamlParser {

    private CompactorYamlParser() {}

    public static CompactorConfig parse(String id, FileConfiguration cfg, NexusLogger logger) {
        boolean enabled = cfg.getBoolean("enabled", true);
        String name = cfg.getString("name", id);

        //Category Item (nexus item config section)
        var categoryItemSec = cfg.getConfigurationSection("category-item");
        Map<String, Object> categoryItem = categoryItemSec != null
                ? categoryItemSec.getValues(true)
                : Collections.emptyMap();


        // Commands
        var commandsSec = cfg.getConfigurationSection("commands");
        CompactorCommandConfig commandConfig;
        if (commandsSec != null && commandsSec.getBoolean("enabled", true)) {
            List<CompactorCommandEntry> entries = new ArrayList<>();
            var list = commandsSec.getList("compactors");
            if (list instanceof List<?> rawList) {
                for (Object o : rawList) {
                    if (o instanceof Map<?, ?> map) {
                        entries.add(CompactorCommandEntry.fromMap((Map<Object, Object>) map));
                    }
                }
            }
            commandConfig = new CompactorCommandConfig(true, entries);
        } else {
            commandConfig = new CompactorCommandConfig(false, Collections.emptyList());
        }

        // Chaining
        var chainSec = cfg.getConfigurationSection("chaining");
        boolean chainingEnabled = chainSec == null || chainSec.getBoolean("enabled", true);
        int maxCrafts = chainSec == null ? 1024 : chainSec.getInt("max-crafts-per-cycle", 1024);

        // Triggers
        var trig = cfg.getConfigurationSection("triggers");
        CompactorTriggers triggers = new CompactorTriggers(
                trig == null || trig.getBoolean("on-pickup", true),
                trig == null || trig.getBoolean("on-autopickup", true),
                trig == null || trig.getBoolean("on-add-item", true),
                trig == null ? 1 : trig.getInt("delay-ticks", 1)
        );

        // Feedback
        ConfigurationSection fb = cfg.getConfigurationSection("feedback.on-no-space");
        CompactorFeedback feedback = new CompactorFeedback(
                fb == null ? "&cNot enough space in your inventory! Compaction aborted." : fb.getString("chat", "&cNot enough space in your inventory! Compaction aborted."),
                fb != null && fb.getConfigurationSection("sound") != null && fb.getBoolean("sound.enabled", true),
                fb == null ? "ENTITY_VILLAGER_NO" : fb.getString("sound.type", "ENTITY_VILLAGER_NO"),
                fb == null ? 1.0f : (float) fb.getDouble("sound.volume", 1.0),
                fb == null ? 1.0f : (float) fb.getDouble("sound.pitch", 1.0),
                fb == null ? 10 : fb.getInt("sound.rate-limit-ticks", 10)
        );

        // Recipes
        List<RecipeConfig> recipes = new ArrayList<>();
        List<Map<?, ?>> maps = cfg.getMapList("recipes");
        for (Map<?, ?> m : maps) {
            if (m == null || m.isEmpty()) continue;
            RecipeConfig rc = parseRecipe(m, logger);
            recipes.add(rc);
        }




        return CompactorConfig.builder()
                .id(id)
                .categoryItem(categoryItem)
                .name(name)
                .enabled(enabled)
                .commands(commandConfig)
                .chainingEnabled(chainingEnabled)
                .maxCraftsPerCycle(maxCrafts)
                .triggers(triggers)
                .feedback(feedback)
                .recipes(recipes)
                .build();
    }

    private static RecipeConfig parseRecipe(Map<?, ?> map, NexusLogger logger) {
        String id = String.valueOf(map.get("id"));

        Object nameObj = map.get("name");
        String name = nameObj != null ? String.valueOf(nameObj) : "Default Recipe";

        boolean enabled = getBool(map, "enabled", true);
        int priority = getInt(map, "priority", 100);

        // permissions als List<String> robust konvertieren
        List<String> permissions;
        Object permsObj = map.get("permissions");
        if (permsObj instanceof List<?> list) {
            List<String> tmp = new ArrayList<>(list.size());
            for (Object e : list) {
                if (e != null) tmp.add(String.valueOf(e));
            }
            permissions = Collections.unmodifiableList(tmp);
        } else {
            permissions = Collections.emptyList();
        }

        // "required" in Map<String, Object> umwandeln
        Map<String, Object> required;
        Object reqObj = map.get("required");
        if (reqObj instanceof Map<?, ?> reqMap) {
            required = new LinkedHashMap<>();
            for (Map.Entry<?, ?> en : reqMap.entrySet()) {
                required.put(String.valueOf(en.getKey()), en.getValue());
            }
        } else {
            throw new IllegalArgumentException("Recipe " + id + " missing 'required'");
        }

        String reqItem = String.valueOf(required.get("item"));
        int reqAmount = ((Number) required.getOrDefault("amount", 1)).intValue();

        // optionaler matcher
        MatcherConfig matcher = null;
        Object matcherObj = required.get("matcher");
        if (matcherObj instanceof Map<?, ?> mm) {
            Map<String, Object> mmStr = new LinkedHashMap<>();
            for (Map.Entry<?, ?> en : ((Map<?, ?>) mm).entrySet()) {
                mmStr.put(String.valueOf(en.getKey()), en.getValue());
            }
            matcher = parseMatcher(mmStr);
        }

        // "final" -> Map<String, Object>
        Map<String, Object> fin;
        Object finObj = map.get("final");
        if (finObj instanceof Map<?, ?> finMap) {
            fin = new LinkedHashMap<>();
            for (Map.Entry<?, ?> en : finMap.entrySet()) {
                fin.put(String.valueOf(en.getKey()), en.getValue());
            }
        } else {
            throw new IllegalArgumentException("Recipe " + id + " missing 'final' Item section");
        }

        String outItem = String.valueOf(fin.get("item"));
        int outAmount = ((Number) fin.getOrDefault("amount", 1)).intValue();

        return RecipeConfig.builder()
                .id(id)
                .name(name)
                .enabled(enabled)
                .priority(priority)
                .permissions(permissions)
                .required(new RequiredConfig(reqItem, reqAmount, matcher))
                .result(new ResultConfig(outItem, outAmount))
                .build();
    }



    private static MatcherConfig parseMatcher(Map<String, Object> mm) {
        String type = String.valueOf(mm.getOrDefault("type", "MATERIAL"));
        String name = (String) mm.getOrDefault("name", null);
        @SuppressWarnings("unchecked")
        List<String> loreContains = (List<String>) mm.getOrDefault("lore-contains", Collections.emptyList());
        Integer cmd = mm.get("custom-model-data") == null ? null : ((Number) mm.get("custom-model-data")).intValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> nbt = (Map<String, Object>) mm.getOrDefault("nbt", Collections.emptyMap());
        return new MatcherConfig(type, name, loreContains, cmd, nbt);
    }

    private static boolean getBool(Map<?, ?> map, String key, boolean def) {
        Object v = map.get(key);
        return v instanceof Boolean b ? b : def;
    }

    private static int getInt(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }
}