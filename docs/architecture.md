# KITA Architecture & Repository Layout

**Status: scaffolding.** This document describes the structure created by feature
`002-source-scaffold` and the architecture it targets. No application code exists yet.

## Runtime architecture (target)

```
Cloud Load Balancer (TLS, DNS, custom domain)   ← feature 001
        │
        ▼
Nginx edge  ── serves built React static assets, HTTP caching/compression, proxies /api/*
        │
        ▼
Spring Cloud Gateway  ── routes /api/* to backend microservices (future: auth, rate limiting)
        │
        ▼
Spring Boot microservices  ── one reference-service now; costing/inventory/accounting later
        │
        ▼
PostgreSQL  ── one managed DB per environment; Flyway migrations per service
```

Only the Nginx/frontend and the gateway are publicly reachable; backend services are private.

## Directory tree

```
frontend/                     React + TS + Vite, served by Nginx
  src/  tests/                (placeholders)
  package.json  tsconfig.json  vite.config.ts  .eslintrc.json  .prettierrc.json
  nginx.conf  Dockerfile
backend/
  settings.gradle.kts         declares :gateway and :reference-service
  build.gradle.kts            root toolchain + Spotless/Checkstyle
  config/checkstyle.xml
  gateway/                    Spring Cloud Gateway (module skeleton)
  reference-service/          TEMPLATE microservice (module skeleton)
    src/main/resources/db/migration/   Flyway location (empty)
contracts/openapi.yaml        API contract (source of truth)
docs/                         this file + add-a-service + quickstart
docker-compose.yml            service graph (skeleton)
Makefile                      build/up/test/lint (skeletons)
README.md  .gitignore  .env.example
```

## Placeholder convention

Every directory that would otherwise be empty contains a `.gitkeep` so the structure is
preserved in version control. Files that imply behavior (Makefile targets, Dockerfiles,
compose) are commented skeletons — they define the shape but do not build or run a real
application in this feature.

## What comes next

Later per-service features implement (TDD) the reference microservice (+ Flyway migrations),
the gateway routing, and the React UI, then activate the Makefile/compose into a buildable,
runnable, tested system. Deployment is owned by feature `001-multi-cloud-cicd`.
