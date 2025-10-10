package io.nexstudios.compactors.config;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Value
public class CompactorCommandEntry {
    String id;
    String label;
    List<String> aliases;
    String permission;
    Scope scope;

    @Value
    public static class Scope {
        String mode; // all | whitelist | blacklist
        List<String> list;

        public boolean isAll() { return "all".equalsIgnoreCase(mode); }
        public boolean isWhitelist() { return "whitelist".equalsIgnoreCase(mode); }
        public boolean isBlacklist() { return "blacklist".equalsIgnoreCase(mode); }
    }

    @SuppressWarnings("unchecked")
    public static CompactorCommandEntry fromMap(Map<Object, Object> map) {
        String id = String.valueOf(map.get("id"));
        String label = String.valueOf(map.get("label"));
        List<String> aliases = (List<String>) map.getOrDefault("aliases", new ArrayList<>());
        String permission = String.valueOf(map.getOrDefault("permission", ""));
        Map<String, Object> scopeMap = (Map<String, Object>) map.getOrDefault("recipes-scope", Map.of("mode","all","list", List.of()));
        String mode = String.valueOf(scopeMap.getOrDefault("mode", "all"));
        List<String> list = (List<String>) scopeMap.getOrDefault("list", new ArrayList<>());
        return new CompactorCommandEntry(id, label, aliases, permission, new Scope(mode, list));
    }
}