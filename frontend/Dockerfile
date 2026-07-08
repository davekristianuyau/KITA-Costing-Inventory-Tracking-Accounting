# Frontend image (SCAFFOLDING SKELETON): multi-stage build — Node builds static assets,
# Nginx serves them and proxies /api to the gateway. App code is added in a later feature.

# --- build stage ---
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json ./
# RUN npm install            # enabled once app sources exist
COPY . .
# RUN npm run build          # produces /app/dist

# --- runtime stage ---
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/nginx.conf
# COPY --from=build /app/dist /usr/share/nginx/html   # enabled once build produces dist
EXPOSE 8080
# HEALTHCHECK to be added with a static health location.
CMD ["nginx", "-g", "daemon off;"]
