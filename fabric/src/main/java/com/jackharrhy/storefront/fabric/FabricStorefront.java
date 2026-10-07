package com.jackharrhy.storefront.fabric;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jackharrhy.storefront.Owner;
import com.jackharrhy.storefront.Storage;
import com.jackharrhy.storefront.WebApiKt;
import com.mojang.serialization.JsonOps;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class FabricStorefront implements ModInitializer {
    public static FabricStorefront instance;
    Storage storage;
    FabricConfig config;
    private Javalin app;
    private FabricRefresh refresher;
    private static final Gson GSON = new Gson();

    @Override public void onInitialize() {
        instance = this;
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var force = Commands.literal("storefrontforceupdate")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(context -> {
                    boolean accepted = refresher != null && refresher.request();
                    context.getSource().sendSuccess(() -> Component.literal(accepted ? "Storefront refresh queued" : "Storefront refresh already running"), false);
                    return accepted ? 1 : 0;
                });
            var node = dispatcher.register(force);
            dispatcher.register(Commands.literal("forceupdate")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(node.getCommand()).redirect(node));
            dispatcher.register(Commands.literal("storefrontrefreshstatus")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(context -> { context.getSource().sendSuccess(() -> Component.literal(refresher.status()), false); return 1; }));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (refresher != null) refresher.tick(); });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world instanceof ServerLevel && hand == InteractionHand.MAIN_HAND
                && !player.isSpectator() && world.getBlockEntity(hit.getBlockPos()) instanceof SignBlockEntity sign && isListing(sign)) {
                save(player, sign);
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) ->
            !(entity instanceof SignBlockEntity sign) || !isListing(sign) || mayEdit(player, sign));
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (entity instanceof SignBlockEntity sign && isListing(sign) && world instanceof ServerLevel level) {
                var chestPos = chestPosition(sign);
                if (chestPos != null) storage.removeStorefront(player.getUUID().toString(), location(level, chestPos));
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                Files.createDirectories(Path.of("config/storefront"));
                config = FabricConfig.load(Path.of("config/storefront/config.yml"));
                storage = new Storage("config/storefront/storefront.db");
                refresher = new FabricRefresh(this, server);
                app = WebApiKt.createWebApp(storage, (id, slot) -> mapContents(server, id, slot))
                    .start(config.host(), config.port());
            } catch (Exception error) { throw new IllegalStateException("Cannot start Storefront", error); }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (app != null) app.stop();
            if (refresher != null) refresher.close();
        });
    }

    static BlockPos chestPosition(SignBlockEntity sign) {
        var state = sign.getBlockState();
        if (!(state.getBlock() instanceof WallSignBlock)) return null;
        var position = sign.getBlockPos().relative(state.getValue(WallSignBlock.FACING).getOpposite());
        return sign.getLevel().getBlockEntity(position) instanceof ChestBlockEntity ? position : null;
    }

    static boolean isListing(SignBlockEntity sign) {
        return sign.getFrontText().getMessage(0, false).getString().equalsIgnoreCase("[storefront]");
    }

    static String location(ServerLevel world, BlockPos pos) {
        String name = instance.config.worldName(world.dimension().identifier().toString());
        return name + ":" + (double) pos.getX() + ":" + (double) pos.getY() + ":" + (double) pos.getZ();
    }

    public boolean mayEdit(Player player, SignBlockEntity sign) {
        var pos = chestPosition(sign);
        if (pos == null || storage == null) return true;
        var owner = storage.ownerUUID(location((ServerLevel) sign.getLevel(), pos));
        return owner == null || owner.equals(player.getUUID().toString());
    }

    public void save(Player player, SignBlockEntity sign) {
        var pos = chestPosition(sign);
        if (pos == null || storage == null || !isListing(sign)) return;
        var world = (ServerLevel) sign.getLevel();
        var contents = inventory(world, pos);
        if (contents == null) return;
        var description = new String[4];
        for (int i = 0; i < 4; i++) description[i] = sign.getFrontText().getMessage(i, false).getString();
        boolean saved = storage.newStorefront(new Owner(player.getUUID().toString(), player.getName().getString()),
            location(world, pos), contents, description);
        player.sendSystemMessage(Component.literal(saved ? "Storefront saved" : "This isn't your storefront!"));
    }

    record Located(ServerLevel world, BlockPos pos) {}

    Located locate(MinecraftServer server, String serialized) {
        int zStart = serialized.lastIndexOf(':');
        int yStart = serialized.lastIndexOf(':', zStart - 1);
        int xStart = serialized.lastIndexOf(':', yStart - 1);
        if (xStart < 0) throw new IllegalArgumentException("Invalid storefront location: " + serialized);
        String name = serialized.substring(0, xStart);
        var pos = BlockPos.containing(Double.parseDouble(serialized.substring(xStart + 1, yStart)),
            Double.parseDouble(serialized.substring(yStart + 1, zStart)), Double.parseDouble(serialized.substring(zStart + 1)));
        for (var world : server.getAllLevels()) {
            if (config.worldName(world.dimension().identifier().toString()).equals(name)) return new Located(world, pos);
        }
        return null;
    }

    private CompletableFuture<String> mapContents(MinecraftServer server, int id, int slot) {
        var serialized = storage.storefrontLocationString(id);
        if (serialized == null) return CompletableFuture.failedFuture(new NotFoundResponse("Storefront not found"));
        var result = new CompletableFuture<String>();
        server.execute(() -> {
            try {
                var target = locate(server, serialized);
                if (target == null) throw new NotFoundResponse("World not found");
                if (!chestChunksLoaded(target.world(), target.pos())) throw new NotFoundResponse("Chest chunk is not loaded");
                var inventory = chest(target.world(), target.pos());
                if (inventory == null) throw new NotFoundResponse("Chest not found");
                if (slot < 0 || slot >= inventory.getContainerSize()) throw new BadRequestResponse("Invalid item position");
                var map = MapItem.getSavedData(inventory.getItem(slot), target.world());
                if (map == null) throw new NotFoundResponse("Map not found");
                String[] scales = { "CLOSEST", "CLOSE", "NORMAL", "FAR", "FARTHEST" };
                result.complete(GSON.toJson(Map.of("world", config.worldName(map.dimension.identifier().toString()),
                    "centerX", map.centerX, "centerZ", map.centerZ,
                    "scale", Map.of("name", scales[map.scale], "ordinal", map.scale))));
            } catch (Exception error) { result.completeExceptionally(error); }
        });
        return result;
    }

    static boolean chestChunksLoaded(ServerLevel world, BlockPos pos) {
        var chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        var state = chunk.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return true;
        var other = ChestBlock.getConnectedBlockPos(pos, state);
        return world.getChunkSource().getChunkNow(other.getX() >> 4, other.getZ() >> 4) != null;
    }

    static Container chest(ServerLevel world, BlockPos pos) {
        if (!chestChunksLoaded(world, pos)) return null;
        var state = world.getBlockState(pos);
        return state.getBlock() instanceof ChestBlock block ? ChestBlock.getContainer(block, state, world, pos, true) : null;
    }

    static String inventory(ServerLevel world, BlockPos pos) {
        if (!world.getServer().isSameThread()) throw new IllegalStateException("Inventory snapshots must be captured on the server thread");
        var inventory = chest(world, pos);
        if (inventory == null) return null;
        var items = new ArrayList<Object>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.isEmpty()) { items.add(null); continue; }
            var item = new LinkedHashMap<String, Object>();
            item.put("name", stack.getHoverName().getString());
            item.put("key", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            item.put("amount", stack.getCount());
            var meta = new LinkedHashMap<String, Object>();
            meta.put("damage", stack.getDamageValue());
            var jsonOps = world.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var encoded = ItemStack.CODEC.encodeStart(jsonOps, stack).getOrThrow().getAsJsonObject();
            meta.put("components", encoded.has("components") ? encoded.get("components") : new JsonObject());
            var nbtOps = world.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var tag = (CompoundTag) ItemStack.CODEC.encodeStart(nbtOps, stack).getOrThrow();
            try {
                var bytes = new ByteArrayOutputStream();
                NbtIo.writeCompressed(tag, bytes);
                meta.put("internal", Base64.getEncoder().encodeToString(bytes.toByteArray()));
            } catch (IOException error) { throw new UncheckedIOException(error); }
            item.put("meta", meta);
            item.put("isBlock", stack.getItem() instanceof BlockItem);
            item.put("maxDurability", stack.getMaxDamage());
            items.add(item);
        }
        return GSON.toJson(items);
    }
}
