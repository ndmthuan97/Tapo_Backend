package backend.service.impl;

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
import backend.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepo;

    // ── Mapping ──────────────────────────────────────────────────────────────────

    private VoucherDto toDto(Voucher v) {
        return new VoucherDto(
                v.getId(), v.getCode(), v.getName(),
                v.getDiscountType(), v.getDiscountValue(),
                v.getMaxDiscountAmount(), v.getMinimumOrderValue(),
                v.getUsageLimit(), v.getUsageCount(),
                v.getStartDate(), v.getEndDate(), v.getStatus()
        );
    }

    // ── Validate and compute discount ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ValidateVoucherResponse validate(ValidateVoucherRequest request) {
        Voucher v = voucherRepo.findByCode(request.code().trim().toUpperCase())
                .orElseThrow(() -> new AuthException(CustomCode.VOUCHER_NOT_FOUND));

        Instant now = Instant.now();

        if (v.getStatus() == VoucherStatus.INACTIVE) {
            throw new AuthException(CustomCode.VOUCHER_INACTIVE);
        }
        if (now.isBefore(v.getStartDate())) {
            throw new AuthException(CustomCode.VOUCHER_NOT_STARTED);
        }
        if (now.isAfter(v.getEndDate())) {
            throw new AuthException(CustomCode.VOUCHER_EXPIRED);
        }
        if (request.subtotal().compareTo(v.getMinimumOrderValue()) < 0) {
            throw new AuthException(CustomCode.VOUCHER_MIN_ORDER);
        }
        if (v.getUsageLimit() != null && v.getUsageCount() >= v.getUsageLimit()) {
            throw new AuthException(CustomCode.VOUCHER_USAGE_LIMIT);
        }

        BigDecimal discount = computeDiscount(v, request.subtotal());
        return new ValidateVoucherResponse(toDto(v), discount);
    }

    /**
     * Called internally from OrderServiceImpl to apply a voucher to an order.
     * Returns the validated Voucher entity (to be set on Order) and the discount amount.
     */
    @Transactional
    public BigDecimal applyVoucher(Voucher v, BigDecimal subtotal) {
        BigDecimal discount = computeDiscount(v, subtotal);
        v.setUsageCount(v.getUsageCount() + 1);
        voucherRepo.save(v);
        return discount;
    }

    private BigDecimal computeDiscount(Voucher v, BigDecimal subtotal) {
        if (v.getDiscountType() == DiscountType.FIXED_AMOUNT) {
            // Can't reduce more than the subtotal
            return v.getDiscountValue().min(subtotal);
        } else { // PERCENTAGE
            BigDecimal raw = subtotal
                    .multiply(v.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            if (v.getMaxDiscountAmount() != null) {
                raw = raw.min(v.getMaxDiscountAmount());
            }
            return raw;
        }
    }

    // ── Admin CRUD ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<VoucherDto> listAll(Pageable pageable) {
        return voucherRepo.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<VoucherDto> listActive(Pageable pageable) {
        Instant now = Instant.now();
        return voucherRepo
                .findByStatusAndStartDateBeforeAndEndDateAfter(VoucherStatus.ACTIVE, now, now, pageable)
                .map(this::toDto);
    }

    @Transactional
    public VoucherDto create(CreateVoucherRequest req) {
        if (voucherRepo.findByCode(req.code().trim().toUpperCase()).isPresent()) {
            throw new AuthException(CustomCode.VOUCHER_CODE_EXISTS);
        }
        Voucher v = new Voucher();
        v.setCode(req.code().trim().toUpperCase());
        v.setName(req.name());
        v.setDiscountType(req.discountType());
        v.setDiscountValue(req.discountValue());
        v.setMaxDiscountAmount(req.maxDiscountAmount());
        v.setMinimumOrderValue(req.minimumOrderValue());
        v.setUsageLimit(req.usageLimit());
        v.setStartDate(req.startDate());
        v.setEndDate(req.endDate());
        v.setStatus(VoucherStatus.ACTIVE);
        return toDto(voucherRepo.save(v));
    }

    @Transactional
    public VoucherDto toggleStatus(UUID id) {
        Voucher v = voucherRepo.findById(id)
                .orElseThrow(() -> new AuthException(CustomCode.VOUCHER_NOT_FOUND));
        v.setStatus(v.getStatus() == VoucherStatus.ACTIVE ? VoucherStatus.INACTIVE : VoucherStatus.ACTIVE);
        return toDto(voucherRepo.save(v));
    }
}
