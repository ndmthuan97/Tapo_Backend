package backend.service;

import backend.dto.common.CustomCode;
import backend.dto.voucher.CreateVoucherRequest;
import backend.dto.voucher.ValidateVoucherRequest;
import backend.dto.voucher.ValidateVoucherResponse;
import backend.dto.voucher.VoucherDto;
import backend.exception.AuthException;
import backend.model.entity.Voucher;
import backend.model.enums.DiscountType;
import backend.model.enums.VoucherStatus;
import backend.repository.VoucherRepository;
import backend.service.impl.VoucherServiceImpl;
import backend.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("VoucherService — Unit Tests")
class VoucherServiceImplTest {

    @Mock VoucherRepository voucherRepo;

    @InjectMocks
    VoucherServiceImpl voucherService;

    // ══════════════════════════════════════════════════════════════════════════════
    // VALIDATE
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("validate()")
    class ValidateTests {

        @Test
        @DisplayName("valid voucher code → returns discount response")
        void validate_success() {
            Voucher v = TestDataFactory.fixedVoucher();
            given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(v));

            ValidateVoucherRequest req = TestDataFactory.validateVoucherRequest(
                    "SAVE50K", BigDecimal.valueOf(500_000));

            ValidateVoucherResponse response = voucherService.validate(req);

            assertThat(response.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(50_000));
        }

        @Test
        @DisplayName("non-existent code → throw VOUCHER_NOT_FOUND")
        void validate_notFound() {
            given(voucherRepo.findByCode(any())).willReturn(Optional.empty());

            ValidateVoucherRequest req = TestDataFactory.validateVoucherRequest(
                    "INVALID", BigDecimal.valueOf(500_000));

            assertThatThrownBy(() -> voucherService.validate(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.VOUCHER_NOT_FOUND));
        }

        @Test
        @DisplayName("INACTIVE voucher → throw VOUCHER_INACTIVE")
        void validate_inactive() {
            Voucher v = TestDataFactory.fixedVoucher();
            v.setStatus(VoucherStatus.INACTIVE);
            given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(v));

            ValidateVoucherRequest req = TestDataFactory.validateVoucherRequest(
                    "SAVE50K", BigDecimal.valueOf(500_000));

            assertThatThrownBy(() -> voucherService.validate(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.VOUCHER_INACTIVE));
        }

        @Test
        @DisplayName("voucher before startDate → throw VOUCHER_NOT_STARTED")
        void validate_notStarted() {
            Voucher v = TestDataFactory.fixedVoucher();
            v.setStartDate(Instant.now().plusSeconds(3600)); // future
            given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(v));

            assertThatThrownBy(() -> voucherService.validate(
                    TestDataFactory.validateVoucherRequest("SAVE50K", BigDecimal.valueOf(500_000))))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.VOUCHER_NOT_STARTED));
        }

        @Test
        @DisplayName("expired voucher → throw VOUCHER_EXPIRED")
        void validate_expired() {
            Voucher v = TestDataFactory.fixedVoucher();
            v.setEndDate(Instant.now().minusSeconds(3600)); // past
            given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(v));

            assertThatThrownBy(() -> voucherService.validate(
                    TestDataFactory.validateVoucherRequest("SAVE50K", BigDecimal.valueOf(500_000))))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.VOUCHER_EXPIRED));
        }

        @Test
        @DisplayName("validate subtotal < minimumOrderValue → throw VOUCHER_MIN_ORDER")
        void validate_belowMinOrder() {
            Voucher v = TestDataFactory.fixedVoucher(); // min 200k
            given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(v));

            assertThatThrownBy(() -> voucherService.validate(
                    TestDataFactory.validateVoucherRequest("SAVE50K", BigDecimal.valueOf(100_000))))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.VOUCHER_MIN_ORDER));
        }

        @Test
        @DisplayName("usageCount >= usageLimit → throw VOUCHER_USAGE_LIMIT")
        void validate_usageLimitReached() {
            Voucher v = TestDataFactory.fixedVoucher();
            v.setUsageLimit(10);
            v.setUsageCount(10); // exhausted
            given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(v));

            assertThatThrownBy(() -> voucherService.validate(
                    TestDataFactory.validateVoucherRequest("SAVE50K", BigDecimal.valueOf(500_000))))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.VOUCHER_USAGE_LIMIT));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // COMPUTE DISCOUNT (via validate)
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Discount Computation")
    class DiscountTests {

        @Test
        @DisplayName("FIXED_AMOUNT: discount equals configured fixed value")
        void discount_fixedAmount() {
            Voucher v = TestDataFactory.fixedVoucher(); // 50k off
            given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(v));

            ValidateVoucherResponse resp = voucherService.validate(
                    TestDataFactory.validateVoucherRequest("SAVE50K", BigDecimal.valueOf(1_000_000)));

            assertThat(resp.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(50_000));
        }

        @Test
        @DisplayName("FIXED_AMOUNT: discount capped at subtotal when subtotal is small")
        void discount_fixedAmount_cappedBySubtotal() {
            Voucher v = TestDataFactory.fixedVoucher();
            v.setDiscountValue(BigDecimal.valueOf(300_000)); // 300k off
            v.setMinimumOrderValue(BigDecimal.ZERO);
            given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(v));

            // subtotal only 200k
            ValidateVoucherResponse resp = voucherService.validate(
                    TestDataFactory.validateVoucherRequest("SAVE50K", BigDecimal.valueOf(200_000)));

            assertThat(resp.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(200_000));
        }

        @Test
        @DisplayName("PERCENTAGE: subtotal 1M x 10% = 100k (equals cap)")
        void discount_percentage_equalsMaxCap() {
            Voucher v = TestDataFactory.percentageVoucher(); // 10%, max 100k
            given(voucherRepo.findByCode("PCTOFF10")).willReturn(Optional.of(v));

            ValidateVoucherResponse resp = voucherService.validate(
                    TestDataFactory.validateVoucherRequest("PCTOFF10", BigDecimal.valueOf(1_000_000)));

            assertThat(resp.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        }

        @Test
        @DisplayName("PERCENTAGE: result capped by maxDiscountAmount")
        void discount_percentage_cappedByMaxDiscount() {
            Voucher v = TestDataFactory.percentageVoucher(); // 10%, max 100k
            given(voucherRepo.findByCode("PCTOFF10")).willReturn(Optional.of(v));

            // subtotal 5M → 10% = 500k → capped at 100k
            ValidateVoucherResponse resp = voucherService.validate(
                    TestDataFactory.validateVoucherRequest("PCTOFF10", BigDecimal.valueOf(5_000_000)));

            assertThat(resp.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        }

        @Test
        @DisplayName("PERCENTAGE with no maxCap → full percentage applied")
        void discount_percentage_noMaxCap() {
            Voucher v = TestDataFactory.percentageVoucher();
            v.setMaxDiscountAmount(null); // no cap
            given(voucherRepo.findByCode("PCTOFF10")).willReturn(Optional.of(v));

            ValidateVoucherResponse resp = voucherService.validate(
                    TestDataFactory.validateVoucherRequest("PCTOFF10", BigDecimal.valueOf(5_000_000)));

            // 5M × 10% = 500k
            assertThat(resp.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(500_000));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // APPLY VOUCHER
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("applyVoucher()")
    class ApplyVoucherTests {

        @Test
        @DisplayName("applyVoucher → increments usageCount by 1")
        void applyVoucher_incrementsUsageCount() {
            Voucher v = TestDataFactory.fixedVoucher();
            v.setUsageCount(5);
            given(voucherRepo.save(v)).willReturn(v);

            voucherService.applyVoucher(v, BigDecimal.valueOf(500_000));

            assertThat(v.getUsageCount()).isEqualTo(6);
            verify(voucherRepo).save(v);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // CRUD
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("create() & update() & toggleStatus()")
    class CrudTests {

        @Test
        @DisplayName("create new voucher → returns VoucherDto with status ACTIVE")
        void create_success() {
            CreateVoucherRequest req = new CreateVoucherRequest(
                    "NEWCODE", "New Voucher", DiscountType.FIXED_AMOUNT,
                    BigDecimal.valueOf(20_000), null, BigDecimal.valueOf(100_000),
                    50, Instant.now().minusSeconds(60), Instant.now().plusSeconds(86400));

            given(voucherRepo.findByCode("NEWCODE")).willReturn(Optional.empty());
            Voucher saved = TestDataFactory.fixedVoucher();
            saved.setCode("NEWCODE");
            given(voucherRepo.save(any())).willReturn(saved);

            VoucherDto result = voucherService.create(req);

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(VoucherStatus.ACTIVE);
        }

        @Test
        @DisplayName("create with duplicate code → throw VOUCHER_CODE_EXISTS")
        void create_duplicateCode() {
            Voucher existing = TestDataFactory.fixedVoucher();
            given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(existing));

            CreateVoucherRequest req = new CreateVoucherRequest(
                    "SAVE50K", "Dup", DiscountType.FIXED_AMOUNT,
                    BigDecimal.valueOf(10_000), null, BigDecimal.ZERO,
                    null, Instant.now(), Instant.now().plusSeconds(86400));

            assertThatThrownBy(() -> voucherService.create(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.VOUCHER_CODE_EXISTS));
        }

        @Test
        @DisplayName("toggleStatus ACTIVE → INACTIVE")
        void toggleStatus_activeToInactive() {
            Voucher v = TestDataFactory.fixedVoucher(); // ACTIVE
            given(voucherRepo.findById(v.getId())).willReturn(Optional.of(v));
            given(voucherRepo.save(v)).willReturn(v);

            VoucherDto result = voucherService.toggleStatus(v.getId());

            assertThat(result.status()).isEqualTo(VoucherStatus.INACTIVE);
        }

        @Test
        @DisplayName("toggleStatus INACTIVE → ACTIVE")
        void toggleStatus_inactiveToActive() {
            Voucher v = TestDataFactory.fixedVoucher();
            v.setStatus(VoucherStatus.INACTIVE);
            given(voucherRepo.findById(v.getId())).willReturn(Optional.of(v));
            given(voucherRepo.save(v)).willReturn(v);

            VoucherDto result = voucherService.toggleStatus(v.getId());

            assertThat(result.status()).isEqualTo(VoucherStatus.ACTIVE);
        }

        @Test
        @DisplayName("update to a code already taken by another voucher → throw VOUCHER_CODE_EXISTS")
        void update_changeCode_takenByOther() {
            Voucher target = TestDataFactory.fixedVoucher();
            target.setCode("OLD_CODE");
            Voucher other = TestDataFactory.percentageVoucher();
            other.setCode("OTHER_CODE");

            given(voucherRepo.findById(target.getId())).willReturn(Optional.of(target));
            given(voucherRepo.findByCode("OTHER_CODE")).willReturn(Optional.of(other));

            CreateVoucherRequest req = new CreateVoucherRequest(
                    "OTHER_CODE", "Updated", DiscountType.FIXED_AMOUNT,
                    BigDecimal.valueOf(10_000), null, BigDecimal.ZERO,
                    null, Instant.now(), Instant.now().plusSeconds(86400));

            assertThatThrownBy(() -> voucherService.update(target.getId(), req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.VOUCHER_CODE_EXISTS));
        }
    }
}
