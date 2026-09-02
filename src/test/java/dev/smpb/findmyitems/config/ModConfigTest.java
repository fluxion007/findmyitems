package dev.smpb.findmyitems.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModConfigTest {
    @Test
    void searchDistanceIsChunksAndConvertsToBlocks() {
        var config = new ModConfig();

        assertEquals(4, config.searchDistanceChunks, "default is 64 blocks (4 chunks)");
        assertEquals(64, config.searchDistanceBlocks());
        assertTrue(config.filterInventory);
        assertTrue(config.filterContainers);

        config.searchDistanceChunks = 8;
        assertEquals(128, config.searchDistanceBlocks());
    }

    @Test
    void unlimitedStaysUnlimitedThroughTheConversion() {
        var config = new ModConfig();
        config.searchDistanceChunks = 0;

        assertEquals(0, config.searchDistanceBlocks(), "0 means unlimited and must not become a radius");
    }

    /** The layout toggle writes through here, so a preference set once has to survive a restart. */
    @Test
    void layoutRoundTripsThroughDisk(@TempDir Path dir) {
        var path = dir.resolve("findmyitems.json");
        var config = ModConfig.load(path);
        assertFalse(config.gridLayout, "list is the default");

        config.gridLayout = true;
        config.save();

        assertTrue(ModConfig.load(path).gridLayout, "grid should still be grid on the next load");
    }

    @Test
    void filterSettingsRoundTripThroughDisk(@TempDir Path dir) {
        var path = dir.resolve("findmyitems.json");
        var config = ModConfig.load(path);
        config.gridLayout = true;
        config.filterInventory = false;
        config.filterContainers = false;
        config.save();

        var loaded = ModConfig.load(path);
        assertTrue(loaded.gridLayout);
        assertFalse(loaded.filterInventory);
        assertFalse(loaded.filterContainers);
    }

    @Test
    void aConfigThatWasNeverLoadedDoesNotThrowOnSave() {
        new ModConfig().save();
    }

    @Test
    void anUnreadableFileFallsBackToDefaults(@TempDir Path dir) throws IOException {
        var path = dir.resolve("findmyitems.json");
        Files.writeString(path, "{ this is not json");

        var config = ModConfig.load(path);

        assertEquals(4, config.searchDistanceChunks);
        // Still remembers where it came from, so the next save repairs the broken file.
        config.save();
        assertEquals(4, ModConfig.load(path).searchDistanceChunks);
    }

    /** Gson answers null for an empty file rather than failing, which used to become an NPE later. */
    @Test
    void anEmptyFileFallsBackToDefaults(@TempDir Path dir) throws IOException {
        var path = dir.resolve("findmyitems.json");
        Files.writeString(path, "");

        assertEquals(5, ModConfig.load(path).rescanIntervalSeconds);
    }
}
