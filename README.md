# Tapo Backend — Spring Boot API

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_Boot-4.0.3-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white"/>
  <img src="https://img.shields.io/badge/PostgreSQL-Supabase-4169E1?style=for-the-badge&logo=postgresql&logoColor=white"/>
  <img src="https://img.shields.io/badge/Redis-Upstash-DC382D?style=for-the-badge&logo=redis&logoColor=white"/>
  <img src="https://img.shields.io/badge/Docker-Nginx-2496ED?style=for-the-badge&logo=docker&logoColor=white"/>
</p>

---

## Overview

RESTful API backend for the TAPO laptop e-commerce platform. Provides authentication, product management, order processing, real-time chat, payment integration, and admin tooling.

---

## Tech Stack

| Technology | Version | Purpose |
|-----------|---------|---------|
| Java | 21 | Language (virtual threads ready) |
| Spring Boot | 4.0.3 | Framework |
| Spring Security | — | Auth filters, RBAC |
| JJWT | 0.12.6 | JWT access + refresh tokens |
| Spring OAuth2 Client | — | Google login |
| Spring Data JPA (Hibernate) | — | ORM / repositories |
| Spring WebSocket (STOMP) | — | Real-time chat |
| Spring AMQP | — | RabbitMQ async messaging (optional) |
| Flyway | — | Database schema migrations |
| Redis (Lettuce / Upstash) | — | Refresh token store, rate limiting |
| Bucket4j | 8.10.1 | Token-bucket rate limiting per IP |
| PayOS SDK | 1.0.2 | Vietnamese payment gateway |
| Apache POI | 5.3.0 | Excel report export |
| MapStruct | 1.6.3 | Entity ↔ DTO mapping |
| Lombok | 1.18.36 | Boilerplate reduction |
| SpringDoc OpenAPI | 2.8.6 | Swagger UI at `/swagger-ui` |
| Spring Actuator | — | `/actuator/health` for keep-alive |
| dotenv-java | 3.0.0 | `.env` file loading |
| Docker + Nginx | — | Containerised deployment |

---

## Project Structure

```
src/
├── main/
│   ├── java/backend/
│   │   ├── BackendApplication.java
│   │   ├── config/          # SecurityConfig, WebSocketConfig, RedisConfig, CorsConfig...
│   │   ├── constants/       # AppConstants
│   │   ├── controller/      # 23 REST controllers
│   │   │   ├── AuthController.java
│   │   │   ├── ProductController.java
│   │   │   ├── OrderController.java
│   │   │   ├── ReviewController.java
│   │   │   ├── VoucherController.java
│   │   │   ├── FlashSaleController.java
│   │   │   ├── InventoryController.java
│   │   │   ├── ChatController.java + ChatMessageHandler.java
│   │   │   ├── StatisticsController.java
│   │   │   ├── UserController.java
│   │   │   ├── PaymentController.java
│   │   │   ├── BlogController.java
│   │   │   ├── ReturnRequestController.java
│   │   │   ├── WishlistController.java
│   │   │   ├── BannerController.java
│   │   │   ├── CartController.java
│   │   │   ├── CategoryController.java
│   │   │   ├── BrandController.java
│   │   │   ├── ContactController.java
│   │   │   ├── EmailVerificationController.java
│   │   │   ├── FileUploadController.java
│   │   │   └── SitemapController.java
│   │   ├── dto/             # Request + Response DTOs (per feature)
│   │   ├── exception/       # GlobalExceptionHandler, AppException, AuthException
│   │   ├── filter/          # JwtAuthFilter, RateLimitFilter
│   │   ├── model/
│   │   │   ├── entity/      # JPA entities (UUID PKs)
│   │   │   └── enums/       # OrderStatus, UserRole, VoucherStatus...
│   │   ├── repository/      # Spring Data JPA repositories
│   │   ├── security/        # JwtTokenProvider, CustomUserDetailsService
│   │   ├── service/         # Service interfaces
│   │   │   └── impl/        # Service implementations
│   │   └── util/            # Helper utilities
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-prod.yml
│       └── db/migration/    # Flyway SQL migrations
└── test/
    └── java/backend/
        ├── flow/            # Integration flow tests (AuthFlowTest, OrderFlowTest...)
        ├── service/         # Service unit tests
        └── controller/      # Controller unit tests (standalone MockMvc)
```

---

## API Endpoints (Summary)

| Module | Base Path |
|--------|-----------|
| Auth | `POST /api/auth/register`, `/login`, `/logout`, `/refresh`, `/forgot-password` |
| Products | `GET /api/products`, `GET /api/products/{slug}`, admin CRUD |
| Categories / Brands | `GET /api/categories`, `GET /api/brands` |
| Cart | `GET/POST/DELETE /api/cart` |
| Orders | `POST /api/orders`, `GET /api/orders/my`, admin list/update |
| Reviews | `GET/POST /api/reviews`, admin approve/reject/bulk |
| Vouchers | `POST /api/vouchers/validate`, admin CRUD |
| Flash Sale | `GET /api/flash-sales/active`, admin CRUD |
| Inventory | Admin import/export, stock adjustments |
| Chat | WebSocket `/ws`, REST history |
| Statistics | `GET /api/admin/statistics/*` |
| Payment | `POST /api/payment/create-link`, `POST /api/payment/webhook` |
| Blog | `GET /api/blogs`, admin CRUD |
| Return Requests | `POST /api/returns`, admin process |
| Wishlist | `GET/POST/DELETE /api/wishlist` |
| Users | `GET /api/users/me`, admin user management |
| Files | `POST /api/upload` (Supabase Storage) |
| Banners | Admin CRUD banners |
| Sitemap | `GET /sitemap.xml` |

Full docs: `http://localhost:8080/swagger-ui` after starting the server.

---

## Running Locally

### Prerequisites
- Java 21+
- Maven 3.9+ (or use `./mvnw`)
- Docker & Docker Compose
- Supabase project (PostgreSQL + Storage)
- Upstash Redis (TLS)

### With Docker (recommended)

```bash
cp .env.example .env
# Fill in all values in .env

# Start Nginx + Spring Boot + Redis
docker compose up -d

# Optional: include RabbitMQ
docker compose --profile rabbit up -d

# View logs
docker compose logs backend -f
```

Nginx listens on **port 80** and proxies to the backend container on 8080. The backend port is **not** exposed externally.

### Without Docker

```bash
cp .env.example .env
# Requires a local/remote Redis instance

./mvnw spring-boot:run
# API available at http://localhost:8080
```

---

## Environment Variables

Copy `.env.example` to `.env` and fill in:

| Variable | Description |
|----------|-------------|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | Supabase PostgreSQL connection |
| `JWT_SECRET` | ≥256-bit secret for signing JWTs |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth2 credentials |
| `PAYOS_CLIENT_ID` / `PAYOS_API_KEY` / `PAYOS_CHECKSUM_KEY` | PayOS payment gateway |
| `SUPABASE_URL` / `SUPABASE_ANON_KEY` / `SUPABASE_SERVICE_ROLE_KEY` | Supabase Storage |
| `REDIS_URL` | Upstash Redis TLS URL (`rediss://...`) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Gmail SMTP (App Password) |
| `ALLOWED_ORIGINS` | CORS allowed frontend URLs |
| `APP_BASE_URL` | Frontend base URL (used in email links) |
| `SPRING_PROFILES_ACTIVE` | `dev` or `prod` |
| `KEEP_ALIVE_ENABLED` | `true` to enable `/actuator/health` pings |

---

## Testing

```bash
# Run all 173 tests (~30s, no infra needed)
./mvnw test

# Flow integration tests only
./mvnw test -Dtest="*FlowTest"

# Specific test class
./mvnw test -Dtest="OrderFlowTest"
```

Tests use **Mockito + MockMvc standalone** — no Spring context, no Redis, no database required.

**Test coverage:**

| Suite | Tests | Covers |
|-------|-------|--------|
| `AuthFlowTest` | 10 | Register, login guards, token lifecycle, logout, forgot-password |
| `OrderFlowTest` | 9 | Order creation, stock deduction, state machine, cancel |
| `ReviewFlowTest` | 8 | Eligibility, submit, approve/reject, stats recalculation |
| `VoucherFlowTest` | 8 | Fixed/percentage discount, all validation edge cases, toggle status |
| `*ServiceImplTest` | ~80 | Unit tests per service method |
| `*ControllerTest` | ~58 | HTTP routing, status codes, response shape |

---

## Docker Services

| Service | Image | Description |
|---------|-------|-------------|
| `nginx` | `nginx:1.27-alpine` | Reverse proxy, public port 80 |
| `backend` | Built from `Dockerfile` | Spring Boot app, internal port 8080 |
| `redis` | `redis:7-alpine` | Cache + token store, 256 MB max |
| `rabbitmq` | `rabbitmq:3.13-management` | Optional async messaging (`--profile rabbit`) |

---

## Database

- **PostgreSQL 15+** hosted on Supabase
- **UUID** primary keys throughout
- **Flyway** manages all schema migrations (`src/main/resources/db/migration/`)
- **23 REST controllers**, data modelled across entities: User, Product, Category, Brand, Order, OrderItem, Review, Voucher, FlashSale, Inventory, Chat, Blog, ReturnRequest, Wishlist, Banner, Contact, Address...
