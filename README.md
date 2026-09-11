# Storefront

A Paper plugin that displays Minecraft chest inventories in a React web interface. Put a wall sign on a chest with `[storefront]` on its first line; the remaining lines describe the shop. Right-click the sign to refresh it, or break it to remove the listing. Only the owner can update or remove a listing. Inventories refresh every two minutes; operators can use `/storefrontforceupdate` (alias `/forceupdate`).

This branch targets **Java 26 and Paper 26.2**. It replaces the original Java 8 / Paper 1.15.2 build, and uses Kotlin 2.4, Javalin 7, React 19, and Vite 8. It is not compatible with the old Minecraft server runtime. The SQLite table and saved inventory JSON remain compatible; the API now includes each storefront's database ID.

## Build the plugin

Install JDK 26 and run:

```sh
./mvnw verify
```

On Windows, use `mvnw.cmd verify`. The wrapper downloads Maven 3.9.16; no global Maven install is needed. Copy `target/storefront-2.0-SNAPSHOT.jar` into a Paper 26.2 server's `plugins/` directory and start the server with Java 26. The plugin bundles its runtime dependencies.

The plugin stores listings in `plugins/Storefront/storefront.db`. Keep a copy of the existing database and world before upgrading an old server. The Maven tests cover SQLite compatibility and HTTP routing. The Compose smoke test below exercises actual chest/sign interactions on a separate Paper server.

`plugins/Storefront/config.yml` configures the API, which listens on `127.0.0.1:7000` by default:

```yaml
web:
  host: 127.0.0.1
  port: 7000
```

Routes are `GET /storefronts/`, `GET /storefronts/{id}`, and `GET /storefronts/{id}/item/{position}/map` (zero-based inventory position). Missing entries return 404; malformed IDs and positions return 400.

## Develop the frontend

Use Node.js 24 (also recorded in `.nvmrc`):

```sh
cd storefront-frontend
npm ci
npm run dev
```

Open `http://localhost:5173/storefront/`. Vite proxies `/storefront/api/` to the plugin at `http://127.0.0.1:7000`. Set `API_PROXY_TARGET` in `storefront-frontend/.env.local` to use another backend. API failures have a retry button, so the UI also starts without a running Minecraft server.

Inventory icons are fetched automatically by `npm run dev`, `npm start`, and `npm run build`, including Docker builds. `npm run icons` fetches them explicitly. The downloader verifies a pinned archive checksum, converts the rendered icon names to lowercase, and caches 1,537 PNGs in `storefront-frontend/public/images/`. Generated graphics are excluded from Git. A missing icon in the cache causes a fresh download; `npm run icons -- --force` refreshes the entire set.

The render source is [Owen1212055/mc-assets](https://github.com/Owen1212055/mc-assets/tree/96b9b546b8797b1b544a8d9eed67c29b2a90b4cc), pinned to its **26.2 Pre-Release 2** export. These are 256×256 inventory renders, including 3D blocks, rather than raw block-face textures. This is not an exact final-26.2 export; new or variant-specific materials may still need custom icons. The graphics originate from Minecraft; the upstream project supplies the renders.

Rebuild the frontend container after updating images:

```sh
docker compose up --build -d --no-deps storefront-frontend
```

For the existing Caddy image route, run `npm run icons -- --output ../caddy/images` from the frontend directory, then run Caddy from `caddy/`. The built-in Minecraft fonts and UI textures are bundled separately.

Query options are preserved: `?username=Alice` filters shops; `&simpleUI` hides the header and refresh control; `&timestamp` adds the capture time. Item details include metadata and decode legacy NBT on demand in the browser.

```sh
npm run lint
npm test
npm run build
npm run preview
```

`dist/` is a static site served under `/storefront/`. Production hosting must proxy `/storefront/api/` to the plugin, serve `/storefront/images/`, and fall back to `index.html` for application routes. `npm run preview` previews static output only; it does not configure the production API proxy.

## Local Paper server and headless test

The root `compose.yml` builds the plugin with Java 26, starts Paper 26.2 build 123, and serves the frontend:

```sh
docker compose up --build -d --wait paper storefront-frontend
```

No local JDK or Maven install is needed for this path. The first build downloads Java/Maven dependencies and Paper; later builds reuse the Maven cache.

- Minecraft: `localhost:25566` (Java Edition 26.2; older clients can use the included ViaVersion/ViaBackwards bridge).
- Web UI: http://localhost:8080/storefront/
- Plugin API: http://localhost:7000/storefronts/

This is an isolated development world in the `storefront-dev_paper-data` Docker volume. Ports bind to localhost, the server uses offline authentication for test bots, and RCON is available only inside the Compose network. The Compose service sets `EULA=TRUE`, accepting the [Minecraft EULA](https://www.minecraft.net/eula). Do not reuse this offline configuration for a public server. Set `MC_PORT`, `WEB_PORT`, or `API_PORT` in your shell to change the host ports.

Run the real headless client test:

```sh
docker compose --profile test run --build --rm bot
```

[Mineflayer](https://github.com/PrismarineJS/mineflayer) connects as `StorefrontBot` without graphics or a Microsoft login. Its supported protocol is currently 26.1; pinned ViaVersion/ViaBackwards 5.11.0 plugins translate it to Paper 26.2. The test uses RCON to prepare a chest and sign near **0, 65, 0**, then has the bot right-click the sign, checks the plugin's HTTP response, changes the chest contents, verifies refresh, and breaks the sign to verify removal. It recreates the demo shop at the end so you can see it in the frontend. Rerunning replaces only this fixture area and its listing.

Useful commands:

```sh
# Logs and an operator command, without attaching a graphical client
docker compose logs -f paper
docker compose exec paper rcon-cli storefrontforceupdate

# Rebuild and restart the plugin after editing Kotlin
docker compose up --build -d --wait paper

# Stop the stack; keep the test world and database
docker compose down
```

`docker compose down -v` also deletes this stack's test world and database. The existing server on port 25565 is independent of this stack. To visit the demo chest with your own client, join `localhost:25566` and run `docker compose exec paper rcon-cli "tp YOUR_NAME 0.5 65 3.5"` from a terminal.

The frontend's nginx proxy targets `paper:7000` in Compose. For standalone hosting, set the container's `API_PROXY_TARGET` to another backend URL (without a trailing slash). Textures may be included in `public/images/` before building or mounted at `/usr/share/nginx/html/storefront/images/`.

The optional Discord screenshot bot uses Node 24, discord.js 14, and Puppeteer 25. Copy `storefront-discord/.env.dist` to `storefront-discord/.env`, set the token and storefront URL, and enable the **Message Content Intent** in the Discord developer portal. Start it with `docker compose --profile discord up --build`. The default prefix is `sf!`, with `ping` and `show <Minecraft username>` commands. For a local run, use `npm ci` and `npm start` in `storefront-discord/`, with `STOREFRONT_URL` pointing to your frontend. Puppeteer downloads its browser locally; the container uses system Chromium.

CI builds and tests the plugin and frontend on pushes and pull requests. The Docker publishing workflow runs only on `master` and uses the existing Docker Hub secrets.
