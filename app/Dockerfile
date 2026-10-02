# Build context: app/
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Caddy serves the static build (with SPA fallback) and reverse-proxies /api and /media to the
# api container - see Caddyfile. It also terminates TLS, so there's no separate nginx/certbot.
FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
