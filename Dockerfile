# build
FROM node:12-alpine as build

WORKDIR /usr/src/app

COPY storefront-frontend/package.json /usr/src/app/package.json
RUN npm install --silent

COPY ./storefront-frontend/ /usr/src/app/
RUN ls
RUN npm run build

# prod
FROM nginx:alpine

COPY --from=build /usr/src/app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
