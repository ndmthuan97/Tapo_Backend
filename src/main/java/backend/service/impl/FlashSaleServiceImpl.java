package backend.service.impl;

import backend.dto.common.CustomCode;
import backend.dto.flashsale.FlashSaleDto;
import backend.dto.flashsale.FlashSaleRequest;
import backend.exception.AppException;
import backend.model.entity.FlashSale;
import backend.model.entity.Product;
import backend.model.enums.FlashSaleStatus;
import backend.repository.FlashSaleRepository;
import backend.repository.ProductRepository;
import backend.service.FlashSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Flash Sale service implementation.
 *
 * <p>java-pro: @Scheduled auto-activate/expire runs every 60s.
 * Transactional write + read-only separation for optimal performance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleServiceImpl implements FlashSaleService {

    private final FlashSaleRepository flashSaleRepo;
    private final ProductRepository   productRepo;

    // ── Admin CRUD ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FlashSaleDto createFlashSale(FlashSaleRequest request) {
        validateTimeRange(request.startTime(), request.endTime());

        Product product = productRepo.findById(request.productId())
                .orElseThrow(() -> new AppException(CustomCode.PRODUCT_NOT_FOUND));

        FlashSale fs = new FlashSale();
        fs.setProduct(product);
        fs.setSalePrice(request.salePrice());
        fs.setStockLimit(request.stockLimit());
        fs.setStartTime(request.startTime());
        fs.setEndTime(request.endTime());
        fs.setStatus(FlashSaleStatus.SCHEDULED);
        fs.setSoldCount(0);

        return toDto(flashSaleRepo.save(fs));
    }

    @Override
    @Transactional
    public FlashSaleDto updateFlashSale(UUID id, FlashSaleRequest request) {
        FlashSale fs = findFlashSale(id);

        if (fs.getStatus() != FlashSaleStatus.SCHEDULED) {
            throw new AppException(CustomCode.FLASH_SALE_CANNOT_MODIFY);
        }
        validateTimeRange(request.startTime(), request.endTime());

        Product product = productRepo.findById(request.productId())
                .orElseThrow(() -> new AppException(CustomCode.PRODUCT_NOT_FOUND));

        fs.setProduct(product);
        fs.setSalePrice(request.salePrice());
        fs.setStockLimit(request.stockLimit());
        fs.setStartTime(request.startTime());
        fs.setEndTime(request.endTime());

        return toDto(flashSaleRepo.save(fs));
    }

    @Override
    @Transactional
    public void deleteFlashSale(UUID id) {
        FlashSale fs = findFlashSale(id);
        if (fs.getStatus() != FlashSaleStatus.SCHEDULED) {
            throw new AppException(CustomCode.FLASH_SALE_CANNOT_MODIFY);
        }
        flashSaleRepo.delete(fs);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<FlashSaleDto> listFlashSales(FlashSaleStatus status) {
        List<FlashSale> all = (status != null)
                ? flashSaleRepo.findByStatus(status)
                : flashSaleRepo.findAllByOrderByStartTimeDesc();
        return all.stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlashSaleDto> getActiveSales() {
        return flashSaleRepo.findActiveNow(Instant.now())
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ── Scheduler ──────────────────────────────────────────────────────────────

    /**
     * Auto-activate SCHEDULED sales and expire ACTIVE ones every 60 seconds.
     * java-pro: @Scheduled(fixedDelay) — runs 60s after each completion.
     */
    @Override
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processFlashSaleSchedule() {
        Instant now = Instant.now();

        // Activate scheduled → active
        List<FlashSale> toActivate = flashSaleRepo.findDueToActivate(now);
        if (!toActivate.isEmpty()) {
            toActivate.forEach(fs -> fs.setStatus(FlashSaleStatus.ACTIVE));
            flashSaleRepo.saveAll(toActivate);
            log.info("[FlashSale] Activated {} sales", toActivate.size());
        }

        // Expire active → ended
        List<FlashSale> toExpire = flashSaleRepo.findDueToExpire(now);
        if (!toExpire.isEmpty()) {
            toExpire.forEach(fs -> fs.setStatus(FlashSaleStatus.ENDED));
            flashSaleRepo.saveAll(toExpire);
            log.info("[FlashSale] Expired {} sales", toExpire.size());
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private FlashSale findFlashSale(UUID id) {
        return flashSaleRepo.findById(id)
                .orElseThrow(() -> new AppException(CustomCode.FLASH_SALE_NOT_FOUND));
    }

    private void validateTimeRange(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new AppException(CustomCode.BAD_REQUEST);
        }
    }

    private FlashSaleDto toDto(FlashSale fs) {
        Product p = fs.getProduct();
        Instant now = Instant.now();

        int discountPct = 0;
        if (p.getOriginalPrice() != null && p.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0) {
            discountPct = fs.getSalePrice()
                    .subtract(p.getOriginalPrice())
                    .abs()
                    .divide(p.getOriginalPrice(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
        }

        long remainingSec = Math.max(0, fs.getEndTime().getEpochSecond() - now.getEpochSecond());

        return new FlashSaleDto(
                fs.getId(),
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getThumbnailUrl(),
                p.getOriginalPrice() != null ? p.getOriginalPrice() : p.getPrice(),
                fs.getSalePrice(),
                discountPct,
                fs.getStockLimit(),
                fs.getSoldCount(),
                Math.max(0, fs.getStockLimit() - fs.getSoldCount()),
                fs.getStartTime(),
                fs.getEndTime(),
                fs.getStatus(),
                remainingSec
        );
    }
}
