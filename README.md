# Hotel Booking Microservices

A modern, cloud-native hotel reservation system built with Spring Boot microservices architecture.

## Architecture

**Microservices:**
- **API Gateway** (8080) - Routing, authentication, rate limiting
- **User Service** (8081) - User management, JWT authentication  
- **Hotel Service** (8082) - Hotel and room management
- **Booking Service** (8083) - Reservations with optimistic locking
- **Search Service** (8084) - Hotel search with Meilisearch
- **Notification Service** (8085) - Async email notifications

**Infrastructure:**
- **PostgreSQL** (5432) - Primary database
- **Redis** (6379) - Caching and sessions
- **RabbitMQ** (5672/15672) - Message queue
- **Meilisearch** (7700) - Search engine
- **Prometheus** (9090) - Metrics
- **Grafana** (3001) - Monitoring dashboards

## Quick Start

### Prerequisites
- Docker 20.10+
- Docker Compose 2.0+
- 8GB RAM, 10GB disk space

### Development
```bash
# Clone repository
git clone <repository-url>
cd hotel-booking-microservices

# Start development environment
make dev

# Or using docker-compose directly
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up
```

### Production
```bash
# Start production environment
make prod

# Or using docker-compose directly  
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Available Commands

```bash
make dev         # Start development environment
make dev-build   # Build and start development
make prod        # Start production environment
make prod-build  # Build and start production
make down        # Stop all services
make logs        # View logs
make health      # Check service health
make clean       # Clean up containers and volumes
```

## Health Checks

```bash
curl http://localhost:8080/actuator/health  # API Gateway
curl http://localhost:8081/actuator/health  # User Service
curl http://localhost:8082/actuator/health  # Hotel Service
curl http://localhost:8083/actuator/health  # Booking Service
curl http://localhost:8084/actuator/health  # Search Service
curl http://localhost:8085/actuator/health  # Notification Service
```

## Monitoring

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3001 (admin/admin123)
- **RabbitMQ Management**: http://localhost:15672 (hotel_user/hotel_pass)

## Database Schema

Single PostgreSQL database with service-specific schemas:
- `user_svc` - User accounts and authentication
- `hotel_svc` - Hotels, room types, user favorites
- `booking_svc` - Bookings and room inventory
- `search_svc` - Search history

## Key Features

### Security
- JWT authentication with configurable secrets
- BCrypt password hashing
- Role-based access control
- API rate limiting and CORS

### Performance
- Redis caching for frequently accessed data
- Optimistic locking for inventory management
- Async processing with RabbitMQ
- Full-text search with Meilisearch

### Reliability
- Event-driven architecture
- Health checks and monitoring
- Graceful error handling
- Transaction integrity

## Development

### Build All Services
```bash
./gradlew build
```

### Run Tests
```bash
./gradlew test
```

### Run Single Service Locally
```bash
./gradlew :services:user-service:bootRun
```

## API Endpoints

All endpoints are accessed through the API Gateway at `http://localhost:8080/api/v1/`

### Authentication
- `POST /auth/register` - User registration
- `POST /auth/login` - User login
- `GET /auth/profile` - Get user profile

### Hotels
- `GET /hotels` - List hotels with filters
- `GET /hotels/{id}` - Get hotel details
- `GET /hotels/{id}/rooms` - Get room types

### Bookings
- `POST /bookings` - Create booking
- `GET /bookings/{id}` - Get booking details
- `PUT /bookings/{id}/cancel` - Cancel booking
- `GET /bookings/user/{userId}` - Get user bookings

### Search
- `GET /search/hotels` - Search hotels
- `GET /search/suggestions` - Get search suggestions

## Environment Configuration

Create `.env` file for custom configuration:

```env
# JWT Configuration
JWT_SECRET=your-secret-key

# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=hotel_reservation
DB_USER=hotel_user
DB_PASSWORD=hotel_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=hotel_redis_pass

# Email Configuration
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# Search Engine
MEILISEARCH_HOST=localhost
MEILISEARCH_PORT=7700
MEILISEARCH_API_KEY=your-master-key
```

## Production Deployment

### Using Docker Images
```bash
# Build and push images
make build
docker tag hotel-booking-user-service:latest your-registry/user-service:v1.0.0
docker push your-registry/user-service:v1.0.0

# Deploy with production compose
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### Production Checklist
- [ ] Configure strong JWT secrets
- [ ] Set up SSL/TLS certificates
- [ ] Configure database backups
- [ ] Set up log aggregation
- [ ] Configure monitoring alerts
- [ ] Review resource limits
- [ ] Secure RabbitMQ and Redis

## Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.2, Spring Cloud Gateway
- **Database**: PostgreSQL 15
- **Cache**: Redis 7
- **Message Queue**: RabbitMQ 3
- **Search**: Meilisearch 1.x
- **Build**: Gradle 8.x
- **Monitoring**: Prometheus, Grafana
- **Containerization**: Docker, Docker Compose

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.