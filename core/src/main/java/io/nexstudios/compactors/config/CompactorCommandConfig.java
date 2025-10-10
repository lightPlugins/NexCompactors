package io.nexstudios.compactors.config;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor
public class CompactorCommandConfig {
    boolean enabled;
    List<CompactorCommandEntry> entries;
}