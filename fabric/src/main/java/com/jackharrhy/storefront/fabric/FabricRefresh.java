package com.jackharrhy.storefront.fabric;

import com.google.gson.Gson;
import com.jackharrhy.storefront.RefreshChange;
import com.jackharrhy.storefront.RefreshTarget;
import net.minecraft.server.MinecraftServer;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class FabricRefresh implements AutoCloseable {
    private final FabricStorefront mod;
    private final MinecraftServer server;
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "Storefront-database"); thread.setDaemon(true); return thread;
    });
    private volatile boolean closed;
    private boolean running;
    private int completed, targets, changed, skipped, ticks;
    private long started, maxCapture, elapsed;
    private String error;
    private Iterator<RefreshTarget> remaining;
    private final List<CompletableFuture<Integer>> writes = new ArrayList<>();

    FabricRefresh(FabricStorefront mod, MinecraftServer server) { this.mod = mod; this.server = server; }

    boolean request() {
        if (!server.isSameThread()) throw new IllegalStateException("Refresh must start on the server thread");
        if (closed || running) return false;
        running = true; targets = changed = skipped = 0; maxCapture = elapsed = 0; error = null;
        started = System.nanoTime(); writes.clear();
        CompletableFuture.supplyAsync(mod.storage::refreshTargets, worker).whenComplete((rows, failure) -> onMain(() -> {
            if (failure != null) { finish(failure); return; }
            targets = rows.size(); remaining = rows.iterator();
        }));
        return true;
    }

    void tick() {
        if (++ticks % 2400 == 0) request();
        if (closed || remaining == null) return;
        try {
            long start = System.nanoTime();
            var changes = new ArrayList<RefreshChange>();
            int count = 0;
            while (remaining.hasNext() && count++ < mod.config.chestsPerTick()) {
                var target = remaining.next();
                var found = mod.locate(server, target.getLocation());
                if (found == null || !FabricStorefront.chestChunksLoaded(found.world(), found.pos())) {
                    skipped++;
                } else {
                    String contents = FabricStorefront.inventory(found.world(), found.pos());
                    if (!java.util.Objects.equals(contents, target.getContents())) changes.add(new RefreshChange(target, contents));
                }
                if (System.nanoTime() - start >= mod.config.budgetNanos()) break;
            }
            maxCapture = Math.max(maxCapture, System.nanoTime() - start);
            if (!changes.isEmpty()) writes.add(CompletableFuture.supplyAsync(() -> mod.storage.applyRefresh(changes), worker));
            if (!remaining.hasNext()) drain(null);
        } catch (Exception failure) { drain(failure); }
    }

    private void drain(Throwable captureFailure) {
        remaining = null;
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> onMain(() -> {
            Throwable problem = captureFailure == null ? failure : captureFailure;
            if (problem == null) changed = writes.stream().mapToInt(CompletableFuture::join).sum();
            finish(problem);
        }));
    }

    private void finish(Throwable failure) {
        running = false; completed++; elapsed = System.nanoTime() - started;
        error = failure == null ? null : failure.toString();
        if (failure != null) LoggerFactory.getLogger("Storefront").error("Storefront refresh failed", failure);
    }

    private void onMain(Runnable action) { server.execute(() -> { if (!closed) action.run(); }); }

    String status() {
        return new Gson().toJson(new Status(running, completed, targets, changed, skipped, maxCapture / 1_000_000.0, elapsed / 1_000_000.0, error));
    }
    private record Status(boolean running, int completed, int targets, int changed, int skippedUnloaded, double maxCaptureMs, double elapsedMs, String error) {}

    @Override public void close() {
        closed = true; remaining = null; worker.shutdown();
        try { if (!worker.awaitTermination(5, TimeUnit.SECONDS)) worker.shutdownNow(); }
        catch (InterruptedException failure) { worker.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
