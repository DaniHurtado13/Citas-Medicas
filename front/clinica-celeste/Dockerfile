# ETAPA 1
FROM node:22-slim AS construccion

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

COPY . .

RUN npm run build


# ETAPA 2
FROM caddy:2.10

COPY --from=construccion /app/dist/clinica-celeste/browser /srv
