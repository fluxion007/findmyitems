package dev.smpb.findmyitems;

import dev.smpb.findmyitems.config.ModConfig;
import dev.smpb.findmyitems.debug.TestbedCommand;
import dev.smpb.findmyitems.gui.CatalogScreen;
import dev.smpb.findmyitems.gui.ChestHighlighter;
import dev.smpb.findmyitems.index.ContainerIndex;
import dev.smpb.findmyitems.index.InMemoryContainerIndex;
import dev.smpb.findmyitems.observation.ObservationCollector;
import dev.smpb.findmyitems.observation.PositionCache;
import dev.smpb.findmyitems.retrieval.GhostOpen;
import dev.smpb.findmyitems.retrieval.CraftingExecutor;
import dev.smpb.findmyitems.search.InventorySearchController;
import dev.smpb.findmyitems.store.JsonWorldStore;
import dev.smpb.findmyitems.store.WorldKey;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.LevelResource;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class FindMyItemsClient implements ClientModInitializer {
    private static ContainerIndex sharedIndex;
    private static ModConfig sharedConfig;
    private static CraftingExecutor sharedExecutor;
    private ContainerIndex index;
    private KeyMapping openCatalogKey;
    private JsonWorldStore store;

    /** The live index for this client. Null before {@code onInitializeClient}. Used by the client game tests. */
    public static ContainerIndex index() {
        return sharedIndex;
    }

    /**
     * The single shared config instance. The settings screen previously loaded its own copy from
     * disk, so rescan-interval changes were not seen by the running collector until restart.
     */
    public static ModConfig config() {
        if (sharedConfig == null) sharedConfig = ModConfig.load(configPath());
        return sharedConfig;
    }

    public static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("findmyitems.json");
    }

    public static CraftingExecutor executor() {
        if (sharedExecutor == null) {
            sharedExecutor = new CraftingExecutor(sharedIndex, config());
        }
        return sharedExecutor;
    }

    @Override
    public void onInitializeClient() {
        index = new InMemoryContainerIndex();
        sharedIndex = index;

        var configDir = Minecraft.getInstance().gameDirectory.toPath().resolve("config");
        sharedConfig = ModConfig.load(configPath());

        new ObservationCollector(index, sharedConfig);
        sharedExecutor = new CraftingExecutor(index, sharedConfig);
        new InventorySearchController();
        ChestHighlighter.init();
        GhostOpen.init();

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            TestbedCommand.register();
        }

        store = new JsonWorldStore(configDir);

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            var pos = hitResult.getBlockPos();
            PositionCache.record(pos, level.dimension().identifier().toString());
            return InteractionResult.PASS;
        });

        openCatalogKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.findmyitems.open_catalog",
                GLFW.GLFW_KEY_B,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var server = client.getSingleplayerServer();
            if (server == null) return;

            // The save directory, not the level name: two saves can both be called "New World",
            // and sharing one index between them would show you chests from the other world.
            var saveId = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                    .getFileName().toString();
            var levelName = server.getWorldData().getLevelName();
            var playerId = client.player.getUUID();
            var key = WorldKey.singleplayer(saveId, levelName, playerId);

            var result = store.load(key);
            CatalogScreen.invalidateRecipeCache();
            index.replace(result.snapshot());
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            var server = client.getSingleplayerServer();
            if (server == null) return;

            // The save directory, not the level name: two saves can both be called "New World",
            // and sharing one index between them would show you chests from the other world.
            var saveId = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                    .getFileName().toString();
            var levelName = server.getWorldData().getLevelName();
            var playerId = client.player.getUUID();
            var key = WorldKey.singleplayer(saveId, levelName, playerId);

            try {
                store.save(key, index.snapshot(), List.of());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Whether the mod does anything right now.
     *
     * <p>Single-player only. The index remembers containers <em>you</em> opened; on a server it goes
     * stale when others modify chests, with no way to detect it. Retrieval, rescanning, and the
     * crafting planner read the integrated server's state, which a remote server does not provide.
     * The mod therefore stands down when there is no local world.
     */
    public static boolean activeWorld() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    private void onTick(Minecraft client) {
        if (sharedExecutor != null) sharedExecutor.tick();
        if (!openCatalogKey.consumeClick()) return;

        if (!activeWorld()) {
            if (client.player != null) {
                client.player.sendOverlayMessage(
                        Component.translatable("message.findmyitems.singleplayer_only"));
            }
            return;
        }

        var current = client.gui.screen();
        if (current instanceof CatalogScreen catalogScreen) {
            catalogScreen.onClose();
        } else if (current == null) {
            client.gui.setScreen(new CatalogScreen(index, sharedConfig));
        }
    }
}
