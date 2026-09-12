# Storefront

Browse Minecraft chest inventories on the web. Storefront is a Paper plugin with a React frontend styled like classic Java Edition.

Put a wall sign on a chest with `[storefront]` on the first line and a shop description below it. Right-click the sign to refresh the listing, or break it to remove it. Only the owner can update or remove a listing. Loaded inventories refresh every two minutes.

## Run locally

With Docker Compose installed:

```sh
docker compose up --build -d --wait paper storefront-frontend
```

Open http://localhost:8080/ or join the test server at `localhost:25566`. Compose builds the plugin and starts Paper 26.2, so you don't need Java installed locally.

The test server binds to localhost and uses offline authentication for bots. Keep this configuration local. Starting it accepts the [Minecraft EULA](https://www.minecraft.net/eula).

To create a demo shop with the headless Minecraft client:

```sh
docker compose --profile test run --build --rm bot
```

Stop with `docker compose down`. The world and database persist; adding `-v` deletes them.

## Install the plugin

Use JDK 26 to build:

```sh
./mvnw verify
```

On Windows, use `mvnw.cmd verify`. Copy `target/storefront-2.0-SNAPSHOT.jar` into your Paper 26.2 server's `plugins/` directory. Back up the world and `plugins/Storefront/storefront.db` before upgrading an existing server.

## Work on the frontend

Use Node.js 24:

```sh
cd storefront-frontend
npm ci
npm run dev
```

Open http://localhost:5173/. Item images download automatically. Vite forwards `/api/` requests to the plugin at `127.0.0.1:7000`; set `API_PROXY_TARGET` in `.env.local` to use another server.

The frontend uses TypeScript, React Router data mode, TanStack Query, Oxlint, and Oxfmt. `npm run build` produces a static site in `dist/`. Serve it at `/` and have your reverse proxy strip `/api` before forwarding API requests to the plugin. The included nginx and Caddy configs do this.

## More details

- [Configuration, API routes, hosting, and Discord setup](dev/README.md)
- [Refresh performance and stress tests](dev/reports/README.md)
- [Every-item fixtures and browser rendering tests](dev/browser/README.md)
