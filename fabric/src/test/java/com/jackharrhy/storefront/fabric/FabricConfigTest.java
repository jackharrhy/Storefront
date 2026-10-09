package com.jackharrhy.storefront.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FabricConfigTest {
    @TempDir Path directory;

    @Test void createsLoopbackDefaultsWithoutOverwritingAnExistingConfig() throws Exception {
        var file = directory.resolve("storefront/config.yml");
        var defaults = FabricConfig.load(file);
        assertEquals("127.0.0.1", defaults.host());
        assertEquals(7000, defaults.port());
        assertEquals("world_nether", defaults.worldName("minecraft:the_nether"));
        assertEquals("custom:dimension", defaults.worldName("custom:dimension"));
        String original = Files.readString(file);
        FabricConfig.load(file);
        assertEquals(original, Files.readString(file));
    }

    @Test void readsCopiedPaperWebSettingsAndExplicitWorldAliases() throws Exception {
        var file = directory.resolve("config.yml");
        Files.writeString(file, "web:\n  host: 127.0.0.1\n  port: 8765\nrefresh:\n  chests-per-tick: 3\n  budget-ms: 1.5\nworlds:\n  minecraft:overworld: cheesetown\n");
        var config = FabricConfig.load(file);
        assertEquals(8765, config.port());
        assertEquals(3, config.chestsPerTick());
        assertEquals(1_500_000, config.budgetNanos());
        assertEquals("cheesetown", config.worldName("minecraft:overworld"));
    }

    @Test void rejectsUnsafeYamlTagsAndAmbiguousWorldNames() throws Exception {
        var file = directory.resolve("config.yml");
        Files.writeString(file, "!!java.net.URL ['https://example.com']");
        assertThrows(org.yaml.snakeyaml.error.YAMLException.class, () -> FabricConfig.load(file));
        Files.writeString(file, "worlds:\n  minecraft:the_nether: world\n");
        assertThrows(IllegalArgumentException.class, () -> FabricConfig.load(file));
    }

    @Test void rejectsBlankWorldAliasesInsteadOfLosingLegacyListings() throws Exception {
        var file = directory.resolve("config.yml");
        Files.writeString(file, "worlds:\n  minecraft:overworld: ''\n");
        assertThrows(IllegalArgumentException.class, () -> FabricConfig.load(file));
    }
}
