# Cấu Trúc Chức Năng Cốt Lõi - Backend (Spring Boot)

## 1. Giới Thiệu
Backend của dự án TAPO được xây dựng trên nền tảng **Java 21** và **Spring Boot 3.x**. Vai trò của Backend là cung cấp hệ thống RESTful APIs ổn định, bảo mật cao, phục vụ cho Frontend Website cũng như các nền tảng mở rộng sau này. Cơ sở dữ liệu sử dụng là **PostgreSQL** (chạy trên Supabase).

## 2. Danh Sách Các Chức Năng Backend (Modules)

### 2.1. Module Xác Thực & Bảo Mật (Auth & Security)
- **JSON Web Token (JWT)**: Cấp phát Access Token (15 phút) và Refresh Token (7 ngày).
- **Phân quyền (RBAC)**: Bảo vệ và xác thực Request thông qua Spring Security (Filter Chains, `@PreAuthorize`). Hỗ trợ 5 Roles: GUEST, CUSTOMER, SALES_STAFF, WAREHOUSE_STAFF, ADMIN.
- **Mã hoá Mật Khẩu**: Sử dụng BCrypt.
- **Tích Hợp OAuth2**: Hỗ trợ Login with Google thông qua Google Auth API.

### 2.2. Module Quản Lý Danh Mục và Sản Phẩm (Catalog & Product API)
- Lưu trữ cấu trúc JSON linh hoạt của thông số kỹ thuật (`specifications`) bằng `hibernate-types`.
- Cung cấp tính năng Full-text search (sử dụng PostgreSQL tsvector hoặc JPA Specification).
- Bộ lọc đa chiều: lọc theo khoá ngoại (Category, Brand) và lọc theo khoảng giá, trạng thái.

### 2.3. Module Quản Lý Đơn Hàng & Thanh Toán (Order & Payment)
- **State Machine Đơn Hàng**: Quản lý ràng buộc khắt khe khi chuyển đổi trạng thái (Từ PENDING -> CONFIRMED -> SHIPPING -> DELIVERED).
- **Khoá Tồn Kho (Pessimistic Locking)**: Xử lý Concurrent Requests (Race conditions) khi 2 người cùng mua sản phẩm sát số lượng tồn, tránh lỗi kho âm.
- **Tích hợp Webhook PayOS**: Verify HTTP Signature từ request của PayOS nhằm tránh giả mạo thanh toán.

### 2.4. Module Dữ Liệu Thời Gian Thực (Real-time Chat)
- Ứng dụng **Spring WebSocket** kết hợp **STOMP** message broker (In-memory pub/sub).
- Quản lý các Subscriptions và truyền tin rẽ nhánh từ nhân viên đến người dùng cuối. Thông báo trạng thái Typing.

### 2.5. Module Kho Hàng (Inventory API)
- Ghi log (History log) chi tiết qua Phiếu Nhập & Phiếu Xuất. Mọi tác động đến tổng số `stock` của product đều có dấu vết lưu trữ.

### 2.6. Module Báo Cáo Thống Kê (Dashboard/Reporting)
- Sử dụng các lệnh Query gộp, Window Functions, Aggregate (SUM, COUNT, GROUP BY DATE) trực tiếp trong Repository hoặc JPQL.
- Xuất dữ liệu biểu đồ hiệu năng, doanh số, top sản phẩm.

## 3. Kiến Trúc Mã Nguồn (Clean Code)
Áp dụng mô hình Controller - Service - Repository truyền thống nhưng tuân thủ nghiêm ngặt Separation of Concerns:
- **`Controller/`**: Validate Request DTO, rẽ nhánh endpoint. KHÔNG chứa Bussiness logic.
- **`Service/`**: Nơi gánh vác nghiệp vụ chính (Tính tiền, call PayOS, tính tồn kho).
- **`Repository/`**: Spring Data JPA Interfaces.
- **`Mapper/`**: MapStruct để ánh xạ qua lại Entity và DTO.
