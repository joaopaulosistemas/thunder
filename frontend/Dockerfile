FROM node:22 AS build
ARG GIT_SHA=dev
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN GIT_SHA=${GIT_SHA} node scripts/gen-version.mjs src/app/version.ts && npx ng build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
