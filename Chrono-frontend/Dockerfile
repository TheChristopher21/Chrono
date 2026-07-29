FROM node:20-bookworm AS builder

RUN apt-get update && apt-get install -y \
    python3 \
    build-essential \
    libpcsclite-dev \
    libusb-dev \
    libudev-dev \
    pcscd \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /frontend
COPY package.json package-lock.json ./
RUN npm ci --legacy-peer-deps

COPY . .

RUN npm run audit:prod
RUN npm test
RUN npm run build:prod

FROM nginxinc/nginx-unprivileged:1.28.0-alpine

COPY --from=builder /frontend/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
