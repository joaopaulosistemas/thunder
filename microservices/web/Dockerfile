# Uma imagem por aplicacao Angular, para deploy independente:
#   docker build --build-arg APP=shell --build-arg CONF=nginx.shell.conf -t shop-shell .
#   docker build --build-arg APP=mfe-orders --build-arg CONF=nginx.remote.conf -t shop-mfe-orders .
FROM node:22-alpine AS build
ARG APP
WORKDIR /w
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build ${APP} \
 && if [ -f dist/${APP}/browser/federation.manifest.prod.json ]; then \
      mv dist/${APP}/browser/federation.manifest.prod.json dist/${APP}/browser/federation.manifest.json; \
    fi

FROM nginx:1.27-alpine
ARG APP
ARG CONF
COPY ${CONF} /etc/nginx/conf.d/default.conf
COPY --from=build /w/dist/${APP}/browser /usr/share/nginx/html
