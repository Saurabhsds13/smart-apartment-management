# syntax=docker/dockerfile:1
#
# Societix frontend (Vite + React + TypeScript) development container image.
#
# For LOCAL development the compose stack runs the Vite dev server (hot reload) in this
# container — matching design.md "Local Development" ("the frontend (Vite dev server)").
# Production serves the built static assets same-origin behind the reverse proxy
# (design "Deployment Architecture"); that build is produced by `npm run build` and is
# out of scope for this dev image.
#
# Pinned Node LTS base (no `latest`) for reproducibility.
FROM node:20-bookworm-slim AS dev
WORKDIR /app

# Install dependencies from the lockfile first so this layer caches across source edits.
# `npm ci` requires package-lock.json and installs exactly the pinned versions.
COPY package.json package-lock.json ./
RUN npm ci

# Source is bind-mounted in compose for hot reload; copying here keeps the image usable
# standalone as well.
COPY . .

# Vite dev server port (see vite.config.ts `server.port`).
EXPOSE 5173

# Bind to 0.0.0.0 so the dev server is reachable from the host via the published port.
# VITE_BACKEND_ORIGIN (from the environment) points the /api proxy at the backend service.
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
