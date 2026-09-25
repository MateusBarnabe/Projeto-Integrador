FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Um processo só: o servidor Node entrega a página (dist) e a API /api/landing na 8088
FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY server server
COPY --from=build /app/dist dist
EXPOSE 8088
CMD ["node", "server/index.ts"]
