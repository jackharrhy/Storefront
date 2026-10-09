package com.jackharrhy.storefront.fabric;

import com.google.gson.Gson;
import com.jackharrhy.storefront.ChestSnapshot;
import com.jackharrhy.storefront.Refresh;
import net.minecraft.server.MinecraftServer;
import org.slf4j.LoggerFactory;

final class FabricRefresh implements AutoCloseable {
    private final FabricStorefront mod;
    private final MinecraftServer server;
    private final Refresh refresh;
    private int ticks;

    FabricRefresh(FabricStorefront mod, MinecraftServer server) {
        this.mod = mod;
        this.server = server;
        refresh = new Refresh(mod.storage, server::execute, location -> {
            var found = mod.locate(server, location);
            if (found == null || !FabricStorefront.chestChunksLoaded(found.world(), found.pos()))
                return ChestSnapshot.Unloaded.INSTANCE;
            return new ChestSnapshot.Loaded(FabricStorefront.inventory(found.world(), found.pos()));
        }, error -> LoggerFactory.getLogger("Storefront").error("Storefront refresh failed", error));
    }

    boolean request() {
        if (!server.isSameThread()) throw new IllegalStateException("Refresh must start on the server thread");
        return refresh.request(mod.config.chestsPerTick(), mod.config.budgetNanos());
    }

    void tick() {
        if (++ticks % 2400 == 0) request();
        refresh.tick();
    }

    String status() { return new Gson().toJson(refresh.getStatus()); }

    @Override public void close() { refresh.close(); }
}
