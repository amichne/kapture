# syntax=docker/dockerfile:1.6

FROM node:20-alpine AS base
WORKDIR /app

COPY package.json ./
RUN npm install --production

COPY src ./src
COPY openapi.json ./openapi.json
COPY resources ./resources

ENV NODE_ENV=production
EXPOSE 8080
CMD ["node", "src/server.js"]
