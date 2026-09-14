FROM node:24-bookworm-slim AS build
WORKDIR /app
ENV PUPPETEER_SKIP_DOWNLOAD=true
COPY package.json package-lock.json tsconfig.base.json ./
COPY packages/shared/package.json packages/shared/package.json
COPY apps/frontend/package.json apps/frontend/package.json
COPY apps/discord/package.json apps/discord/package.json
COPY packages/shared packages/shared
RUN npm ci
COPY apps/discord apps/discord
RUN npm run build -w storefront-discord
RUN npm ci --omit=dev --workspace=storefront-discord --workspace=@storefront/shared --ignore-scripts

FROM node:24-bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends chromium fonts-liberation \
    && rm -rf /var/lib/apt/lists/*
ENV PUPPETEER_SKIP_DOWNLOAD=true \
    PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium
WORKDIR /app
COPY --from=build /app/package.json ./package.json
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/packages/shared ./packages/shared
COPY --from=build /app/apps/discord/package.json ./apps/discord/package.json
COPY --from=build /app/apps/discord/dist ./apps/discord/dist
USER node
CMD ["node", "apps/discord/dist/index.js"]
