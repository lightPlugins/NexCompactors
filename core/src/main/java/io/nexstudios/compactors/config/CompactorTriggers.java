package io.nexstudios.compactors.config;

import lombok.Value;

@Value
public class CompactorTriggers {
    boolean onPickup;
    boolean onAutoPickup;
    boolean onAddItem;
    int delayTicks;
}