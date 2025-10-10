package io.nexstudios.compactors.config;

import lombok.Value;

@Value
public class RequiredConfig {
    String item; // namespaced id
    int amount;
    MatcherConfig matcher; // optional; null => MATERIAL-only
}