package io.nexstudios.compactors.config;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class CompactorConfig {
    String id;
    Map<String, Object> categoryItem;
    String name;
    boolean enabled;
    CompactorCommandConfig commands;
    boolean chainingEnabled;
    int maxCraftsPerCycle;
    CompactorTriggers triggers;
    CompactorFeedback feedback;
    List<RecipeConfig> recipes;
}