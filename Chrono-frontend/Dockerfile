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

FROM alpine:3.23

RUN apk upgrade --no-cache \
    && apk add --no-cache nginx \
    && mkdir -p /usr/share/nginx/html \
    && chown -R nginx:nginx /usr/share/nginx/html

COPY --from=builder /frontend/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
USER nginx
CMD ["nginx", "-g", "daemon off;"]
