# 🔧 TAPO BACKEND - TECHNICAL SPECIFICATION DOCUMENT

**Phiên bản:** 1.0  
**Ngày tạo:** 2026-03-20  
**Framework:** Spring Boot 4.0.3  
**Java Version:** 21  
**Build Tool:** Maven  

---

## 1. Tổng Quan Kỹ Thuật

| Thành phần          | Công nghệ / Phiên bản                       |
|----------------------|----------------------------------------------|
| **Language**         | Java 21 (LTS)                                |
| **Framework**        | Spring Boot 4.0.3                            |
| **Build Tool**       | Maven + Maven Wrapper                        |
| **Database**         | PostgreSQL 15+ (Supabase)                    |
| **ORM**              | Spring Data JPA / Hibernate                  |
| **DB Migration**     | Flyway                                       |
| **Security**         | Spring Security 7.x + JWT + OAuth2           |
| **Validation**       | Spring Boot Starter Validation (Jakarta)     |
| **WebSocket**        | Spring WebSocket (STOMP + SockJS)            |
| **API Docs**         | SpringDoc OpenAPI (Swagger UI)               |
| **File Storage**     | Supabase Storage (S3 compatible)             |
| **Payment**          | PayOS Java SDK                               |
| **Logging**          | SLF4J + Logback                              |
| **Testing**          | JUnit 5 + Mockito + Spring Boot Test         |
| **Containerization** | Docker + Docker Compose                      |

---

## 2. Kiến Trúc Backend (Layered Architecture)

```
┌─────────────────────────────────────────────────────┐
│                  PRESENTATION LAYER                  │
│                                                     │
│  Controllers (REST)     WebSocket Handlers          │
│  @RestController        @MessageMapping             │
│  Request/Response DTO   STOMP Messages              │
│  Input Validation       Session Management          │
├─────────────────────────────────────────────────────┤
│                  BUSINESS LOGIC LAYER                │
│                                                     │
│  Services               Event Handlers              │
│  @Service               @EventListener              │
│  Business Rules         Async Processing            │
│  Transaction Mgmt       Scheduled Tasks             │
├─────────────────────────────────────────────────────┤
│                  DATA ACCESS LAYER                   │
│                                                     │
│  Repositories           Specifications              │
│  @Repository            Custom Queries              │
│  Spring Data JPA        Native SQL                  │
│  Flyway Migrations      Query DSL                   │
├─────────────────────────────────────────────────────┤
│                  CROSS-CUTTING CONCERNS              │
│                                                     │
│  Security (JWT/OAuth)   Exception Handling           │
│  Logging (SLF4J)        CORS Configuration          │
│  Caching                Rate Limiting               │
└─────────────────────────────────────────────────────┘
```

---

## 3. Cấu Trúc Package

```
src/main/java/backend/
├── BackendApplication.java                 # Main entry point
│
├── config/                                 # Configuration classes
│   ├── SecurityConfig.java                 # Spring Security configuration
│   ├── JwtConfig.java                      # JWT token configuration
│   ├── CorsConfig.java                     # CORS policy
│   ├── WebSocketConfig.java                # WebSocket STOMP configuration
│   ├── SwaggerConfig.java                  # OpenAPI documentation
│   ├── FlywayConfig.java                   # Database migration
│   └── SupabaseStorageConfig.java          # File storage configuration
│
├── security/                               # Security components
│   ├── JwtTokenProvider.java               # JWT token creation & validation
│   ├── JwtAuthenticationFilter.java        # JWT filter in security chain
│   ├── OAuth2SuccessHandler.java           # Google OAuth2 callback handler
│   ├── CustomUserDetails.java              # UserDetails implementation
│   └── CustomUserDetailsService.java       # Load user from DB
│
├── controller/                             # REST Controllers
│   ├── AuthController.java                 # POST /api/auth/**
│   ├── ProductController.java              # /api/products/**
│   ├── CategoryController.java             # /api/categories/**
│   ├── BrandController.java                # /api/brands/**
│   ├── CartController.java                 # /api/cart/**
│   ├── OrderController.java                # /api/orders/**
│   ├── PaymentController.java              # /api/payments/**
│   ├── InventoryController.java            # /api/inventory/**
│   ├── VoucherController.java              # /api/vouchers/**
│   ├── FlashSaleController.java            # /api/flash-sales/**
│   ├── ReviewController.java               # /api/reviews/**
│   ├── WishlistController.java             # /api/wishlist/**
│   ├── ChatController.java                 # WebSocket /ws/chat
│   ├── BlogController.java                 # /api/blogs/**
│   ├── UserController.java                 # /api/users/**
│   └── DashboardController.java            # /api/dashboard/**
│
├── service/                                # Business Logic
│   ├── AuthService.java
│   ├── ProductService.java
│   ├── CategoryService.java
│   ├── BrandService.java
│   ├── CartService.java
│   ├── OrderService.java
│   ├── PaymentService.java
│   ├── InventoryService.java
│   ├── VoucherService.java
│   ├── FlashSaleService.java
│   ├── ReviewService.java
│   ├── WishlistService.java
│   ├── ChatService.java
│   ├── BlogService.java
│   ├── UserService.java
│   ├── DashboardService.java
│   └── FileStorageService.java             # Supabase Storage upload/download
│
├── repository/                             # Data Access
│   ├── UserRepository.java
│   ├── AddressRepository.java
│   ├── ProductRepository.java
│   ├── CategoryRepository.java
│   ├── BrandRepository.java
│   ├── ProductImageRepository.java
│   ├── CartItemRepository.java
│   ├── OrderRepository.java
│   ├── OrderItemRepository.java
│   ├── OrderStatusHistoryRepository.java
│   ├── PaymentRepository.java
│   ├── ReturnRequestRepository.java
│   ├── InstallmentRequestRepository.java
│   ├── InventoryReceiptRepository.java
│   ├── InventoryReceiptItemRepository.java
│   ├── VoucherRepository.java
│   ├── FlashSaleRepository.java
│   ├── ReviewRepository.java
│   ├── WishlistRepository.java
│   ├── ChatRoomRepository.java
│   ├── ChatMessageRepository.java
│   ├── BlogCategoryRepository.java
│   └── BlogPostRepository.java
│
├── model/                                  # JPA Entities
│   ├── entity/
│   │   ├── User.java
│   │   ├── Address.java
│   │   ├── Product.java
│   │   ├── Category.java
│   │   ├── Brand.java
│   │   ├── ProductImage.java
│   │   ├── CartItem.java
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   ├── OrderStatusHistory.java
│   │   ├── Payment.java
│   │   ├── ReturnRequest.java
│   │   ├── InstallmentRequest.java
│   │   ├── InventoryReceipt.java
│   │   ├── InventoryReceiptItem.java
│   │   ├── Voucher.java
│   │   ├── FlashSale.java
│   │   ├── Review.java
│   │   ├── Wishlist.java
│   │   ├── ChatRoom.java
│   │   ├── ChatMessage.java
│   │   ├── BlogCategory.java
│   │   └── BlogPost.java
│   │
│   └── enums/
│       ├── UserRole.java                   # ADMIN, SALES_STAFF, WAREHOUSE_STAFF, CUSTOMER
│       ├── UserStatus.java                 # ACTIVE, LOCKED, DELETED
│       ├── AuthProvider.java               # LOCAL, GOOGLE
│       ├── ProductStatus.java              # ACTIVE, INACTIVE, DRAFT
│       ├── OrderStatus.java                # PENDING, CONFIRMED, PROCESSING, SHIPPING, DELIVERED, CANCELLED, RETURNED
│       ├── PaymentStatus.java              # UNPAID, PAID, REFUNDED, FAILED
│       ├── ReceiptType.java                # IMPORT, EXPORT
│       ├── DiscountType.java               # PERCENTAGE, FIXED_AMOUNT
│       ├── VoucherStatus.java              # ACTIVE, INACTIVE, EXPIRED
│       ├── FlashSaleStatus.java            # SCHEDULED, ACTIVE, ENDED
│       ├── ReviewStatus.java               # PENDING, APPROVED, REJECTED
│       ├── ChatRoomStatus.java             # OPEN, CLOSED
│       ├── BlogPostStatus.java             # DRAFT, PUBLISHED
│       ├── ReturnRequestStatus.java        # PENDING, APPROVED, REJECTED
│       └── InstallmentStatus.java          # PENDING, APPROVED, REJECTED
│
├── dto/                                    # Data Transfer Objects
│   ├── request/
│   │   ├── auth/
│   │   │   ├── LoginRequest.java
│   │   │   ├── RegisterRequest.java
│   │   │   └── RefreshTokenRequest.java
│   │   ├── product/
│   │   │   ├── CreateProductRequest.java
│   │   │   ├── UpdateProductRequest.java
│   │   │   └── ProductFilterRequest.java
│   │   ├── order/
│   │   │   ├── CreateOrderRequest.java
│   │   │   ├── UpdateOrderStatusRequest.java
│   │   │   └── ReturnOrderRequest.java
│   │   ├── cart/
│   │   │   ├── AddToCartRequest.java
│   │   │   └── UpdateCartItemRequest.java
│   │   ├── inventory/
│   │   │   └── CreateInventoryReceiptRequest.java
│   │   ├── promotion/
│   │   │   ├── CreateVoucherRequest.java
│   │   │   ├── ValidateVoucherRequest.java
│   │   │   └── CreateFlashSaleRequest.java
│   │   ├── review/
│   │   │   └── CreateReviewRequest.java
│   │   ├── chat/
│   │   │   └── ChatMessageRequest.java
│   │   ├── blog/
│   │   │   └── CreateBlogPostRequest.java
│   │   └── user/
│   │       ├── UpdateProfileRequest.java
│   │       ├── ChangePasswordRequest.java
│   │       └── CreateAddressRequest.java
│   │
│   └── response/
│       ├── ApiResponse.java                # Generic response wrapper
│       ├── PageResponse.java               # Paginated response wrapper
│       ├── auth/
│       │   └── AuthResponse.java           # JWT tokens + user info
│       ├── product/
│       │   ├── ProductListResponse.java
│       │   ├── ProductDetailResponse.java
│       │   └── ProductCompareResponse.java
│       ├── order/
│       │   ├── OrderListResponse.java
│       │   └── OrderDetailResponse.java
│       ├── cart/
│       │   └── CartResponse.java
│       ├── inventory/
│       │   ├── InventoryOverviewResponse.java
│       │   └── InventoryReceiptResponse.java
│       ├── promotion/
│       │   ├── VoucherResponse.java
│       │   └── FlashSaleResponse.java
│       ├── review/
│       │   └── ReviewResponse.java
│       ├── chat/
│       │   ├── ChatRoomResponse.java
│       │   └── ChatMessageResponse.java
│       ├── blog/
│       │   ├── BlogPostListResponse.java
│       │   └── BlogPostDetailResponse.java
│       ├── user/
│       │   └── UserProfileResponse.java
│       └── dashboard/
│           └── DashboardResponse.java
│
├── exception/                              # Exception Handling
│   ├── GlobalExceptionHandler.java         # @RestControllerAdvice
│   ├── ResourceNotFoundException.java      # 404
│   ├── BadRequestException.java            # 400
│   ├── UnauthorizedException.java          # 401
│   ├── ForbiddenException.java             # 403
│   ├── ConflictException.java              # 409
│   ├── PaymentException.java               # Payment related errors
│   └── InsufficientStockException.java     # Inventory related errors
│
├── util/                                   # Utility Classes
│   ├── SlugUtil.java                       # Generate URL-friendly slugs
│   ├── OrderCodeGenerator.java             # Generate order codes: TP-YYYYMMDD-XXXXX
│   ├── ReceiptCodeGenerator.java           # Generate receipt codes
│   └── FileValidationUtil.java             # Validate file type, size
│
└── scheduler/                              # Scheduled Tasks
    ├── FlashSaleScheduler.java             # Auto activate/end flash sales
    ├── OrderAutoCancel.java              # Auto cancel unpaid orders after 24h
    └── VoucherExpirationScheduler.java     # Auto expire vouchers
```

---

## 4. Dependencies (pom.xml)

### 4.1. Dependencies Cần Thêm

```xml
<dependencies>
    <!-- ===== CORE ===== -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>

    <!-- ===== DATABASE ===== -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>

    <!-- ===== SECURITY ===== -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.6</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.6</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.6</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>

    <!-- ===== PAYMENT ===== -->
    <dependency>
        <groupId>vn.payos</groupId>
        <artifactId>payos-java</artifactId>
        <version>1.0.3</version>
    </dependency>

    <!-- ===== API DOCS ===== -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.8.6</version>
    </dependency>

    <!-- ===== UTILITIES ===== -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>1.6.3</version>
    </dependency>

    <!-- ===== DEV TOOLS ===== -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-devtools</artifactId>
        <scope>runtime</scope>
        <optional>true</optional>
    </dependency>

    <!-- ===== TESTING ===== -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 5. Cấu Hình Application

### 5.1. application.yml
```yaml
spring:
  application:
    name: tapo-backend

  # Database
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000

  # JPA
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        jdbc:
          batch_size: 20

  # Flyway
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  # Security OAuth2
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: email,profile

  # File Upload
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 25MB

# JWT Configuration
jwt:
  secret: ${JWT_SECRET}
  access-token-expiration: 900000       # 15 phút (ms)
  refresh-token-expiration: 604800000   # 7 ngày (ms)

# PayOS Configuration
payos:
  client-id: ${PAYOS_CLIENT_ID}
  api-key: ${PAYOS_API_KEY}
  checksum-key: ${PAYOS_CHECKSUM_KEY}

# Supabase Storage
supabase:
  url: ${SUPABASE_URL}
  anon-key: ${SUPABASE_ANON_KEY}
  service-role-key: ${SUPABASE_SERVICE_ROLE_KEY}

# Server
server:
  port: 8080
  servlet:
    context-path: /

# Swagger
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui
```

### 5.2. Environment Variables
```properties
# Database (Supabase)
DB_HOST=aws-0-ap-southeast-1.pooler.supabase.com
DB_PORT=6543
DB_NAME=postgres
DB_USERNAME=postgres.xxxxxxxxxxxx
DB_PASSWORD=your_supabase_db_password

# JWT
JWT_SECRET=your_jwt_secret_key_at_least_256_bits

# Google OAuth2
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret

# PayOS
PAYOS_CLIENT_ID=your_payos_client_id
PAYOS_API_KEY=your_payos_api_key
PAYOS_CHECKSUM_KEY=your_payos_checksum_key

# Supabase Storage
SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_key
SUPABASE_SERVICE_ROLE_KEY=your_supabase_service_role_key
```

---

## 6. Entity Mapping Conventions

### 6.1. Base Entity
```java
@MappedSuperclass
@Getter @Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;
}
```

### 6.2. Entity Example (Product)
```java
@Entity
@Table(name = "products")
@Getter @Setter
public class Product extends BaseEntity {

    @Column(nullable = false, length = 500)
    private String name;

    @Column(nullable = false, unique = true, length = 600)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> specifications;

    @Column(name = "avg_rating", precision = 2, scale = 1)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "review_count")
    private Integer reviewCount = 0;

    @Column(name = "sold_count")
    private Integer soldCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.DRAFT;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images = new ArrayList<>();
}
```

---

## 7. Security Architecture

### 7.1. Request Flow
```
Client Request
    │
    ▼
┌──────────────────────┐
│   CORS Filter        │  ← Cho phép frontend domain
├──────────────────────┤
│   JWT Auth Filter    │  ← Extract & validate JWT token
├──────────────────────┤
│   SecurityContext    │  ← Set Authentication
├──────────────────────┤
│   @PreAuthorize      │  ← Check role permissions
├──────────────────────┤
│   Controller Method  │  ← Process request
└──────────────────────┘
```

### 7.2. JWT Token Structure
```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "user-uuid",
    "email": "user@example.com",
    "role": "CUSTOMER",
    "iat": 1711000000,
    "exp": 1711000900
  }
}
```

### 7.3. Endpoint Security Matrix
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigSource()))
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            // Public endpoints
            .requestMatchers(GET, "/api/products/**").permitAll()
            .requestMatchers(GET, "/api/categories/**").permitAll()
            .requestMatchers(GET, "/api/brands/**").permitAll()
            .requestMatchers(GET, "/api/blogs/**").permitAll()
            .requestMatchers(POST, "/api/auth/**").permitAll()
            .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()

            // Customer endpoints
            .requestMatchers("/api/cart/**").hasRole("CUSTOMER")
            .requestMatchers(POST, "/api/orders").hasRole("CUSTOMER")
            .requestMatchers("/api/wishlist/**").hasRole("CUSTOMER")
            .requestMatchers(POST, "/api/reviews").hasRole("CUSTOMER")

            // Staff endpoints
            .requestMatchers("/api/inventory/**").hasAnyRole("WAREHOUSE_STAFF", "ADMIN")
            .requestMatchers(PATCH, "/api/orders/*/status").hasAnyRole("SALES_STAFF", "ADMIN")
            .requestMatchers("/api/vouchers/**").hasAnyRole("SALES_STAFF", "ADMIN")

            // Admin only
            .requestMatchers(POST, "/api/products").hasRole("ADMIN")
            .requestMatchers(PUT, "/api/products/**").hasRole("ADMIN")
            .requestMatchers(DELETE, "/api/products/**").hasRole("ADMIN")
            .requestMatchers("/api/users/**").hasRole("ADMIN")
            .requestMatchers("/api/dashboard/**").hasRole("ADMIN")

            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

---

## 8. Error Handling Strategy

### 8.1. Global Exception Handler
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404).body(
            ApiResponse.error(404, ex.getMessage())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
            .toList();

        return ResponseEntity.badRequest().body(
            ApiResponse.validationError(errors)
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(500).body(
            ApiResponse.error(500, "Internal server error")
        );
    }
}
```

---

## 9. WebSocket Chat Architecture

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");  // Subscribe destinations
        config.setApplicationDestinationPrefixes("/app");  // Send prefix
        config.setUserDestinationPrefix("/user");  // User-specific messages
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("${FRONTEND_URL}")
                .withSockJS();
    }
}
```

**WebSocket Endpoints:**
| Direction | Destination              | Mô tả                            |
|-----------|--------------------------|-----------------------------------|
| SEND      | `/app/chat.send`         | Gửi tin nhắn                      |
| SEND      | `/app/chat.join`         | Tham gia/Tạo room                 |
| SUBSCRIBE | `/topic/room/{roomId}`   | Nhận tin nhắn trong room          |
| SUBSCRIBE | `/user/queue/notifications` | Nhận thông báo cá nhân         |

---

## 10. Docker Configuration

### 10.1. Dockerfile
```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:resolve
COPY src ./src
RUN ./mvnw package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 10.2. docker-compose.yml
```yaml
version: '3.8'
services:
  backend:
    build: .
    container_name: tapo-backend
    ports:
      - "8080:8080"
    env_file:
      - .env
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

---

## 11. API Conventions

### 11.1. URL Naming
```
GET    /api/{resource}              → Danh sách (paginated)
GET    /api/{resource}/{id}         → Chi tiết
POST   /api/{resource}              → Tạo mới
PUT    /api/{resource}/{id}         → Cập nhật toàn bộ
PATCH  /api/{resource}/{id}         → Cập nhật một phần
DELETE /api/{resource}/{id}         → Xóa

# Nested resources
GET    /api/products/{id}/reviews   → Reviews của sản phẩm
POST   /api/orders/{id}/return      → Yêu cầu hoàn trả đơn hàng

# Actions
PATCH  /api/orders/{id}/status      → Cập nhật trạng thái
POST   /api/vouchers/validate       → Kiểm tra voucher
```

### 11.2. Query Parameters
```
?page=0&size=20              → Pagination
?sort=price,asc              → Sorting
?category=uuid&brand=uuid    → Filtering
?search=macbook              → Search
?minPrice=10000000           → Range filter
```

### 11.3. Standard HTTP Status Codes
| Code | Meaning          | Sử dụng khi                        |
|------|------------------|-------------------------------------|
| 200  | OK               | GET, PUT, PATCH thành công          |
| 201  | Created          | POST tạo resource mới               |
| 204  | No Content       | DELETE thành công                   |
| 400  | Bad Request      | Validation lỗi                     |
| 401  | Unauthorized     | Chưa đăng nhập / token hết hạn     |
| 403  | Forbidden        | Không đủ quyền                     |
| 404  | Not Found        | Resource không tồn tại              |
| 409  | Conflict         | Trùng email, trùng review           |
| 500  | Internal Error   | Lỗi server không mong đợi          |

---

## 12. Testing Strategy

| Loại Test         | Công cụ                     | Phạm vi                              |
|-------------------|-----------------------------|---------------------------------------|
| **Unit Tests**    | JUnit 5 + Mockito           | Service layer logic                   |
| **Integration**   | Spring Boot Test + TestContainers | Repository + API endpoints       |
| **Security**      | Spring Security Test        | Auth & Role-based access              |
| **API**           | MockMvc                     | Controller endpoints                  |

### Test Structure
```
src/test/java/backend/
├── service/
│   ├── ProductServiceTest.java
│   ├── OrderServiceTest.java
│   └── ...
├── controller/
│   ├── ProductControllerTest.java
│   ├── AuthControllerTest.java
│   └── ...
└── repository/
    ├── ProductRepositoryTest.java
    └── ...
```
