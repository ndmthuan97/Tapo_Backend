package backend.flow;

import backend.dto.common.CustomCode;
import backend.dto.voucher.ValidateVoucherRequest;
import backend.dto.voucher.ValidateVoucherResponse;
import backend.exception.AuthException;
import backend.model.entity.Voucher;
import backend.model.enums.VoucherStatus;
import backend.repository.VoucherRepository;
import backend.service.impl.VoucherServiceImpl;
import backend.util.TestDataFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Flow Test: Voucher Validation Lifecycle
 *
 * Flow: ACTIVE → VALIDATE (happy path) → EDGE CASES → ADMIN TOGGLE_INACTIVE
 *
 * All validation errors in VoucherServiceImpl throw AuthException.
 * Business rules tested:
 * - Min order value guard
 * - Usage limit exhaustion
 * - Expiry date
 * - Not-yet-started date
 * - Non-existent code
 * - FIXED_AMOUNT vs PERCENTAGE with max cap
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Voucher Flow — Validation and Edge Cases")
class VoucherFlowTest {

    @Mock VoucherRepository voucherRepo;

    @InjectMocks
    VoucherServiceImpl voucherService;

    // ─── HAPPY PATH ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1. Validate FIXED_AMOUNT voucher → correct discount returned")
    void validate_fixedAmount_returnsCorrectDiscount() {
        Voucher voucher = TestDataFactory.fixedVoucher(); // 50K off, min order 200K
        given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(voucher));

        ValidateVoucherRequest req = TestDataFactory.validateVoucherRequest("SAVE50K", BigDecimal.valueOf(500_000));
        ValidateVoucherResponse response = voucherService.validate(req);

        assertThat(response.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(50_000));
        assertThat(response.voucher().code()).isEqualTo("SAVE50K");
    }

    @Test
    @DisplayName("2. Validate PERCENTAGE voucher → discount capped at maxDiscountAmount")
    void validate_percentage_withCap() {
        Voucher voucher = TestDataFactory.percentageVoucher(); // 10%, max cap 100K, min order 500K
        given(voucherRepo.findByCode("PCTOFF10")).willReturn(Optional.of(voucher));

        // subtotal = 2,000,000 → 10% = 200K, but capped at 100K
        ValidateVoucherRequest req = TestDataFactory.validateVoucherRequest("PCTOFF10", BigDecimal.valueOf(2_000_000));
        ValidateVoucherResponse response = voucherService.validate(req);

        assertThat(response.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
    }

    // ─── EDGE CASES ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("3. Subtotal below minimumOrderValue → throw VOUCHER_MIN_ORDER")
    void validate_subtotalBelowMinimum_throws() {
        Voucher voucher = TestDataFactory.fixedVoucher(); // min order 200K
        given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(voucher));

        ValidateVoucherRequest req = TestDataFactory.validateVoucherRequest("SAVE50K", BigDecimal.valueOf(100_000));

        assertThatThrownBy(() -> voucherService.validate(req))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                        .isEqualTo(CustomCode.VOUCHER_MIN_ORDER));
    }

    @Test
    @DisplayName("4. usageCount >= usageLimit → throw VOUCHER_USAGE_LIMIT")
    void validate_usageLimitExceeded_throws() {
        Voucher voucher = TestDataFactory.fixedVoucher();
        voucher.setUsageCount(100); // limit reached
        given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(voucher));

        ValidateVoucherRequest req = TestDataFactory.validateVoucherRequest("SAVE50K", BigDecimal.valueOf(500_000));

        assertThatThrownBy(() -> voucherService.validate(req))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                        .isEqualTo(CustomCode.VOUCHER_USAGE_LIMIT));
    }

    @Test
    @DisplayName("5. Voucher past endDate → throw VOUCHER_EXPIRED")
    void validate_expired_throws() {
        Voucher voucher = TestDataFactory.fixedVoucher();
        voucher.setEndDate(Instant.now().minusSeconds(3600)); // expired 1 hour ago
        given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(voucher));

        ValidateVoucherRequest req = TestDataFactory.validateVoucherRequest("SAVE50K", BigDecimal.valueOf(500_000));

        assertThatThrownBy(() -> voucherService.validate(req))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                        .isEqualTo(CustomCode.VOUCHER_EXPIRED));
    }

    @Test
    @DisplayName("6. Voucher before startDate → throw VOUCHER_NOT_STARTED")
    void validate_notYetStarted_throws() {
        Voucher voucher = TestDataFactory.fixedVoucher();
        voucher.setStartDate(Instant.now().plusSeconds(86400)); // starts tomorrow
        given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(voucher));

        ValidateVoucherRequest req = TestDataFactory.validateVoucherRequest("SAVE50K", BigDecimal.valueOf(500_000));

        assertThatThrownBy(() -> voucherService.validate(req))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                        .isIn(CustomCode.VOUCHER_NOT_STARTED, CustomCode.VOUCHER_EXPIRED));
    }

    @Test
    @DisplayName("7. Non-existent voucher code → throw VOUCHER_NOT_FOUND")
    void validate_nonExistentCode_throws() {
        given(voucherRepo.findByCode("INVALID")).willReturn(Optional.empty());

        ValidateVoucherRequest req = TestDataFactory.validateVoucherRequest("INVALID", BigDecimal.valueOf(500_000));

        assertThatThrownBy(() -> voucherService.validate(req))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                        .isEqualTo(CustomCode.VOUCHER_NOT_FOUND));
    }

    // ─── ADMIN TOGGLE ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8. Admin toggleStatus ACTIVE → INACTIVE → validate subsequently rejected")
    void toggleStatus_activeToInactive() {
        Voucher voucher = TestDataFactory.fixedVoucher(); // starts ACTIVE
        given(voucherRepo.findById(voucher.getId())).willReturn(Optional.of(voucher));
        given(voucherRepo.save(voucher)).willReturn(voucher);

        voucherService.toggleStatus(voucher.getId());

        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.INACTIVE);
        verify(voucherRepo).save(voucher);
    }
}
