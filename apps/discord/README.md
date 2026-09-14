# Discord bot

`sf!ping` replies with `pong!`. `sf!show Alice` captures Alice's shops and replies with a PNG and a link to the website. The bot works in server channels and direct messages.

## Run

Use Node 24. From the repository root:

```sh
npm ci
cp apps/discord/.env.dist apps/discord/.env
npm run dev:discord
```

Set `STOREFRONT_DISCORD_TOKEN` and `STOREFRONT_URL` in that file before starting. For a bot running on your host against Compose, use `http://localhost:8080/`; the example's `http://storefront-frontend/` address is for Docker. Set `STOREFRONT_PUBLIC_URL` to the address Discord users can open, such as your public or tailnet URL. It defaults to `STOREFRONT_URL` when omitted. `STOREFRONT_COMMAND_PREFIX` defaults to `sf!`.

Enable Message Content Intent in the Discord developer portal. The bot needs permission to view its channels, send messages, and attach files.

To run in Compose:

```sh
docker compose --profile discord up --build -d
```

For a compiled local build:

```sh
npm run build
npm start -w storefront-discord
```

## Screenshots

The bot reuses Chromium with a separate browser context for each capture. It accepts two captures at once; further requests get a retry response and a website link. Captures wait for the frontend data, fonts, and lazy images. Images taller than 30,000 pixels or larger than 8 MB fall back to a link. SIGINT and SIGTERM close Discord and Chromium.

Docker uses system Chromium. Local installs use Puppeteer's browser; set `PUPPETEER_EXECUTABLE_PATH` to use an existing installation. If browser downloads were skipped, run `npx puppeteer browsers install chrome`.

Run unit tests with `npm test -w storefront-discord`. To check real screenshots against the local frontend without logging into Discord or sending messages:

```sh
docker compose --profile test run --build --rm bot
npm run smoke -w storefront-discord
```

Set `STOREFRONT_URL` and `SMOKE_USERNAME` in your shell to test another page. Set `SCREENSHOT_PATH` to save the image. The smoke check also exercises empty results, the concurrency limit, browser reuse, and shutdown.
