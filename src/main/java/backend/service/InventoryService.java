package backend.service;

import backend.dto.inventory.CreateReceiptRequest;
import backend.dto.inventory.InventoryReceiptDto;
import backend.model.entity.Order;
import backend.model.enums.ReceiptType;
import org.springframework.data.domain.Page;

import java.util.UUID;

/**
 * Inventory management: manual stock imports and exports with receipt tracking.
 */
public interface InventoryService {

    /**
     * Create a new receipt and apply stock delta:
     * - IMPORT  → increases product.stock
     * - EXPORT  → decreases product.stock (with underflow guard)
     */
    InventoryReceiptDto createReceipt(UUID currentUserId, CreateReceiptRequest request);

    /** Get paginated receipt list, optionally filtered by ReceiptType. */
    Page<InventoryReceiptDto> listReceipts(ReceiptType type, int page, int size);

    /** Get single receipt with all items. */
    InventoryReceiptDto getReceipt(UUID id);

    /**
     * Auto-generate EXPORT receipt when an order is marked DELIVERED.
     * java-pro: called from OrderServiceImpl inside existing @Transactional boundary.
     * No underflow check here — stock was already deducted at order creation time.
     *
     * @param order the delivered order (items must be eagerly loaded)
     */
    void autoExportOnDelivery(Order order);
}
