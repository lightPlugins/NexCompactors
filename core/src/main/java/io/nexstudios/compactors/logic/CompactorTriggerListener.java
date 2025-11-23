package io.nexstudios.compactors.logic;

import io.nexstudios.compactors.NexCompactors;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trigger mit Entprellung (Debounce) pro Spieler, ohne direkte CompactorConfig-Abhängigkeit.
 * Der Delay und die aktivierten Trigger-Flags werden aus allen geladenen Configs via CompactorManager aggregiert.
 */
public final class CompactorTriggerListener implements Listener {

    private final NexCompactors plugin;
    private final CompactorManager manager;
    private final Map<UUID, Long> lastScheduleTick = new ConcurrentHashMap<>();

    public CompactorTriggerListener(NexCompactors plugin, CompactorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private void schedulePass(Player player) {
        if (player == null || !player.isOnline()) return;

        int delayTicks = manager.resolveTriggerDelayTicks();
        long now = Bukkit.getCurrentTick();
        long nextAllowed = lastScheduleTick.getOrDefault(player.getUniqueId(), -1L) + delayTicks;

        if (now < nextAllowed) return;
        lastScheduleTick.put(player.getUniqueId(), now);

        Bukkit.getScheduler().runTaskLater(plugin, () -> manager.compactAllEligible(player), delayTicks);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent e) {
        if (!manager.anyOnPickupEnabled()) return;
        schedulePass(e.getPlayer());
    }

    @EventHandler
    public void onPickup(PlayerInventorySlotChangeEvent e) {
        if (!manager.anyOnAddItemEnabled()) return;
        schedulePass(e.getPlayer());
    }

    // Verhindere Doppeltrigger: ignorieren, wenn es ein Spieler ist (AttemptPickup deckt den Fall bereits ab)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityPickup(EntityPickupItemEvent e) {
        if (!manager.anyOnPickupEnabled()) return;
        if (e.getEntity() instanceof Player) return;
    }

    // @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!manager.anyOnAddItemEnabled()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory().getType() != InventoryType.PLAYER) return;
        schedulePass(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!manager.anyOnAutopickupEnabled()) return;
        schedulePass(e.getPlayer());
    }
}