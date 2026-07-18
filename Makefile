# KITA developer commands.
.PHONY: help build up down clean ps logs test lint

help:
	@echo "KITA — available targets:"
	@echo "  make build   Build service images via Docker Compose"
	@echo "  make up      Start the full backend stack (build + detached)"
	@echo "  make down    Stop the stack (KEEPS data volumes)"
	@echo "  make clean   Stop the stack and REMOVE data volumes (clean slate)"
	@echo "  make ps      Show stack status"
	@echo "  make logs    Tail stack logs"
	@echo "  make test    Run backend tests"
	@echo "  make lint    Run backend format/style checks"

build:
	docker compose build

up:
	docker compose up -d --build

down:
	docker compose down

clean:
	docker compose down -v

ps:
	docker compose ps

logs:
	docker compose logs -f

test:
	cd backend && ./gradlew test

lint:
	cd backend && ./gradlew spotlessCheck checkstyleMain
