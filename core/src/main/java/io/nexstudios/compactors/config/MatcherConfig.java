package io.nexstudios.compactors.config;

import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
public class MatcherConfig {
    String type; // MATERIAL | NAME | LORE | CUSTOM_MODEL_DATA | NBT | COMBINED
    String name; // case-sensitive
    List<String> loreContains; // case-sensitive contains on any line
    Integer customModelData;
    Map<String, Object> nbt; // optional, implementation-defined
}