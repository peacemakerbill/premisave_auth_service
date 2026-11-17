# Deploying the auth-service

One Docker image runs everywhere. What makes an environment development, staging or production is only the
environment variables, above all `SPRING_PROFILES_ACTIVE`.

| Environment | Profile | Branch | Image tag | Data |
|---|---|---|---|---|
| development | `dev` | `develop` | `development` | throw-away, on your machine or a free plan |
| staging | `staging` | `staging` | `staging` | its own database, never production's |
| production | `prod` | `main` | `production`, or a version such as `1.0.0` | managed MongoDB with backups |

Files in this repository:

| File | What it is for |
|---|---|
| `Dockerfile`, `docker/entrypoint.sh` | The image. Builds with Maven, runs Java 21, listens on `PORT` (or 8080) |
| `docker-compose.yml` | Development: the service with its own MongoDB and Redis |
| `docker-compose.staging.yml`, `docker-compose.production.yml` (in this folder) | One server with Docker |
| `docker-compose.coolify.yml` | Coolify (any stage) |
| `render.yaml` | Render: development, staging and production in one Blueprint |
| `heroku.yml`, `app.json` (these two stay in the repository root, Heroku only reads them there) | Heroku (container stack) |
| `fly.staging.toml`, `fly.production.toml` | Fly.io |
| `railway.json` | Railway |
| `deploy/env/*.env.example` | The values each environment needs |
| `.github/workflows/docker-image.yml` | Builds the image and publishes it to ghcr.io |
| `src/main/resources/application-{dev,staging,prod}.yml` | Logging and error settings per profile |

## What every environment needs

The service starts only when these are set. Everything else has a default (see `.env.example`).

| Variable | Notes |
|---|---|
| `MONGODB_URI` | MongoDB Atlas address, or the bundled MongoDB in compose |
| `REDIS_HOST` and `REDIS_PORT`, or `SPRING_DATA_REDIS_URL` | The URL form carries a password and `rediss://` for TLS. Hosting platforms give you the URL |
| `JWT_SECRET` | Long random base64 value. **The messenger service must use the same one** |
| `API_KEY` | Shared key for internal calls. The messenger calls it `INTERNAL_API_KEY` |
| `FRONTEND_URL`, `BACKEND_URL` | Public addresses, used in email links |
| `GMAIL_USERNAME`, `GMAIL_PASSWORD` | Mail sender and its App Password |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Profile pictures |

Make secrets with `openssl rand -base64 32` (JWT) and `openssl rand -hex 32` (API key). Use different values
in staging and production. Never commit a filled-in env file: `deploy/env/*.env` is git-ignored.

Health check for every platform: `GET /health` (public, returns `{"status":"UP"}`).

## Development on your machine

```bash
docker compose up --build
```

The service is on http://localhost:8080. MongoDB and Redis start with it. Without a `.env` file the service
uses development defaults, so email and profile pictures stay off until you copy `.env.example` to `.env`
and add the Gmail and Cloudinary values.

## A server with Docker (staging and production)

```bash
cp deploy/env/staging.env.example deploy/env/staging.env      # fill it in
docker compose --env-file deploy/env/staging.env -f deploy/docker-compose.staging.yml up -d --build
```

For production the image is pulled by an exact version and MongoDB is external:

```bash
cp deploy/env/production.env.example deploy/env/production.env
docker login ghcr.io                          # once, with a GitHub token that can read packages
IMAGE_TAG=1.0.0 docker compose --env-file deploy/env/production.env -f deploy/docker-compose.production.yml pull
IMAGE_TAG=1.0.0 docker compose --env-file deploy/env/production.env -f deploy/docker-compose.production.yml up -d
```

The service is published on `127.0.0.1:8080`. Put nginx, Caddy or Traefik in front for https and set
`SECURITY_TRUSTED_PROXIES` to the proxy's address. Documents live in the `auth-data` volume: back it up.

## Coolify

1. Create the project, then one Coolify **environment** per stage (development, staging, production).
2. In each: New resource, Git repository, Build Pack **Docker Compose**, branch for that stage,
   Base Directory `/deploy`, Docker Compose Location `/docker-compose.coolify.yml`.
3. Open Environment Variables. Fill the required ones and set `SPRING_PROFILES_ACTIVE` to `dev`, `staging` or `prod`.
4. Give the `auth` service a domain, then deploy.

The file starts MongoDB and Redis beside the service. In production set `MONGODB_URI` to an Atlas address.
Coolify keeps the volumes between deploys. The domain is also available to other resources as `SERVICE_URL_AUTH`.

Without compose: use the **Dockerfile** build pack, add a Redis resource, a MongoDB resource (or Atlas), set the
same variables and add a persistent storage mounted at `/data`. Set the health check path to `/health`.

## Render

1. New, **Blueprint**, pick this repository. Open **Blueprint settings** and set the file path to `deploy/render.yaml` (Render looks for the root otherwise). Render then reads it and creates the three environments.
2. Fill in the secret values it asks for. `JWT_SECRET` must equal the messenger's.
3. `MONGODB_URI` is an Atlas address: Render has no MongoDB.

Development uses the free plan: no disk (documents are lost on restart) and the service sleeps when idle.
Staging and production have a disk at `/data`. Production does not deploy by itself: use Manual Deploy, or a
deploy hook, once staging is good. Plan names and the region are in the file.

## Heroku

Staging and production are two apps in one pipeline.

```bash
heroku create premisave-auth-staging    --stack container
heroku create premisave-auth-production --stack container
heroku pipelines:create premisave-auth -a premisave-auth-staging    --stage staging
heroku pipelines:add    premisave-auth -a premisave-auth-production --stage production

heroku config:set -a premisave-auth-staging $(grep -v '^#' deploy/env/staging.env | grep -v '^$' | xargs)
heroku config:set -a premisave-auth-production $(grep -v '^#' deploy/env/production.env | grep -v '^$' | xargs)

heroku git:remote -a premisave-auth-staging && git push heroku main     # build and run on staging
heroku pipelines:promote -a premisave-auth-staging                      # the same build, to production
```

Two limits to know before choosing Heroku:

- **No file storage.** The disk is wiped on every restart, so home owner document uploads do not last.
- **Redis.** Use Upstash or Redis Cloud and put its `rediss://` address in `SPRING_DATA_REDIS_URL`.
  Heroku's own Key-Value Store uses a self-signed certificate that the service rejects.

## Fly.io

```bash
fly apps create premisave-auth-staging
fly volumes create premisave_auth_staging --region jnb --size 5 -a premisave-auth-staging
fly secrets import -a premisave-auth-staging < deploy/env/staging.env
fly deploy . --config deploy/fly.staging.toml --dockerfile Dockerfile
```

Production is the same with `deploy/fly.production.toml` and `premisave-auth`. Fly's Redis address goes in
`SPRING_DATA_REDIS_URL`. The volume keeps the documents.

## Railway

Create one Railway **environment** per stage in the project. Add MongoDB and Redis from the templates, deploy this
repository (open the service settings, Config as code, and set the file path to `/deploy/railway.json`) and add a volume mounted at `/data`. Variables per environment, for example:

```
SPRING_PROFILES_ACTIVE=staging
MONGODB_URI=${{MongoDB.MONGO_URL}}
SPRING_DATA_REDIS_URL=${{Redis.REDIS_URL}}
```

## Any other platform

The image is `ghcr.io/peacemakerbill/premisave_auth_service:<tag>` (see the workflow), or build the
`Dockerfile`. Then:

1. Send traffic to the container port from the `PORT` variable (8080 when it is not set).
2. Set the variables above. Choose the profile with `SPRING_PROFILES_ACTIVE`.
3. Health check: `GET /health`. Allow about 90 seconds for the first start.
4. Mount a persistent volume at `/data` if you use home owner applications.
5. Reachable MongoDB and Redis. Redis needs `SPRING_DATA_REDIS_URL` when it has a password or TLS.
6. Set `SECURITY_TRUSTED_PROXIES` to the addresses of the platform's router, or every caller looks like one address.

## Publishing the image

`.github/workflows/docker-image.yml` runs on GitHub: a push to `develop`, `staging` or `main` publishes the tag
for that stage, and a tag such as `v1.0.0` publishes `1.0.0`. In GitHub, open the package once and allow the
repository to read it, or make it public. Pull requests only test that the image builds.

## When something is wrong

| Sign | Cause |
|---|---|
| Stops at start with `Could not resolve placeholder 'JWT_SECRET'` (or another name) | That required variable is missing |
| `Connection refused` to `localhost:27017` or `6379` | The address still points at `localhost`. Inside a container that is the container itself |
| Health check fails on a small plan for the first minutes | Java is still starting. Give it 90 seconds, or a plan with at least 512 MB |
| Everyone looks like the same IP and gets rate limited together | `SECURITY_TRUSTED_PROXIES` does not include the platform's router |
| Document upload answers 503 | No writable `/data`. Mount a volume or disk (see `docs/STORAGE.md`) |
| Sign in works but the cookie is dropped | The site is on http. Use https, or set `SECURITY_DEVICE_COOKIE_SECURE=false` for development only |
