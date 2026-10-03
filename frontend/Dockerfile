FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .

# Build-time only: Vite bakes these into the static bundle, so they must point to wherever the
# browser (not other containers) can reach the backend. The defaults are same-origin paths, for
# a reverse proxy or Kubernetes Ingress that routes /api, /oauth2, /login/oauth2 and /ws to the
# backend; docker-compose passes the backend's published localhost port instead.
ARG VITE_API_URL=/api/v1
ARG VITE_AUTH_LOGIN_URL=/oauth2/authorization/github
ARG VITE_SSO_LOGIN_URL=/oauth2/authorization/oidc
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_AUTH_LOGIN_URL=$VITE_AUTH_LOGIN_URL
ENV VITE_SSO_LOGIN_URL=$VITE_SSO_LOGIN_URL

RUN npm run build

# Unprivileged nginx (uid 101) on port 8080.
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
