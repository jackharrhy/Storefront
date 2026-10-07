import java.lang.reflect.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Exercises the shipped jars together, without compiling against unshaded library APIs. */
public class CombinedWebClasspath {
    static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        return target.getClass().getMethod(name, types).invoke(target, args);
    }
    static void check(HttpClient client, Object app, String path, String body, int status, String contains) throws Exception {
        int port = (Integer) call(app, "port", new Class<?>[0]);
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body != null) builder.header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != status || !response.body().contains(contains))
            throw new AssertionError(path + ": " + response.statusCode() + " " + response.body());
    }
    public static void main(String[] args) throws Exception {
        String contextPath = args.length == 0 ? "/sorter" : args[0];
        Object storefront = null, sorter = null;
        Path db = Files.createTempFile(Path.of(System.getenv("TMPDIR")), "combined-web-", ".sqlite");
        try (HttpClient client = HttpClient.newHttpClient()) {
            Class<?> storageType = Class.forName("com.jackharrhy.storefront.Storage");
            Object storage = storageType.getConstructor(String.class).newInstance(db.toString());
            Method create = Arrays.stream(Class.forName("com.jackharrhy.storefront.WebApiKt").getMethods())
                .filter(m -> m.getName().equals("createWebApp")).findFirst().orElseThrow();
            Class<?> function = create.getParameterTypes()[1];
            Object maps = Proxy.newProxyInstance(function.getClassLoader(), new Class<?>[]{function},
                (proxy, method, values) -> CompletableFuture.completedFuture("{\"map\":true}"));
            storefront = create.invoke(null, storage, maps);
            call(storefront, "start", new Class<?>[]{String.class, int.class}, "127.0.0.1", 0);

            Class<?> editor = Class.forName("tollenaar.stephen.ItemSorter.Shared.WebEditor");
            Method configure = Arrays.stream(editor.getMethods()).filter(m -> m.getName().equals("configure")).findFirst().orElseThrow();
            String javalinName = configure.getParameterTypes()[0].getName().replace("core.JavalinConfig", "Javalin");
            Class<?> javalin = Class.forName(javalinName);
            sorter = javalin.getMethod("create", Consumer.class).invoke(null, (Consumer<Object>) config -> {
                try { configure.invoke(null, config, contextPath); }
                catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            });
            Method register = Arrays.stream(editor.getMethods()).filter(m -> m.getName().equals("registerEditor")).findFirst().orElseThrow();
            Class<?> sessionsType = register.getParameterTypes()[1];
            Object sessions = sessionsType.getConstructor().newInstance();
            String token = (String) sessionsType.getMethod("issue", UUID.class, Integer.class, String.class)
                .invoke(sessions, UUID.randomUUID(), 1, null);
            Class<?> handler = register.getParameterTypes()[6];
            Object noop = Proxy.newProxyInstance(handler.getClassLoader(), new Class<?>[]{handler}, (proxy, method, values) -> null);
            Class<?> hopper = Class.forName("tollenaar.stephen.ItemSorter.Util.Web.HopperItems");
            Object choices = hopper.getConstructors()[0].newInstance(List.of(), List.of(), List.of(), false, false, false, null);
            Object load = Proxy.newProxyInstance(handler.getClassLoader(), new Class<?>[]{handler}, (proxy, method, values) -> {
                Object ctx = values[0];
                call(ctx, "attribute", new Class<?>[]{String.class, Object.class}, "attributes",
                    Map.of("items", List.of(), "enchantments", List.of(), "potions", List.of()));
                call(ctx, "attribute", new Class<?>[]{String.class, Object.class}, "checkItems", choices);
                return null;
            });
            register.invoke(null, sorter, sessions, "/editor", "/save", false, load, noop);
            call(sorter, "start", new Class<?>[]{String.class, int.class}, "127.0.0.1", 0);
            if (storefront.getClass().getClassLoader() != sorter.getClass().getClassLoader())
                throw new AssertionError("Apps must share a classloader");
            check(client, storefront, "/storefronts/", null, 200, "[]");
            check(client, storefront, "/storefronts/1/item/0/map", null, 200, "\"map\":true");
            check(client, sorter, contextPath + "/", null, 200, "<title>Hopper Configuration</title>");
            check(client, sorter, contextPath + "/css/page.css", null, 200, "font-family");
            check(client, sorter, contextPath + "/images/block/stone.png", null, 200, "PNG");
            check(client, sorter, contextPath + "/editor", null, 403, "fresh link");
            check(client, sorter, contextPath + "/editor?token=" + token, null, 200, token);
            check(client, sorter, contextPath + "/save", "token=" + token, 200, "Thank you");
            check(client, sorter, contextPath + "/save", "token=" + token, 403, "fresh link");
            System.out.println("PASS: both shipped web apps in one classloader; Storefront JSON/map, ItemSorter static/Thymeleaf/save/replay");
        } finally {
            if (sorter != null) call(sorter, "stop", new Class<?>[0]);
            if (storefront != null) call(storefront, "stop", new Class<?>[0]);
            for (String suffix : List.of("", "-wal", "-shm")) Files.deleteIfExists(Path.of(db + suffix));
        }
    }
}
