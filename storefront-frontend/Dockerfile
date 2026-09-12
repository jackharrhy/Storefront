FROM node:24-alpine AS build
WORKDIR /app
ENV PUPPETEER_SKIP_DOWNLOAD=true
COPY package.json package-lock.json tsconfig.base.json ./
COPY packages/shared/package.json packages/shared/package.json
COPY storefront-frontend/package.json storefront-frontend/package.json
COPY storefront-discord/package.json storefront-discord/package.json
COPY packages/shared packages/shared
RUN npm ci
COPY storefront-frontend storefront-frontend
RUN npm run build -w storefront-frontend

FROM nginx:stable-alpine
ENV API_PROXY_TARGET=http://host.docker.internal:7000
COPY storefront-frontend/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/storefront-frontend/dist /usr/share/nginx/html
EXPOSE 80
