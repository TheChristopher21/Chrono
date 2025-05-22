# ---- STAGE 1: Build Frontend ----
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
COPY . .

RUN npm install --legacy-peer-deps
RUN npm run build:prod

# ---- STAGE 2: Nginx serving ----
FROM nginx:alpine

COPY --from=builder /frontend/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
