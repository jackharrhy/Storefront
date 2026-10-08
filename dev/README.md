# Development reference

Build, configuration, and test details for [Storefront](../README.md). Run commands from the repository root unless a section says otherwise.

## Runtime and compatibility

Storefront targets Java 25+ (build with Java 26), Paper/Fabric 26.2, Kotlin 2.4, Javalin 7, React 19, and Vite 8. Servers running the original Java 8 / Paper 1.15.2 version need a runtime upgrade. The SQLite table and saved inventory JSON remain compatible. API responses include each storefront's database ID.

## Build the plugin

Install JDK 26 and run:

```sh
./mvnw verify
```

On Windows, use `mvnw.cmd verify`. The wrapper downloads Maven 3.9.16; no global Maven install is needed. Copy `target/storefront-2.0-SNAPSHOT.jar` into a Paper 26.2 server's `plugins/` directory and start the server with Java 26. The plugin bundles its runtime dependencies.

The plugin stores listings in `plugins/Storefront/storefront.db`. Keep a copy of the existing database and world before upgrading an old server. The Maven tests cover SQLite compatibility, HTTP routing, refresh budgets, and protection against stale refresh writes. The Compose smoke test below exercises actual chest/sign interactions on a separate Paper server.

`plugins/Storefront/config.yml` configures the API, which listens on `127.0.0.1:7000` by default:

```yaml
web:
  host: 127.0.0.1
  port: 7000
```

Routes are `GET /storefronts/`, `GET /storefronts/{id}`, and `GET /storefronts/{id}/item/{position}/map` (zero-based inventory position). Missing entries return 404; malformed IDs and positions return 400. Live map details also return 404 while the chest chunk is unloaded; HTTP requests never force it to load.

## Develop the frontend

Use Node.js 24 (also recorded in `.nvmrc`). Install from the repository root; npm workspaces share one lockfile across the frontend, Discord bot, and `packages/shared`:

```sh
npm ci
npm run dev
```

Open `http://localhost:5173/`. Vite proxies `/api/` to the plugin at `http://127.0.0.1:7000`. Set `API_PROXY_TARGET` in `apps/frontend/.env.local` to use another backend. Without a running Minecraft server, the UI shows an API error and a retry button.

The npm workspace contains `apps/frontend`, `apps/discord`, and `packages/shared`. Run npm commands from the repository root.

Frontend development and builds fetch inventory icons automatically, including Docker builds. `npm run icons -w storefront-frontend` fetches them explicitly. The downloader verifies a pinned archive checksum, converts the rendered icon names to lowercase, and caches 1,537 PNGs in `apps/frontend/public/images/`. Generated graphics are excluded from Git. A missing icon in the cache causes a fresh download; `npm run icons -w storefront-frontend -- --force` refreshes the entire set.

The render source is [Owen1212055/mc-assets](https://github.com/Owen1212055/mc-assets/tree/96b9b546b8797b1b544a8d9eed67c29b2a90b4cc), pinned to its **26.2 Pre-Release 2** export. These are 256×256 inventory renders, including 3D blocks, rather than raw block-face textures. This is not an exact final-26.2 export; new or variant-specific materials may still need custom icons. The graphics originate from Minecraft; the upstream project supplies the renders.

Rebuild the frontend container after updating images:

```sh
docker compose up --build -d --no-deps storefront-frontend
```

The [Caddy config](../caddy/Caddyfile) proxies `/api/` to the plugin and other requests, including images, to Vite. Run Caddy from `caddy/`. The built-in Minecraft fonts and UI textures are bundled separately.

The frontend uses strict TypeScript, React Router data mode, and TanStack Query. A route loader fills the shared query cache before rendering; these are browser HTTP requests to the plugin, with static hosting and no SSR process. Initial loading is a quiet text status. Refresh keeps existing shops visible, including on errors, and username navigation reuses cached data. Oxlint and Oxfmt provide linting and formatting.

Query options: `?username=Alice` filters shops; `&simpleUI` hides the header and refresh control; `&timestamp` adds the capture time. Item details include metadata and decode legacy NBT on demand in the browser.

```sh
npm run lint
npm run format:check # npm run format to apply formatting
npm run typecheck
npm test
npm run build
npm run preview -w storefront-frontend
```

`apps/frontend/dist/` is a static site served under `/`. Production hosting must strip `/api` when proxying to the plugin (`/api/storefronts/` → `http://127.0.0.1:7000/storefronts/`), serve `/images/`, and fall back to `index.html` for application routes. `npm run preview` previews static output only; it does not configure the production API proxy.

## Local Paper server and headless test

The root [`compose.yml`](../compose.yml) builds the plugin with Java 26, starts Paper 26.2 build 123, and serves the frontend:

```sh
docker compose up --build -d --wait paper storefront-frontend
```

No local JDK or Maven install is needed for this path. The first build downloads Java/Maven dependencies and Paper; later builds reuse the Maven cache.

- Minecraft: `localhost:25566` (Java Edition 26.2; older clients can use the included ViaVersion/ViaBackwards bridge).
- Web UI: http://localhost:8080/
- Plugin API: http://localhost:7000/storefronts/

This is an isolated development world in the `storefront-dev_paper-data` Docker volume. Ports bind to localhost, the server uses offline authentication for test bots, and RCON is available only inside the Compose network. The Compose service sets `EULA=TRUE`, accepting the [Minecraft EULA](https://www.minecraft.net/eula). Do not reuse this offline configuration for a public server. Set `MC_PORT`, `WEB_PORT`, or `API_PORT` in your shell to change the host ports.

Run the headless client test:

```sh
docker compose --profile test run --build --rm bot
```

[Mineflayer](https://github.com/PrismarineJS/mineflayer) connects as `StorefrontBot` without graphics or a Microsoft login. Its supported protocol is currently 26.1; pinned ViaVersion/ViaBackwards 5.11.0 plugins translate it to Paper 26.2. The test uses RCON to prepare a chest and sign near `0, 65, 0`, then has the bot right-click the sign, checks the plugin's HTTP response, changes the chest contents, verifies refresh, and breaks the sign to verify removal. It recreates the demo shop at the end so you can see it in the frontend. Rerunning replaces only this fixture area and its listing.

```sh
# Logs and an operator command, without attaching a graphical client
docker compose logs -f paper
docker compose exec paper rcon-cli storefrontforceupdate
docker compose exec paper rcon-cli storefrontrefreshstatus

# Rebuild and restart the plugin after editing Kotlin
docker compose up --build -d --wait paper

# Stop the stack; keep the test world and database
docker compose down
```

`docker compose down -v` also deletes this stack's test world and database. The existing server on port 25565 is independent of this stack. To visit the demo chest with your own client, join `localhost:25566` and run `docker compose exec paper rcon-cli "tp YOUR_NAME 0.5 65 3.5"` from a terminal.

The frontend's nginx proxy targets `paper:7000` in Compose. For standalone hosting, set the container's `API_PROXY_TARGET` to another backend URL (without a trailing slash). Textures may be included in `public/images/` before building or mounted at `/usr/share/nginx/html/images/`.

The optional Discord screenshot bot uses TypeScript, Node 24, discord.js 14, and Puppeteer 25. It shares storefront types and URL options with the frontend through `@storefront/shared`. See [Discord setup and screenshot checks](../apps/discord/README.md).

## Fabric smoke test

Build with `./fabric/gradlew -p fabric build smokeJar`. Maven and Gradle compile
`src/main/kotlin/common` for the shared SQLite schema, owner policy, HTTP routes, persisted location/JSON
contracts and complete refresh lifecycle (budgets, async writes, draining and shutdown). Paper retains its events and item/component translation; Fabric owns
its vanilla sign-edit mixin, world snapshots, scheduling and shutdown. Dependencies
are bundled, excluding Minecraft-owned Gson/SLF4J. Javalin, Kotlin, Jetty,
servlet and ASM packages are private to the mod; service descriptors are merged
and relocated with their providers.

For a fast combined-classpath HTTP regression against ItemSorter Fabric, build
both runtime jars and run from this repository (no Minecraft server or world):

```sh
python dev/combined-web-classpath.py \
  fabric/build/libs/storefront-fabric-2.0-SNAPSHOT.jar \
  /path/to/ItemSorter/fabric/build/libs/ItemSorter-Fabric-26.2.jar
```

It starts both real web apps in one classloader, in both jar orders, and checks
Storefront owner-protected persistence/reopen and JSON/map routes plus ItemSorter static files, authorized
Thymeleaf rendering, save and replay rejection. Reflection follows each shipped
API's relocated parameter types rather than compiling a fixture against original
library names. Append AudioPlayer's runtime jar as an optional third argument to
put its colliding `web/index.html` first on the classpath. ItemSorter's root must
be Hopper Configuration (not merely any HTML); CSS and block icons must also
load. Both root and context-prefixed ItemSorter URLs are checked. Only Minecraft-owned Gson/SLF4J come from the Gradle cache; a
missing standalone SLF4J provider produces harmless logging warnings. Packaging
checks reject exposed conflicting libraries and missing service providers.

The split borrows only small patterns from genuine dual-platform projects:
[Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat/blob/master/settings.gradle)
separates common behavior from loader entrypoints;
[BlueMap](https://github.com/BlueMap-Minecraft/BlueMap/tree/master/implementations)
builds separate platform artifacts and excludes host-owned libraries;
[LuckPerms](https://github.com/LuckPerms/LuckPerms/tree/master/fabric)
keeps platform events and resource lifecycle outside shared storage. Storefront
has no platform factory or generic compatibility framework.

Download a Fabric server launcher for **26.2 / Loader 0.19.5 / Installer 1.1.2**,
Fabric API **0.161.0+26.2**, and Mojang's **26.2** server JAR into your scratch
workspace. The CI workflow contains exact URLs and verified game/API checksums.
Run, replacing paths with those local downloads:

```sh
python dev/fabric-smoke.py --workspace "$TMPDIR" \
  --launcher "$TMPDIR/fabric-launch.jar" \
  --fabric-api "$TMPDIR/fabric-api.jar" \
  --minecraft "$TMPDIR/minecraft.jar" --accept-eula
```

The runner always creates a new test directory, binds game/HTTP to
`127.0.0.1:25675` / `127.0.0.1:8765`, and stops only its own child processes.
It seeds a legacy-schema test listing, edits real world signs, invokes the
registered interaction/break callbacks, checks ownership and administrator
restrictions, snapshots nested metadata and maps over HTTP, exercises bounded
refreshes and unloaded double-chest neighbors, then restarts to check persistence.
Logs and artifact hashes remain in that test directory. Never install its separate
`smoke` JAR on a real server: it intentionally creates fixture blocks.
These are server-side lifecycle tests with synthetic players, not packet/client
tests. Mineflayer 4.39.0 rejects protocol 26.2; an actual vanilla-client join,
sign editing and breaking still need in-game verification. No production data,
deployment or running production server is involved.

CI builds and tests both server artifacts and all three JavaScript workspaces on
pushes and pull requests. It also runs the isolated Fabric lifecycle/restart test.
The installable JARs are saved as `storefront-plugin` and `storefront-fabric`.

To release, update the version in `pom.xml`, then publish a GitHub Release targeting `main` for that commit’s tag. Fabric reads the version from `pom.xml`. CI tests the tagged code and attaches both JARs after all jobs pass, including for pre-releases. The tag must include this workflow. Re-running the release job replaces the attachment.

Docker publishing uses GHCR and `GITHUB_TOKEN`, only for `main` or releases targeting `main`. Feature-branch manual dispatches build without publishing.

## Refresh scheduling and stress testing

Operators can queue a refresh with `/storefrontforceupdate` (alias `/forceupdate`).

Bulk refresh reads database targets on a dedicated worker, captures live inventories on the server thread, and writes changed snapshots in transactions on the worker. It captures at most eight chests or approximately 2 ms of work per tick by default; one inventory capture cannot be interrupted and may exceed that budget. Unloaded chunks retain their last snapshot until a later refresh with the chunk loaded. Overlapping refresh requests are coalesced; `/storefrontforceupdate` queues work, and `/storefrontrefreshstatus` reports completion and timing as JSON.

Configure `refresh.chests-per-tick` and `refresh.budget-ms` in the plugin config. SQLite uses WAL and a location index. Conditional writes ensure an older bulk capture cannot overwrite a newer player update or delete a replacement listing. Individual sign interactions still perform their small database operations on the server thread. See [the performance report](reports/README.md) for measured results and limitations.

Run a larger Mineflayer workload in the Compose development world:

```sh
STOREFRONTS=100 RUN_LABEL=local docker compose --profile stress run --build --rm stress
# Repeat against existing fixtures without rebuilding the world
STOREFRONTS=100 SETUP=false RUN_LABEL=repeat docker compose --profile stress run --rm stress
# Modify every chest between three refreshes and assert the writes reached the API
STOREFRONTS=100 SETUP=false DIRTY=true RUN_LABEL=dirty docker compose --profile stress run --rm stress
```

The bot registers full chests by interacting with signs in a grid beginning at `1024, 65, 1024`, then runs ten refreshes alongside four HTTP readers. `DIRTY=true` runs three sweeps with an item-count change in every chest and restores the original counts afterward. Reports are saved to `dev/reports/`. The stress container writes as UID/GID 1000 by default; set `LOCAL_UID` and `LOCAL_GID` to match your user if needed. Fixture chunks stay loaded for measurement and are released afterward. Listings and fixture blocks persist for inspection; reuse the same count for `SETUP=false`. This exercises actual server inventory serialization and API reads, but does not simulate many concurrent players.

## Every-item rendering fixture

The item catalog uses Paper's live registry and includes potion, enchantment, trim, dye, durability, and other metadata variants. Run `docker compose --profile catalog run --build --rm catalog` after rebuilding the stack, then visit `/?username=StorefrontItems`.

See [catalog setup and browser audit](browser/README.md) for the exact coverage boundaries, image audit, and viewport-loading checks. The fixture checks base item images; metadata variants can still share the same sprite.
