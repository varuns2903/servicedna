FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .

# Build-time only: Vite bakes these into the static bundle, so they must point to wherever the
# browser (not other containers) can reach the backend — by default that's the host's published
# port, which is why these default to localhost rather than the "backend" service DNS name.
ARG VITE_API_URL=http://localhost:8080/api/v1
ARG VITE_AUTH_LOGIN_URL=http://localhost:8080/oauth2/authorization/github
ARG VITE_SSO_LOGIN_URL=http://localhost:8080/oauth2/authorization/oidc
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_AUTH_LOGIN_URL=$VITE_AUTH_LOGIN_URL
ENV VITE_SSO_LOGIN_URL=$VITE_SSO_LOGIN_URL

RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
