package io.nexstudios.compactors.logic;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Lädt die Compactor-States eines Spielers beim Join in den Cache
 * und hält sie bis zum Server-Stopp im Speicher.
 */
@RequiredArgsConstructor
public class CompactorPlayerListener implements Listener {

    private final CompactorManager manager;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        manager.warmupPlayer(e.getPlayer());
    }
}