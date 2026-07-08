# KITA developer commands (SCAFFOLDING SKELETON).
# Targets are documented placeholders. They become fully functional as each service is
# implemented in later features. Running them now may no-op or print guidance.
.PHONY: help build up down test lint

help:
	@echo "KITA — available targets (scaffolding stage):"
	@echo "  make build   Build all service images (frontend, gateway, reference-service)"
	@echo "  make up      Start the full stack via Docker Compose"
	@echo "  make down    Stop the stack"
	@echo "  make test    Run all tests (frontend + backend) — added with the code"
	@echo "  make lint    Run lint/format checks (ESLint/Prettier + Spotless/Checkstyle)"

build:
	@echo "[scaffold] build: docker compose build   # enabled once services are implemented"
	# docker compose build

up:
	@echo "[scaffold] up: docker compose up -d       # enabled once services are implemented"
	# docker compose up -d

down:
	# docker compose down
	@echo "[scaffold] down: docker compose down"

test:
	@echo "[scaffold] test: no tests yet — added with the service implementations"
	# cd frontend && npm test
	# cd backend && ./gradlew test

lint:
	@echo "[scaffold] lint: config present; runs once sources exist"
	# cd frontend && npm run lint && npm run format
	# cd backend && ./gradlew spotlessCheck checkstyleMain
