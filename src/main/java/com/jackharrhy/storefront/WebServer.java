package com.jackharrhy.storefront;

import io.javalin.Javalin;

public class WebServer {
    public WebServer(Storefront plugin, Storage storage) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(Storefront.class.getClassLoader());
        Javalin app = Javalin.create().start(7000);
        Thread.currentThread().setContextClassLoader(classLoader);

        app.get("/", ctx -> {
            ctx.res.setContentType("application/json");
            String resp = storage.getAllContents()
                    .stream()
                    .reduce("[", (acum, next) -> acum + next + ",");
            ctx.result(resp.substring(0, resp.length() - 1) + "]");
        });

        plugin.getLogger().info("WebServer now running");
    }
}
