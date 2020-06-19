# build
FROM node:12-alpine as build

WORKDIR /usr/src/app

COPY package.json /usr/src/app/package.json
RUN npm install --silent

COPY . /usr/src/app/
RUN npm run build

# prod
FROM nginx:alpine

COPY --from=build /usr/src/app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
