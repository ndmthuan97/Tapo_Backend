package backend.service.impl;

import backend.dto.common.CustomCode;
import backend.dto.flashsale.FlashSaleRequest;
import backend.exception.AppException;
import backend.model.entity.FlashSale;
import backend.model.entity.Product;
import backend.model.enums.FlashSaleStatus;
import backend.repository.FlashSaleRepository;
import backend.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for FlashSaleServiceImpl — testing-patterns skill applied:
 * - Factory pattern: stubFlashSale(), stubProduct(), stubRequest()
 * - One behaviour per test — no shared mutable state
 * - Nested describe blocks for CRUD + Scheduler sections
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlashSaleServiceImpl — Unit Tests")
class FlashSaleServiceImplTest {

    @Mock FlashSaleRepository flashSaleRepo;
    @Mock ProductRepository   productRepo;

    @InjectMocks FlashSaleServiceImpl flashSaleService;

    // ── createFlashSale ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createFlashSale()")
    class CreateFlashSale {

        @Test
        @DisplayName("invalid time range (end before start) → throws BAD_REQUEST")
        void createFlashSale_invalidTimeRange_throwsBadRequest() {
            Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
            Instant end   = Instant.now();                           // end < start
            var request = stubRequest(UUID.randomUUID(), start, end);

            assertThatThrownBy(() -> flashSaleService.createFlashSale(request))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining(CustomCode.BAD_REQUEST.getDefaultMessage());

            then(flashSaleRepo).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("product not found → throws PRODUCT_NOT_FOUND")
        void createFlashSale_productNotFound_throwsProductNotFound() {
            UUID productId = UUID.randomUUID();
            Instant start  = Instant.now().plus(1, ChronoUnit.HOURS);
            Instant end    = start.plus(2, ChronoUnit.HOURS);
            var request    = stubRequest(productId, start, end);

            given(productRepo.findById(productId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> flashSaleService.createFlashSale(request))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining(CustomCode.PRODUCT_NOT_FOUND.getDefaultMessage());
        }

        @Test
        @DisplayName("valid request → saves FlashSale with SCHEDULED status")
        void createFlashSale_valid_savedScheduled() {
            UUID productId = UUID.randomUUID();
            Product product = stubProduct(productId);
            Instant start   = Instant.now().plus(1, ChronoUnit.HOURS);
            Instant end     = start.plus(2, ChronoUnit.HOURS);
            var request     = stubRequest(productId, start, end);

            given(productRepo.findById(productId)).willReturn(Optional.of(product));
            given(flashSaleRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

            flashSaleService.createFlashSale(request);

            then(flashSaleRepo).should().save(argThat(fs ->
                    fs.getStatus() == FlashSaleStatus.SCHEDULED &&
                    fs.getSoldCount() == 0
            ));
        }
    }

    // ── updateFlashSale ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateFlashSale()")
    class UpdateFlashSale {

        @Test
        @DisplayName("ACTIVE sale → throws FLASH_SALE_CANNOT_MODIFY")
        void updateFlashSale_activeSale_throwsCannotModify() {
            UUID id = UUID.randomUUID();
            FlashSale fs = stubFlashSale(FlashSaleStatus.ACTIVE);
            given(flashSaleRepo.findById(id)).willReturn(Optional.of(fs));

            Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
            Instant end   = start.plus(2, ChronoUnit.HOURS);

            assertThatThrownBy(() -> flashSaleService.updateFlashSale(id, stubRequest(UUID.randomUUID(), start, end)))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining(CustomCode.FLASH_SALE_CANNOT_MODIFY.getDefaultMessage());
        }

        @Test
        @DisplayName("SCHEDULED sale → updates fields and saves")
        void updateFlashSale_scheduled_updatesAndSaves() {
            UUID id = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            FlashSale fs = stubFlashSale(FlashSaleStatus.SCHEDULED);
            Product product = stubProduct(productId);
            Instant start   = Instant.now().plus(1, ChronoUnit.HOURS);
            Instant end     = start.plus(3, ChronoUnit.HOURS);
            var request     = new FlashSaleRequest(productId, BigDecimal.valueOf(8_000_000), 20, start, end);

            given(flashSaleRepo.findById(id)).willReturn(Optional.of(fs));
            given(productRepo.findById(productId)).willReturn(Optional.of(product));
            given(flashSaleRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

            flashSaleService.updateFlashSale(id, request);

            then(flashSaleRepo).should().save(argThat(updated ->
                    updated.getSalePrice().compareTo(BigDecimal.valueOf(8_000_000)) == 0 &&
                    updated.getStockLimit() == 20
            ));
        }
    }

    // ── deleteFlashSale ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteFlashSale()")
    class DeleteFlashSale {

        @Test
        @DisplayName("non-SCHEDULED sale → throws FLASH_SALE_CANNOT_MODIFY")
        void deleteFlashSale_nonScheduled_throwsCannotModify() {
            UUID id = UUID.randomUUID();
            FlashSale fs = stubFlashSale(FlashSaleStatus.ENDED);
            given(flashSaleRepo.findById(id)).willReturn(Optional.of(fs));

            assertThatThrownBy(() -> flashSaleService.deleteFlashSale(id))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining(CustomCode.FLASH_SALE_CANNOT_MODIFY.getDefaultMessage());

            then(flashSaleRepo).should(never()).delete(any());
        }

        @Test
        @DisplayName("SCHEDULED sale → deletes")
        void deleteFlashSale_scheduled_deletesEntity() {
            UUID id = UUID.randomUUID();
            FlashSale fs = stubFlashSale(FlashSaleStatus.SCHEDULED);
            given(flashSaleRepo.findById(id)).willReturn(Optional.of(fs));

            flashSaleService.deleteFlashSale(id);

            then(flashSaleRepo).should().delete(fs);
        }
    }

    // ── processFlashSaleSchedule ───────────────────────────────────────────────

    @Nested
    @DisplayName("processFlashSaleSchedule() [Scheduler]")
    class ProcessSchedule {

        @Test
        @DisplayName("no sales due → no saves called")
        void processSchedule_noSalesDue_noop() {
            given(flashSaleRepo.findDueToActivate(any())).willReturn(List.of());
            given(flashSaleRepo.findDueToExpire(any())).willReturn(List.of());

            flashSaleService.processFlashSaleSchedule();

            then(flashSaleRepo).should(never()).saveAll(any());
        }

        @Test
        @DisplayName("sale due to activate → status set to ACTIVE and saved")
        void processSchedule_scheduledDue_activates() {
            FlashSale scheduled = stubFlashSale(FlashSaleStatus.SCHEDULED);
            given(flashSaleRepo.findDueToActivate(any())).willReturn(List.of(scheduled));
            given(flashSaleRepo.findDueToExpire(any())).willReturn(List.of());

            flashSaleService.processFlashSaleSchedule();

            assertThat(scheduled.getStatus()).isEqualTo(FlashSaleStatus.ACTIVE);
            then(flashSaleRepo).should().saveAll(List.of(scheduled));
        }

        @Test
        @DisplayName("active sale past end time → status set to ENDED and saved")
        void processSchedule_activePastEnd_expires() {
            FlashSale active = stubFlashSale(FlashSaleStatus.ACTIVE);
            given(flashSaleRepo.findDueToActivate(any())).willReturn(List.of());
            given(flashSaleRepo.findDueToExpire(any())).willReturn(List.of(active));

            flashSaleService.processFlashSaleSchedule();

            assertThat(active.getStatus()).isEqualTo(FlashSaleStatus.ENDED);
            then(flashSaleRepo).should().saveAll(List.of(active));
        }
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    /** Factory: FlashSale with given status */
    private FlashSale stubFlashSale(FlashSaleStatus status) {
        Product product = stubProduct(UUID.randomUUID());
        FlashSale fs = new FlashSale();
        fs.setId(UUID.randomUUID());
        fs.setProduct(product);
        fs.setSalePrice(BigDecimal.valueOf(9_000_000));
        fs.setStockLimit(10);
        fs.setSoldCount(2);
        fs.setStatus(status);
        fs.setStartTime(Instant.now().minus(1, ChronoUnit.HOURS));
        fs.setEndTime(Instant.now().plus(1, ChronoUnit.HOURS));
        return fs;
    }

    /** Factory: Product */
    private Product stubProduct(UUID id) {
        Product p = new Product();
        p.setId(id);
        p.setName("Laptop Flash");
        p.setSlug("laptop-flash");
        p.setPrice(BigDecimal.valueOf(10_000_000));
        p.setOriginalPrice(BigDecimal.valueOf(12_000_000));
        return p;
    }

    /** Factory: FlashSaleRequest */
    private FlashSaleRequest stubRequest(UUID productId, Instant start, Instant end) {
        return new FlashSaleRequest(productId, BigDecimal.valueOf(9_000_000), 10, start, end);
    }
}
