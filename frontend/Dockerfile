# Production web image: builds the React app, then Caddy serves it and proxies /api to the Spring Boot app.
# Built by .github/workflows/deploy.yml; the Caddyfile is mounted from deploy/Caddyfile at runtime.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM caddy:2
COPY --from=build /app/dist /srv
