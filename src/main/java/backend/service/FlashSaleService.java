package backend.service;

import backend.dto.flashsale.FlashSaleDto;
import backend.dto.flashsale.FlashSaleRequest;
import backend.model.enums.FlashSaleStatus;

import java.util.List;
import java.util.UUID;

/**
 * Flash Sale service — manages limited-time discounts with auto-scheduling.
 */
public interface FlashSaleService {

    /** Admin: create a new flash sale — must be SCHEDULED in the future. */
    FlashSaleDto createFlashSale(FlashSaleRequest request);

    /** Admin: update a SCHEDULED flash sale. */
    FlashSaleDto updateFlashSale(UUID id, FlashSaleRequest request);

    /** Admin: delete a SCHEDULED flash sale only (cannot delete ACTIVE/ENDED). */
    void deleteFlashSale(UUID id);

    /** Admin: list all flash sales, optionally filtered by status. */
    List<FlashSaleDto> listFlashSales(FlashSaleStatus status);

    /** Public: list currently ACTIVE flash sales within their time window. */
    List<FlashSaleDto> getActiveSales();

    /** Scheduled job: auto-activate/expire flash sales based on time. */
    void processFlashSaleSchedule();
}
