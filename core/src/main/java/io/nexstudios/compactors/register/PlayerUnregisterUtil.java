package io.nexstudios.compactors.register;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

public final class PlayerUnregisterUtil {
    private PlayerUnregisterUtil() {}

    public static void unregisterListener(Listener l) {
        HandlerList.unregisterAll(l);
    }
}
