# Hotel Booking System - Makefile
# =====================================

.PHONY: help build up down restart logs clean test dev prod

# Default target
help:
	@echo "Hotel Booking System - Docker Compose Commands"
	@echo "=============================================="
	@echo ""
	@echo "Development Commands:"
	@echo "  make dev          - Start development environment"
	@echo "  make dev-build    - Build and start development environment"
	@echo "  make dev-down     - Stop development environment"
	@echo ""
	@echo "Production Commands:"
	@echo "  make prod         - Start production environment"
	@echo "  make prod-build   - Build and start production environment"
	@echo "  make prod-down    - Stop production environment"
	@echo ""
	@echo "General Commands:"
	@echo "  make build        - Build all services"
	@echo "  make up           - Start all services (default)"
	@echo "  make down         - Stop all services"
	@echo "  make restart      - Restart all services"
	@echo "  make logs         - Show logs for all services"
	@echo "  make logs-f       - Follow logs for all services"
	@echo "  make clean        - Clean up containers, networks and volumes"
	@echo "  make test         - Run tests for all services"
	@echo ""
	@echo "Service-specific Commands:"
	@echo "  make logs-api     - Show API Gateway logs"
	@echo "  make logs-user    - Show User Service logs"
	@echo "  make logs-hotel   - Show Hotel Service logs"
	@echo "  make logs-booking - Show Booking Service logs"
	@echo "  make logs-search  - Show Search Service logs"
	@echo "  make logs-notify  - Show Notification Service logs"
	@echo ""
	@echo "Infrastructure Commands:"
	@echo "  make infra-up     - Start only infrastructure services"
	@echo "  make infra-down   - Stop only infrastructure services"
	@echo ""

# Development environment
dev:
	@echo "🚀 Starting development environment..."
	docker-compose -f docker-compose.yml -f docker-compose.dev.yml up

dev-build:
	@echo "🔨 Building and starting development environment..."
	docker-compose -f docker-compose.yml -f docker-compose.dev.yml up --build

dev-down:
	@echo "🛑 Stopping development environment..."
	docker-compose -f docker-compose.yml -f docker-compose.dev.yml down

# Production environment
prod:
	@echo "🚀 Starting production environment..."
	docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d

prod-build:
	@echo "🔨 Building and starting production environment..."
	docker-compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d

prod-down:
	@echo "🛑 Stopping production environment..."
	docker-compose -f docker-compose.yml -f docker-compose.prod.yml down

# General commands
build:
	@echo "🔨 Building all services..."
	docker-compose build

up:
	@echo "🚀 Starting all services..."
	docker-compose up

up-d:
	@echo "🚀 Starting all services in detached mode..."
	docker-compose up -d

down:
	@echo "🛑 Stopping all services..."
	docker-compose down

restart:
	@echo "🔄 Restarting all services..."
	docker-compose restart

# Logging
logs:
	@echo "📋 Showing logs for all services..."
	docker-compose logs

logs-f:
	@echo "📋 Following logs for all services..."
	docker-compose logs -f

logs-api:
	@echo "📋 Showing API Gateway logs..."
	docker-compose logs -f api-gateway

logs-user:
	@echo "📋 Showing User Service logs..."
	docker-compose logs -f user-service

logs-hotel:
	@echo "📋 Showing Hotel Service logs..."
	docker-compose logs -f hotel-service

logs-booking:
	@echo "📋 Showing Booking Service logs..."
	docker-compose logs -f booking-service

logs-search:
	@echo "📋 Showing Search Service logs..."
	docker-compose logs -f search-service

logs-notify:
	@echo "📋 Showing Notification Service logs..."
	docker-compose logs -f notification-service

# Infrastructure services only
infra-up:
	@echo "🚀 Starting infrastructure services..."
	docker-compose up -d postgres redis rabbitmq meilisearch

infra-down:
	@echo "🛑 Stopping infrastructure services..."
	docker-compose stop postgres redis rabbitmq meilisearch

# Cleaning
clean:
	@echo "🧹 Cleaning up containers, networks and volumes..."
	docker-compose down -v --remove-orphans
	docker system prune -f
	docker volume prune -f

clean-all:
	@echo "🧹 Cleaning up everything including images..."
	docker-compose down -v --remove-orphans --rmi all
	docker system prune -af
	docker volume prune -f

# Testing
test:
	@echo "🧪 Running tests for all services..."
	docker-compose -f docker-compose.yml -f docker-compose.dev.yml exec user-service ./gradlew test
	docker-compose -f docker-compose.yml -f docker-compose.dev.yml exec hotel-service ./gradlew test
	docker-compose -f docker-compose.yml -f docker-compose.dev.yml exec booking-service ./gradlew test

# Database operations
db-migrate:
	@echo "📊 Running database migrations..."
	docker-compose exec postgres psql -U hotel_user -d hotel_reservation -c "SELECT version();"

db-backup:
	@echo "💾 Creating database backup..."
	docker-compose exec postgres pg_dump -U hotel_user hotel_reservation > backup_$(shell date +%Y%m%d_%H%M%S).sql

# Health checks
health:
	@echo "🏥 Checking service health..."
	@echo "API Gateway: $$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health || echo "DOWN")"
	@echo "User Service: $$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator/health || echo "DOWN")"
	@echo "Hotel Service: $$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/actuator/health || echo "DOWN")"
	@echo "Booking Service: $$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/actuator/health || echo "DOWN")"
	@echo "Search Service: $$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8084/actuator/health || echo "DOWN")"
	@echo "Notification Service: $$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8085/actuator/health || echo "DOWN")"

# Monitoring
monitoring-up:
	@echo "📊 Starting monitoring services..."
	docker-compose up -d prometheus grafana

monitoring-down:
	@echo "📊 Stopping monitoring services..."
	docker-compose stop prometheus grafana