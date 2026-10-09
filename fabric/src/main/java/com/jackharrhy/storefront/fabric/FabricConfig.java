package com.jackharrhy.storefront.fabric;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

record FabricConfig(String host, int port, int chestsPerTick, long budgetNanos, Map<String, String> worlds) {
    static FabricConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, "web:\n  host: 127.0.0.1\n  port: 7000\nrefresh:\n  chests-per-tick: 8\n  budget-ms: 2.0\n");
        }
        Object loaded;
        try (var input = Files.newInputStream(path)) {
            var options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            loaded = new Yaml(new SafeConstructor(options)).load(input);
        }
        var root = loaded == null ? Map.of() : (Map<?, ?>) loaded;
        var web = (Map<?, ?>) root.getOrDefault("web", null);
        var refresh = (Map<?, ?>) root.getOrDefault("refresh", null);
        String host = String.valueOf(value(web, "host", "127.0.0.1"));
        int port = ((Number) value(web, "port", 7000)).intValue();
        if (host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("Invalid web address");
        int limit = Math.clamp(((Number) value(refresh, "chests-per-tick", 8)).intValue(), 1, 100);
        double budget = ((Number) value(refresh, "budget-ms", 2.0)).doubleValue();
        if (!Double.isFinite(budget)) throw new IllegalArgumentException("Invalid refresh budget");
        var worlds = new java.util.LinkedHashMap<String, String>();
        worlds.put("minecraft:overworld", "world");
        worlds.put("minecraft:the_nether", "world_nether");
        worlds.put("minecraft:the_end", "world_the_end");
        if (root.get("worlds") instanceof Map<?, ?> aliases) {
            aliases.forEach((dimension, name) -> worlds.put(String.valueOf(dimension), String.valueOf(name)));
        }
        if (worlds.values().stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("World aliases must not be blank");
        if (worlds.values().stream().distinct().count() != worlds.size()) throw new IllegalArgumentException("World aliases must be unique");
        return new FabricConfig(host, port, limit, (long) (Math.clamp(budget, 0.1, 20.0) * 1_000_000), Map.copyOf(worlds));
    }

    private static Object value(Map<?, ?> map, String key, Object fallback) {
        return map == null || map.get(key) == null ? fallback : map.get(key);
    }

    String worldName(String dimension) { return worlds.getOrDefault(dimension, dimension); }
}
