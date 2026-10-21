FROM node:24-alpine
RUN apk upgrade --no-cache && apk add --no-cache ca-certificates && update-ca-certificates
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY *.js ./
CMD ["npm", "test"]
