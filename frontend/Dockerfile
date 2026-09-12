FROM node:22-alpine AS build
WORKDIR /build

# npm ci, not npm install: it installs exactly what package-lock.json pins, so the
# image can't quietly pick up a different dependency version than you tested with.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# The built app is static files. nginx serves them and proxies /api to the backend,
# which puts the SPA and the API on one origin — so the session cookie and the CSRF
# cookie behave in production exactly as they do behind the Vite dev proxy.
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
