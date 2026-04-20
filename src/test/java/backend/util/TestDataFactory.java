package backend.util;

import backend.dto.auth.LoginRequest;
import backend.dto.auth.RegisterRequest;
import backend.dto.auth.TokenRefreshRequest;
import backend.dto.order.CreateOrderRequest;
import backend.dto.review.CreateReviewRequest;
import backend.dto.voucher.ValidateVoucherRequest;
import backend.model.entity.*;
import backend.model.enums.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Central test factory — provides consistent, reusable mock objects.
 * All builder methods accept overrides via setter-style chaining after creation.
 *
 * Pattern: getDefault*() returns a fully-populated but detached entity (no id unless set).
 * SOLID-DIP: tests depend on this factory abstraction, not hardcoded data.
 */
public final class TestDataFactory {

    private TestDataFactory() {}

    // ── User ─────────────────────────────────────────────────────────────────────

    public static User activeUser() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("test@tapo.vn");
        u.setFullName("Test User");
        u.setPasswordHash("$2a$10$hashedPassword");
        u.setRole(UserRole.CUSTOMER);
        u.setStatus(UserStatus.ACTIVE);
        u.setAvatarUrl("https://cdn.tapo.vn/avatar/default.png");
        return u;
    }

    public static User pendingUser() {
        User u = activeUser();
        u.setEmail("pending@tapo.vn");
        u.setStatus(UserStatus.PENDING_VERIFICATION);
        return u;
    }

    public static User lockedUser() {
        User u = activeUser();
        u.setEmail("locked@tapo.vn");
        u.setStatus(UserStatus.LOCKED);
        return u;
    }

    // ── Auth DTOs ────────────────────────────────────────────────────────────────

    public static RegisterRequest registerRequest() {
        return new RegisterRequest("newuser@tapo.vn", "password123", "New User");
    }

    public static RegisterRequest registerRequest(String email) {
        return new RegisterRequest(email, "password123", "New User");
    }

    public static LoginRequest loginRequest() {
        return new LoginRequest("test@tapo.vn", "password123");
    }

    public static TokenRefreshRequest tokenRefreshRequest(String accessToken, String refreshToken) {
        // record fields: (accessToken, refreshToken)
        return new TokenRefreshRequest(accessToken, refreshToken);
    }

    // ── Product ──────────────────────────────────────────────────────────────────

    public static Product product(int stock) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName("MacBook Pro 14");
        p.setSlug("macbook-pro-14");
        p.setPrice(BigDecimal.valueOf(35_000_000));
        p.setStock(stock);
        p.setSoldCount(0);
        p.setStatus(ProductStatus.ACTIVE);
        p.setThumbnailUrl("https://cdn.tapo.vn/products/macbook-pro.jpg");
        p.setDeleted(false);
        return p;
    }

    // ── CartItem ─────────────────────────────────────────────────────────────────

    public static CartItem cartItem(User user, Product product, int quantity) {
        CartItem ci = new CartItem();
        ci.setId(UUID.randomUUID());
        ci.setUser(user);
        ci.setProduct(product);
        ci.setQuantity(quantity);
        return ci;
    }

    // ── Address ──────────────────────────────────────────────────────────────────

    public static Address address(User user) {
        Address a = new Address();
        a.setId(UUID.randomUUID());
        a.setUser(user);
        a.setRecipientName("Nguyen Van A");
        a.setPhoneNumber("0901234567");
        a.setAddress("123 Nguyen Hue");
        a.setCity("Ho Chi Minh");
        a.setIsDefault(true);
        return a;
    }

    // ── Order DTOs ───────────────────────────────────────────────────────────────

    public static CreateOrderRequest createOrderRequest(UUID addressId) {
        return new CreateOrderRequest(addressId, null, null, "COD");
    }

    /** @param voucherCode optional voucher code */
    public static CreateOrderRequest createOrderRequest(UUID addressId, String voucherCode) {
        // record fields: (addressId, customerNote, voucherCode, paymentMethod)
        return new CreateOrderRequest(addressId, null, voucherCode, "COD");
    }

    // ── Voucher ──────────────────────────────────────────────────────────────────

    /** An active, valid FIXED_AMOUNT voucher (50k off, min order 200k, no cap, limit 100) */
    public static Voucher fixedVoucher() {
        Voucher v = new Voucher();
        v.setId(UUID.randomUUID());
        v.setCode("SAVE50K");
        v.setName("Giảm 50,000đ");
        v.setDiscountType(DiscountType.FIXED_AMOUNT);
        v.setDiscountValue(BigDecimal.valueOf(50_000));
        v.setMaxDiscountAmount(null);
        v.setMinimumOrderValue(BigDecimal.valueOf(200_000));
        v.setUsageLimit(100);
        v.setUsageCount(0);
        v.setStartDate(Instant.now().minusSeconds(3600));
        v.setEndDate(Instant.now().plusSeconds(86400));
        v.setStatus(VoucherStatus.ACTIVE);
        return v;
    }

    /** An active, valid PERCENTAGE voucher (10% off, max 100k, min order 500k) */
    public static Voucher percentageVoucher() {
        Voucher v = new Voucher();
        v.setId(UUID.randomUUID());
        v.setCode("PCTOFF10");
        v.setName("Giảm 10%");
        v.setDiscountType(DiscountType.PERCENTAGE);
        v.setDiscountValue(BigDecimal.valueOf(10));
        v.setMaxDiscountAmount(BigDecimal.valueOf(100_000));
        v.setMinimumOrderValue(BigDecimal.valueOf(500_000));
        v.setUsageLimit(null);   // unlimited
        v.setUsageCount(0);
        v.setStartDate(Instant.now().minusSeconds(3600));
        v.setEndDate(Instant.now().plusSeconds(86400));
        v.setStatus(VoucherStatus.ACTIVE);
        return v;
    }

    public static ValidateVoucherRequest validateVoucherRequest(String code, BigDecimal subtotal) {
        return new ValidateVoucherRequest(code, subtotal);
    }

    // ── Review ───────────────────────────────────────────────────────────────────

    public static Review pendingReview(User user, Product product, Order order) {
        Review r = new Review();
        r.setId(UUID.randomUUID());
        r.setUser(user);
        r.setProduct(product);
        r.setOrder(order);
        r.setRating(5);
        r.setComment("Sản phẩm tuyệt vời!");
        r.setImages(new ArrayList<>());
        r.setStatus(ReviewStatus.PENDING);
        return r;
    }

    public static CreateReviewRequest createReviewRequest(UUID productId, UUID orderId) {
        return new CreateReviewRequest(productId, orderId, 5, "Sản phẩm tuyệt vời!", new ArrayList<>());
    }

    // ── Order (entity) ───────────────────────────────────────────────────────────

    public static Order pendingOrder(User user) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setOrderCode("TP" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        o.setUser(user);
        o.setShippingRecipientName("Nguyen Van A");
        o.setShippingPhone("0901234567");
        o.setShippingAddress("123 Nguyen Hue");
        o.setShippingCity("Ho Chi Minh");
        o.setSubtotal(BigDecimal.valueOf(35_000_000));
        o.setDiscountAmount(BigDecimal.ZERO);
        o.setShippingFee(BigDecimal.valueOf(30_000));
        o.setTotalAmount(BigDecimal.valueOf(35_030_000));
        o.setStatus(OrderStatus.PENDING);
        o.setPaymentStatus(PaymentStatus.UNPAID);
        o.setPaymentMethod("COD");
        return o;
    }

    public static Order orderWithStatus(User user, OrderStatus status) {
        Order o = pendingOrder(user);
        o.setStatus(status);
        return o;
    }
}
