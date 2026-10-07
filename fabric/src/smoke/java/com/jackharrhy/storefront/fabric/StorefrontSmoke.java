package com.jackharrhy.storefront.fabric;

import com.google.gson.JsonParser;
import com.jackharrhy.storefront.Owner;
import com.jackharrhy.storefront.Storage;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class StorefrontSmoke implements ModInitializer {
    private boolean started;
    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (started) return;
            started = true;
            try {
                if (Boolean.getBoolean("storefront.smoke.restart")) {
                    var restored = new Storage("config/storefront/storefront.db").getAllContents();
                    if (restored.size() != 1 || restored.getFirst().getContents().getAsJsonArray().size() != 54
                        || !restored.getFirst().getContents().toString().contains("minecraft:gold_ingot")) throw new AssertionError("Listing did not survive restart");
                    var saved = (SignBlockEntity) server.overworld().getBlockEntity(new BlockPos(0, 80, -1));
                    if (saved == null || !saved.getFrontText().getMessage(1, false).getString().equals("Double chest")) throw new AssertionError("Sign did not survive restart");
                    String current = FabricStorefront.inventory(server.overworld(), new BlockPos(0, 80, 0));
                    if (!JsonParser.parseString(current).equals(restored.getFirst().getContents())) throw new AssertionError("World inventory differs after restart");
                    System.out.println("STOREFRONT SMOKE PASS: persisted listing and world sign/inventory");
                    return;
                }
                var world = server.overworld();
                var farChest = new BlockPos(15999, 80, 16000);
                world.getChunk(farChest.getX() >> 4, farChest.getZ() >> 4);
                world.setBlock(farChest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT), 18);
                if (world.hasChunk(1000, 1000)) throw new AssertionError("Fixture neighbor was already loaded");
                FabricStorefront.inventory(world, farChest);
                if (world.hasChunk(1000, 1000)) throw new AssertionError("Snapshot forced an unloaded double-chest neighbor");
                var chestPos = new BlockPos(0, 80, 0);
                var signPos = chestPos.north();
                world.setChunkForced(0, 0, true);
                world.setChunkForced(0, -1, true);
                world.setBlock(chestPos.east(), Blocks.AIR.defaultBlockState(), 3);
                world.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
                world.setBlock(signPos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH), 3);
                var chest = (ChestBlockEntity) world.getBlockEntity(chestPos);
                chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
                chest.setItem(1, MapItem.create(world, 128, 256, (byte) 2, true, false));
                var sword = new ItemStack(Items.DIAMOND_SWORD);
                sword.setDamageValue(50);
                sword.set(DataComponents.CUSTOM_NAME, Component.literal("Trade blade"));
                sword.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Hand forged"))));
                var custom = new CompoundTag(); custom.putString("storefront-test", "retained");
                sword.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
                chest.setItem(2, sword);
                var box = new ItemStack(Items.SHULKER_BOX);
                box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(sword)));
                chest.setItem(3, box);
                var alice = new ServerPlayer(server, world,
                    new GameProfile(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), "Alice"), ClientInformation.createDefault()) {
                    @Override public void sendSystemMessage(Component text) { System.out.println("SMOKE PLAYER: " + text.getString()); }
                };
                alice.setPos(0, 80, -1);
                var sign = (SignBlockEntity) world.getBlockEntity(signPos);
                sign.setAllowedPlayerEditor(alice.getUUID());
                sign.updateSignText(alice, true, List.of(FilteredText.passThrough("[storefront]"),
                    FilteredText.passThrough("Diamonds"), FilteredText.passThrough("For sale"), FilteredText.passThrough("")));
                var storage = new Storage("config/storefront/storefront.db");
                if (storage.getAllContents().size() != 1) throw new AssertionError("Editing a chest sign must create a listing");
                var listing = storage.getAllContents().getFirst();
                int legacyId = Integer.getInteger("storefront.smoke.legacyId", -1);
                if (legacyId >= 0 && listing.getId() != legacyId) throw new AssertionError("Legacy Paper listing ID changed");
                if (!listing.getDescription().getAsJsonArray().get(1).getAsString().equals("Diamonds")) throw new AssertionError("Sign text not captured");
                if (listing.getContents().getAsJsonArray().get(0).getAsJsonObject().get("amount").getAsInt() != 3) throw new AssertionError("Chest contents not captured");
                var swordJson = listing.getContents().getAsJsonArray().get(2).getAsJsonObject();
                if (!swordJson.get("meta").getAsJsonObject().has("components")) throw new AssertionError("Item components are missing");
                String components = swordJson.get("meta").getAsJsonObject().get("components").toString();
                if (!components.contains("Hand forged") || !components.contains("retained")) throw new AssertionError("Lore/custom data lost");
                var internal = swordJson.get("meta").getAsJsonObject().get("internal");
                if (internal == null) throw new AssertionError("Raw NBT metadata is missing");
                var nbt = NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(internal.getAsString())), NbtAccounter.unlimitedHeap());
                if (!nbt.toString().contains("retained")) throw new AssertionError("Raw NBT metadata lost");
                if (!listing.getContents().getAsJsonArray().get(3).toString().contains("Trade blade")) throw new AssertionError("Nested item metadata lost");
                chest.setItem(0, new ItemStack(Items.DIAMOND, 4));
                var hit = new BlockHitResult(Vec3.atCenterOf(signPos), Direction.NORTH, signPos, false);
                UseBlockCallback.EVENT.invoker().interact(alice, world, InteractionHand.MAIN_HAND, hit);
                if (storage.getAllContents().getFirst().getContents().getAsJsonArray().get(0).getAsJsonObject().get("amount").getAsInt() != 4) throw new AssertionError("Right-click must update chest contents");
                CompletableFuture.runAsync(() -> {
                    try (var client = HttpClient.newHttpClient()) {
                        try {
                            FabricStorefront.inventory(world, chestPos);
                            throw new AssertionError("Off-thread inventory capture was allowed");
                        } catch (IllegalStateException expected) { }
                        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8765/storefronts/"))
                            .timeout(Duration.ofSeconds(10)).build();
                        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() != 200 || !response.body().contains("minecraft:diamond")) throw new AssertionError("HTTP listing not served: " + response.body());
                        var mapRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8765/storefronts/" + listing.getId() + "/item/1/map"))
                            .timeout(Duration.ofSeconds(10)).build();
                        var mapResponse = client.send(mapRequest, HttpResponse.BodyHandlers.ofString());
                        if (mapResponse.statusCode() != 200 || !mapResponse.body().contains("NORMAL")) throw new AssertionError("Map details not served: " + mapResponse.body());
                        var checks = Map.of(
                            "/storefronts/" + listing.getId(), 200,
                            "/storefronts/invalid", 400,
                            "/storefronts/99999", 404,
                            "/storefronts/" + listing.getId() + "/item/-1/map", 400,
                            "/storefronts/" + listing.getId() + "/item/99/map", 400,
                            "/storefronts/" + listing.getId() + "/item/0/map", 404,
                            "/storefronts/99999/item/0/map", 404);
                        for (var check : checks.entrySet()) {
                            var reply = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8765" + check.getKey())).timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
                            if (reply.statusCode() != check.getValue()) throw new AssertionError(check.getKey() + ": " + reply.statusCode());
                        }
                        server.submit(() -> {
                            chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
                            storage.newStorefront(new Owner("alice", "Alice"), "world:1000000.0:80.0:1000000.0", "[]", new String[]{"Unloaded"});
                            storage.newStorefront(new Owner("alice", "Alice"), "retired:0.0:80.0:0.0", "[]", new String[]{"Missing world"});
                            storage.newStorefront(new Owner("alice", "Alice"), "world:5.0:80.0:5.0", "[]", new String[]{"Missing chest"});
                            var dispatcher = server.getCommands().getDispatcher();
                            if (dispatcher.findNode(List.of("storefrontforceupdate")) == null) throw new AssertionError("Refresh command is missing");
                            try {
                                if (dispatcher.execute("storefrontforceupdate", server.createCommandSourceStack()) != 1) throw new AssertionError("Refresh not queued");
                                if (dispatcher.execute("storefrontforceupdate", server.createCommandSourceStack()) != 0) throw new AssertionError("Concurrent sweep not coalesced");
                                if (dispatcher.execute("forceupdate", server.createCommandSourceStack()) != 0) throw new AssertionError("Refresh alias not coalesced");
                                for (var name : List.of("storefrontforceupdate", "forceupdate", "storefrontrefreshstatus")) {
                                    if (dispatcher.findNode(List.of(name)).canUse(server.createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS))) throw new AssertionError("Non-admin command access: " + name);
                                }
                            } catch (CommandSyntaxException error) { throw new RuntimeException(error); }
                        }).get(10, TimeUnit.SECONDS);
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                        while (storage.getAllContents().getFirst().getContents().getAsJsonArray().get(0).getAsJsonObject().get("amount").getAsInt() != 7
                            || storage.ownerUUID("world:5.0:80.0:5.0") != null) {
                            if (System.nanoTime() > deadline) throw new AssertionError("Refresh did not capture changed inventory");
                            Thread.sleep(50);
                        }
                        server.submit(() -> {
                            var bob = new ServerPlayer(server, world, new GameProfile(UUID.randomUUID(), "Bob"), ClientInformation.createDefault()) {
                                @Override public void sendSystemMessage(Component text) { System.out.println("SMOKE PLAYER: " + text.getString()); }
                            };
                            var original = storage.storefrontContentsById(listing.getId());
                            UseBlockCallback.EVENT.invoker().interact(bob, world, InteractionHand.MAIN_HAND, hit);
                            sign.setAllowedPlayerEditor(bob.getUUID());
                            sign.updateSignText(bob, true, List.of(FilteredText.passThrough("[storefront]"), FilteredText.passThrough("Stolen"), FilteredText.passThrough(""), FilteredText.passThrough("")));
                            if (!original.equals(storage.storefrontContentsById(listing.getId())) || !sign.getFrontText().getMessage(1, false).getString().equals("Diamonds")) throw new AssertionError("Non-owner edit changed listing/sign");
                            var breaks = PlayerBlockBreakEvents.BEFORE;
                            if (breaks.invoker().beforeBlockBreak(world, bob, signPos, sign.getBlockState(), sign)) throw new AssertionError("Non-owner can break listing sign");
                            if (!breaks.invoker().beforeBlockBreak(world, alice, signPos, sign.getBlockState(), sign)) throw new AssertionError("Owner cannot break sign");
                            var state = sign.getBlockState();
                            world.removeBlock(signPos, false);
                            PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(world, alice, signPos, state, sign);
                            if (storage.storefrontContentsById(listing.getId()) != null) throw new AssertionError("Sign break must remove listing");
                            if (storage.ownerUUID("world:5.0:80.0:5.0") != null) throw new AssertionError("Refresh did not delete missing chest");
                            if (storage.ownerUUID("world:1000000.0:80.0:1000000.0") == null || storage.ownerUUID("retired:0.0:80.0:0.0") == null) throw new AssertionError("Unloaded/missing worlds were deleted");
                            if (world.hasChunk(1000000 >> 4, 1000000 >> 4)) throw new AssertionError("Refresh loaded an unloaded chunk");
                            storage.removeStorefront("alice", "world:1000000.0:80.0:1000000.0");
                            storage.removeStorefront("alice", "retired:0.0:80.0:0.0");
                            world.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT), 3);
                            world.setBlock(chestPos.east(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.RIGHT), 3);
                            ((ChestBlockEntity) world.getBlockEntity(chestPos.east())).setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
                            world.setBlock(signPos, state, 3);
                            var doubleSign = (SignBlockEntity) world.getBlockEntity(signPos);
                            doubleSign.setAllowedPlayerEditor(alice.getUUID());
                            doubleSign.updateSignText(alice, true, List.of(FilteredText.passThrough("[storefront]"), FilteredText.passThrough("Double chest"), FilteredText.passThrough(""), FilteredText.passThrough("")));
                            var doubled = storage.getAllContents().getFirst();
                            if (doubled.getContents().getAsJsonArray().size() != 54 || !doubled.getContents().toString().contains("minecraft:gold_ingot")) throw new AssertionError("Double chest contents missing");
                        }).get(10, TimeUnit.SECONDS);
                        System.out.println("STOREFRONT SMOKE PASS: sign creation/update/removal and HTTP listing/map");
                    } catch (Throwable error) { error.printStackTrace(); System.out.println("STOREFRONT SMOKE FAIL"); }
                });
            } catch (Throwable error) { error.printStackTrace(); System.out.println("STOREFRONT SMOKE FAIL"); }
        });
    }
}
