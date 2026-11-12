FROM node:24.15.0-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

COPY . .

RUN npm run build

# runs as a non-root user (uid 101) and listens on 8080, unlike the root-run nginx image
FROM nginxinc/nginx-unprivileged:1.31.3-alpine

COPY --from=builder /app/build /usr/share/nginx/html

USER 101

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
