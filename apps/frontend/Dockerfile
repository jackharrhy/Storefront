FROM node:24-alpine AS build
WORKDIR /app
ENV PUPPETEER_SKIP_DOWNLOAD=true
COPY package.json package-lock.json tsconfig.base.json ./
COPY packages/shared/package.json packages/shared/package.json
COPY apps/frontend/package.json apps/frontend/package.json
COPY apps/discord/package.json apps/discord/package.json
COPY packages/shared packages/shared
RUN npm ci
COPY apps/frontend apps/frontend
ARG BASE_PATH=/
RUN npm run build -w storefront-frontend -- --base=${BASE_PATH}

FROM nginx:stable-alpine
ENV API_PROXY_TARGET=http://host.docker.internal:7000
COPY apps/frontend/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/apps/frontend/dist /usr/share/nginx/html
EXPOSE 80
