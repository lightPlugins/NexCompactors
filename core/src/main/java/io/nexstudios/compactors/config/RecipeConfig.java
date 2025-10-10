package io.nexstudios.compactors.config;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RecipeConfig {
    String id;
    String name;
    boolean enabled;
    int priority;
    List<String> permissions; // ALL must be present to enable
    RequiredConfig required;
    ResultConfig result;
}