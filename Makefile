# KITA developer commands.
.PHONY: help build up down test lint

help:
	@echo "KITA — available targets:"
	@echo "  make build   Build service images via Docker Compose"
	@echo "  make up      Start the full stack via Docker Compose"
	@echo "  make down    Stop the stack"
	@echo "  make test    Run backend tests (operations-service)"
	@echo "  make lint    Run backend format/style checks"

build:
	docker compose build

up:
	docker compose up -d

down:
	docker compose down

test:
	cd backend && ./gradlew test
	# frontend tests are added when the frontend app is implemented

lint:
	cd backend && ./gradlew spotlessCheck checkstyleMain
	# frontend lint (ESLint/Prettier) is added when the frontend app is implemented
