package dev.smpb.findmyitems.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public int rescanIntervalSeconds = 5;
    /**
     * Rescan radius in chunks, matching the units used by the view and simulation distance. A
     * player can compare "8 chunks" with the render distance without doing arithmetic.
     * 0 means unlimited. The default is 64 blocks.
     */
    public int searchDistanceChunks = 4;
    /** Whether the catalog opens in grid layout; this preference persists beyond the screen. */
    public boolean gridLayout = false;
    public boolean filterInventory = true;
    public boolean filterContainers = true;
    /**
     * Keep the ender inventory indexed with no ender chest placed.
     *
     * <p>Unlike every other container, this one is player data: reading it needs no block and no
     * loaded chunk, so the count stays true wherever you are. What it does not give you is a way
     * in — the catalog lists that stock as out of reach until you stand at an ender chest.
     */
    public boolean indexEnderInventory = true;
    /**
     * Maximum container distance, in blocks, for retrieval. 0 preserves normal reach.
     *
     * <p>Off by default because it changes what the mod lets you do rather than what it knows. The
     * index remembers chests across a whole base; this decides whether the catalog may also empty
     * one from the other side of it.
     */
    public int retrieveDistanceBlocks = 0;

    private transient Path path;

    public static ModConfig load(Path path) {
        var config = read(path);
        config.path = path;
        return config;
    }

    private static ModConfig read(Path path) {
        if (Files.isRegularFile(path)) {
            try {
                var parsed = GSON.fromJson(Files.readString(path), ModConfig.class);
                // Gson hands back null for an empty or literal-null file rather than failing.
                if (parsed != null) return parsed;
            } catch (IOException | com.google.gson.JsonParseException e) {
                return new ModConfig();
            }
        }
        return new ModConfig();
    }

    /** Writes back to wherever this was loaded from. No-op for a config that was never loaded. */
    public void save() {
        if (path != null) save(path);
    }

    public int searchDistanceBlocks() {
        return searchDistanceChunks * 16;
    }

    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
