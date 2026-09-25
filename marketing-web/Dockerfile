FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_BASE_PATH=/modulos/marketing/
ENV VITE_BASE_PATH=${VITE_BASE_PATH}
RUN npm run build

# nginx serve o build no caminho base e repassa /api/marketing para a API
FROM nginx:1.29-alpine
ARG VITE_BASE_PATH=/modulos/marketing/
ENV BASE_PATH=${VITE_BASE_PATH} \
    API_UPSTREAM=http://marketing:8087
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html${VITE_BASE_PATH}
EXPOSE 3007
