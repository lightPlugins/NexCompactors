package io.nexstudios.compactors.logic;

import io.nexstudios.compactors.NexCompactors;
import io.nexstudios.compactors.config.CompactorCommandEntry;
import io.nexstudios.compactors.config.CompactorConfig;
import io.nexstudios.compactors.config.RecipeConfig;
import io.nexstudios.compactors.config.RequiredConfig;
import io.nexstudios.compactors.database.CompactorPlayerCache;
import io.nexstudios.compactors.database.CompactorPlayerStateDao;
import io.nexstudios.nexus.bukkit.utils.NexusLogger;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
public class CompactorManager {

    private final NexCompactors plugin;
    private final NexusLogger logger;
    private final Map<String, CompactorConfig> compactors;
    private final CompactorPlayerStateDao playerStateDao;
    private final CompactorPlayerCache playerCache; // Injected, bleibt über Reload bestehen

    // Rate-limit pro (player, compactorId): letzter Aufrufzeitpunkt in Nanosekunden
    private final Map<String, Long> lastFeedback = new HashMap<>();

    public Collection<CompactorConfig> getAllCompactors() {
        return compactors.values();
    }

    public Optional<CompactorConfig> getCompactor(String id) {
        return Optional.ofNullable(compactors.get(id));
    }

    public List<RecipeConfig> getRecipesInScope(CompactorConfig cfg, CompactorCommandEntry.Scope scope) {
        List<RecipeConfig> base = cfg.getRecipes().stream().filter(RecipeConfig::isEnabled).toList();
        if (scope.isAll()) return base;
        Set<String> set = new HashSet<>(scope.getList());
        if (scope.isWhitelist()) {
            return base.stream().filter(r -> set.contains(r.getId())).toList();
        } else if (scope.isBlacklist()) {
            return base.stream().filter(r -> !set.contains(r.getId())).toList();
        }
        return base;
    }

    public void warmupPlayer(Player player) {
        for (CompactorConfig cfg : getAllCompactors()) {
            if (!cfg.isEnabled()) continue;
            loadStates(player.getUniqueId(), cfg.getId()).thenAccept(map -> {
                if (map != null) {
                    playerCache.putStates(player.getUniqueId(), cfg.getId(), map);
                }
            });
        }
    }

    public CompletableFuture<Map<String, Boolean>> loadPlayerStates(UUID player, String compactorId) {
        Map<String, Boolean> cached = playerCache.getStates(player, compactorId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return loadStates(player, compactorId).thenApply(map -> {
            if (map != null) {
                playerCache.putStates(player, compactorId, map);
            }
            return map;
        });
    }

    private CompletableFuture<Map<String, Boolean>> loadStates(UUID player, String compactorId) {
        return playerStateDao.loadStates(player, compactorId);
    }

    public CompletableFuture<Void> setPlayerRecipeState(UUID player, String compactorId, String recipeId, boolean enabled) {
        playerCache.putState(player, compactorId, recipeId, enabled);
        return playerStateDao.saveState(player, compactorId, recipeId, enabled);
    }

    public void tryCompactLater(Player player, int delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> compactAllEligible(player), Math.max(0, delayTicks));
    }

    public void compactAllEligible(Player player) {
        for (CompactorConfig cfg : compactors.values()) {
            if (!cfg.isEnabled()) continue;
            applyCompactor(player, cfg);
        }
    }

    private void applyCompactor(Player player, CompactorConfig cfg) {
        List<RecipeConfig> enabledRecipes = cfg.getRecipes().stream()
                .filter(RecipeConfig::isEnabled)
                .toList();

        if (enabledRecipes.isEmpty()) return;

        loadPlayerStates(player.getUniqueId(), cfg.getId()).thenAccept(states -> {
            if (states == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                List<RecipeConfig> active = enabledRecipes.stream()
                        .filter(r -> states.getOrDefault(r.getId(), false))
                        .collect(Collectors.toList());
                if (active.isEmpty()) return;

                active = active.stream()
                        .filter(r -> hasAllPermissions(player, r.getPermissions()))
                        .collect(Collectors.toList());

                if (active.isEmpty()) return;

                processInventory(player, cfg, active);
            });
        });
    }

    private boolean hasAllPermissions(Player player, List<String> perms) {
        if (perms == null || perms.isEmpty()) return true;
        for (String node : perms) {
            if (node == null || node.isBlank()) continue;
            if (!player.hasPermission(node)) return false;
        }
        return true;
    }

    private void processInventory(Player player, CompactorConfig cfg, List<RecipeConfig> recipes) {
        Map<Integer, List<RecipeConfig>> byPrio = recipes.stream().collect(Collectors.groupingBy(RecipeConfig::getPriority));
        List<Integer> prios = new ArrayList<>(byPrio.keySet());
        Collections.sort(prios);

        List<RecipeConfig> ordered = new ArrayList<>();
        Random rnd = new Random();
        for (Integer p : prios) {
            List<RecipeConfig> same = new ArrayList<>(byPrio.get(p));
            Collections.shuffle(same, rnd);
            ordered.addAll(same);
        }

        int craftsLeft = Math.max(1, cfg.getMaxCraftsPerCycle());
        var inv = player.getInventory();

        final int LRU_MAX = 16;
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        while (craftsLeft-- > 0) {
            boolean progressedAny = false;

            for (RecipeConfig r : ordered) {
                if (!canCraft(inv, r)) continue;

                boolean crafted = applyOneCraftTransactional(inv, r, cfg, player);
                if (crafted) {
                    progressedAny = true;
                }
            }

            if (!progressedAny) {
                break;
            }

            String signature = signature(inv, ordered);
            if (seen.contains(signature)) {
                logger.error("Compactor loop detected for compactor '" + cfg.getId() + "'. Repeated inventory state encountered. Check config.");
                break;
            }
            if (seen.size() >= LRU_MAX) {
                var it = seen.iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
            seen.add(signature);

            if (!cfg.isChainingEnabled()) break;
        }
    }

    private void sendNoSpaceFeedback(Player player, CompactorConfig cfg) {
        // Rate-Limit via System.nanoTime()
        String key = player.getUniqueId() + "|" + cfg.getId();
        long now = System.nanoTime();
        long last = lastFeedback.getOrDefault(key, 0L);
        long minDeltaNanos = Math.max(0, cfg.getFeedback().getRateLimitTicks()) * 50_000_000L; // ticks -> ns
        if (now - last < minDeltaNanos) return;
        lastFeedback.put(key, now);

        if (cfg.getFeedback().getChatMessage() != null && !cfg.getFeedback().getChatMessage().isBlank()) {
            player.sendMessage(cfg.getFeedback().getChatMessage());
        }
        if (cfg.getFeedback().isSoundEnabled()) {
            try {
                Key soundKey = Key.key(cfg.getFeedback().getSoundType());
                Sound s = Registry.SOUNDS.get(soundKey);
                if (s != null) {
                    player.playSound(player.getLocation(), s, cfg.getFeedback().getSoundVolume(), cfg.getFeedback().getSoundPitch());
                }
            } catch (Throwable ignored) { }
        }
    }

    private boolean canCraft(Inventory inv, RecipeConfig r) {
        int have = countMatching(inv, r.getRequired());
        return have >= r.getRequired().getAmount();
    }

    private int countMatching(Inventory inv, RequiredConfig req) {
        int count = 0;
        for (int i : storageSlots(inv)) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.getType().isAir()) continue;
            if (ItemMatcher.matches(slot, req)) count += slot.getAmount();
        }
        return count;
    }

    private boolean applyOneCraftTransactional(Inventory inv, RecipeConfig r, CompactorConfig cfg, Player player) {
        ItemStack out = ItemUtil.parseItem(r.getResult().getItem(), r.getResult().getAmount());
        if (out.getType().isAir()) {
            logger.warning("Compactor result item invalid for recipe '" + r.getId() + "'. Skipping craft.");
            return false;
        }

        int toRemove = r.getRequired().getAmount();
        List<SlotDelta> inputDeltas = new ArrayList<>();
        for (int i : storageSlots(inv)) {
            if (toRemove <= 0) break;
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.getType().isAir()) continue;
            if (!ItemMatcher.matches(slot, r.getRequired())) continue;

            int take = Math.min(slot.getAmount(), toRemove);
            ItemStack original = slot.clone();
            int newAmount = slot.getAmount() - take;
            inputDeltas.add(new SlotDelta(i, original, newAmount <= 0 ? null : setAmountClone(original, newAmount)));
            toRemove -= take;
        }
        if (toRemove > 0) {
            return false;
        }

        List<SlotDelta> outputDeltas = simulatePlaceResultInStorage(inv, out);
        if (outputDeltas == null) {
            sendNoSpaceFeedback(player, cfg);
            return false;
        }

        applyDeltas(inv, inputDeltas);
        applyDeltas(inv, outputDeltas);
        return true;
    }

    private static ItemStack setAmountClone(ItemStack base, int newAmount) {
        ItemStack c = base.clone();
        c.setAmount(newAmount);
        return c;
    }

    private List<SlotDelta> simulatePlaceResultInStorage(Inventory inv, ItemStack result) {
        int remaining = result.getAmount();
        List<SlotDelta> deltas = new ArrayList<>();

        // 1) vorhandene, kompatible Stacks auffüllen
        for (int i : storageSlots(inv)) {
            if (remaining <= 0) break;
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.getType().isAir()) continue;

            if (!slot.isSimilar(result)) continue;
            if (slot.getAmount() >= slot.getMaxStackSize()) continue;

            int canAdd = Math.min(slot.getMaxStackSize() - slot.getAmount(), remaining);
            if (canAdd <= 0) continue;

            ItemStack before = slot.clone();
            ItemStack after = slot.clone();
            after.setAmount(slot.getAmount() + canAdd);
            deltas.add(new SlotDelta(i, before, after));
            remaining -= canAdd;
        }

        // 2) freie Slots belegen
        for (int i : storageSlots(inv)) {
            if (remaining <= 0) break;
            ItemStack slot = inv.getItem(i);
            if (slot != null && !slot.getType().isAir()) continue;

            int place = Math.min(result.getMaxStackSize(), remaining);
            ItemStack before = slot == null ? null : slot.clone();
            ItemStack after = result.clone();
            after.setAmount(place);
            deltas.add(new SlotDelta(i, before, after));
            remaining -= place;
        }

        if (remaining > 0) {
            return null; // kein Platz in Storage
        }
        return deltas;
    }

    private void applyDeltas(Inventory inv, List<SlotDelta> deltas) {
        for (SlotDelta d : deltas) {
            inv.setItem(d.slot(), d.after());
        }
    }

    // Signatur zählt jetzt via Prototyp + isSimilar (nutzt ItemMatcher-Proto-Cache)
    private String signature(Inventory inv, List<RecipeConfig> relevant) {
        Map<String, Integer> counts = new TreeMap<>();
        Set<String> keys = new HashSet<>();
        for (RecipeConfig r : relevant) {
            keys.add(r.getRequired().getItem());
            keys.add(r.getResult().getItem());
        }
        for (String k : keys) {
            int c = 0;
            ItemStack proto = ItemMatcher.getOrBuildProto(k);
            if (  proto.getType().isAir()) {
                counts.put(k, 0);
                continue;
            }
            for (int i : storageSlots(inv)) {
                ItemStack slot = inv.getItem(i);
                if (slot == null || slot.getType().isAir()) continue;
                if (slot.isSimilar(proto)) c += slot.getAmount();
            }
            counts.put(k, c);
        }
        return counts.toString();
    }

    private IntStream storageSlotsStream(Inventory inv) {
        if (inv instanceof PlayerInventory) {
            return IntStream.range(0, 36);
        }
        return IntStream.range(0, inv.getSize());
    }

    private Iterable<Integer> storageSlots(Inventory inv) {
        return storageSlotsStream(inv).boxed().toList();
    }

    private record SlotDelta(int slot, ItemStack before, ItemStack after) {}

    public void runCompactorPass(Player player, CompactorConfig cfg) {
        if (player == null || cfg == null || !cfg.isEnabled()) return;

        Inventory inv = player.getInventory();

        Map<String, Boolean> states = getCachedOrLoadStatesSync(player.getUniqueId(), cfg.getId());

        List<RecipeConfig> recipes = cfg.getRecipes().stream()
                .filter(RecipeConfig::isEnabled)
                .filter(r -> states.getOrDefault(r.getId(), false))
                .filter(r -> hasAllPermissions(player, r.getPermissions()))
                .sorted(Comparator.comparingInt(RecipeConfig::getPriority))
                .toList();

        for (RecipeConfig r : recipes) {
            RequiredConfig required = r.getRequired();
            int perCraft = required.getAmount();
            if (perCraft <= 0) continue;

            int crafts = ItemUtil.computeMaxCrafts(inv, required, perCraft);
            if (crafts <= 0) continue;

            int maxPerCycle = cfg.isChainingEnabled()
                    ? cfg.getMaxCraftsPerCycle()
                    : crafts;
            if (maxPerCycle > 0) {
                crafts = Math.min(crafts, maxPerCycle);
            }

            if (!ItemUtil.consumeForCrafts(inv, required, perCraft, crafts)) {
                continue;
            }

            String resKey = r.getResult().getItem();
            int resPerCraft = Math.max(1, r.getResult().getAmount());
            long totalOut = (long) resPerCraft * crafts;

            ItemStack proto = ItemUtil.parseItem(resKey, 1);
            if (proto.getType() == Material.AIR) {
                logger.warning("[Compactor] Result item could not be parsed for recipe " + r.getId());
                continue;
            }

            int maxStack = Math.max(1, proto.getMaxStackSize());
            while (totalOut > 0) {
                int part = (int) Math.min(maxStack, totalOut);
                ItemStack give = proto.clone();
                give.setAmount(part);
                Map<Integer, ItemStack> leftovers = inv.addItem(give);

                if (!leftovers.isEmpty()) {
                    player.sendMessage(cfg.getFeedback().getChatMessage() != null ? cfg.getFeedback().getChatMessage() : "Not enough space in your inventory! Compaction aborted.");
                    break;
                }
                totalOut -= part;
            }
        }
    }

    private Map<String, Boolean> getCachedOrLoadStatesSync(UUID uuid, String compactorId) {
        Map<String, Boolean> cached = playerCache.getStates(uuid, compactorId);
        if (cached != null) return cached;
        try {
            return playerStateDao.loadStates(uuid, compactorId).join();
        } catch (Exception e) {
            logger.warning("Failed to load states synchronously for " + uuid + " / " + compactorId + ": " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ===== Helper für Trigger-Listener =====

    public boolean anyOnPickupEnabled() {
        return compactors.values().stream()
                .anyMatch(c -> c.isEnabled() && c.getTriggers() != null && c.getTriggers().isOnPickup());
    }

    public boolean anyOnAddItemEnabled() {
        return compactors.values().stream()
                .anyMatch(c -> c.isEnabled() && c.getTriggers() != null && c.getTriggers().isOnAddItem());
    }

    public boolean anyOnAutopickupEnabled() {
        return compactors.values().stream()
                .anyMatch(c -> c.isEnabled() && c.getTriggers() != null &&
                        (c.getTriggers().isOnAutoPickup() || safeIsOnAutoPickup(c)));
    }

    private boolean safeIsOnAutoPickup(CompactorConfig c) {
        try {
            return (boolean) c.getTriggers().getClass().getMethod("isOnAutoPickup").invoke(c.getTriggers());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public int resolveTriggerDelayTicks() {
        return compactors.values().stream()
                .filter(CompactorConfig::isEnabled)
                .filter(c -> c.getTriggers() != null)
                .mapToInt(c -> Math.max(0, c.getTriggers().getDelayTicks()))
                .max().orElse(0);
    }
}