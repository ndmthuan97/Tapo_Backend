package backend.service.impl;

import backend.dto.common.CustomCode;
import backend.dto.inventory.CreateReceiptRequest;
import backend.dto.inventory.InventoryReceiptDto;
import backend.exception.AppException;
import backend.model.entity.*;
import backend.model.enums.ReceiptType;
import backend.model.enums.UserRole;
import backend.repository.InventoryReceiptRepository;
import backend.repository.ProductRepository;
import backend.repository.UserRepository;
import backend.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Inventory service implementation.
 *
 * <p>java-pro: @Transactional ensures DB + stock updates are atomic —
 * prevents partial inventory corruption on failure (Race Condition guard).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryReceiptRepository receiptRepo;
    private final ProductRepository          productRepo;
    private final UserRepository             userRepo;

    // Auto-increment counter for receipt codes (in production: use DB sequence)
    private static final AtomicInteger RECEIPT_COUNTER = new AtomicInteger(1000);

    @Override
    @Transactional
    public InventoryReceiptDto createReceipt(UUID currentUserId, CreateReceiptRequest request) {
        User createdBy = userRepo.findById(currentUserId)
                .orElseThrow(() -> new AppException(CustomCode.USER_NOT_FOUND));

        InventoryReceipt receipt = new InventoryReceipt();
        String code = String.format("%s-%05d",
                request.type() == ReceiptType.IMPORT ? "IMP" : "EXP",
                RECEIPT_COUNTER.incrementAndGet());
        receipt.setReceiptCode(code);
        receipt.setType(request.type());
        receipt.setCreatedBy(createdBy);
        receipt.setNote(request.note());

        List<InventoryReceiptItem> items = new ArrayList<>();
        for (CreateReceiptRequest.ItemEntry entry : request.items()) {
            Product product = productRepo.findById(entry.productId())
                    .orElseThrow(() -> new AppException(CustomCode.PRODUCT_NOT_FOUND));

            // Apply stock delta — @Transactional guarantees atomicity
            int delta = entry.quantity();
            if (request.type() == ReceiptType.IMPORT) {
                product.setStock(product.getStock() + delta);
            } else {
                if (product.getStock() < delta) {
                    throw new AppException(CustomCode.INSUFFICIENT_STOCK);
                }
                product.setStock(product.getStock() - delta);
            }
            productRepo.save(product);

            InventoryReceiptItem item = new InventoryReceiptItem();
            item.setReceipt(receipt);
            item.setProduct(product);
            item.setQuantity(delta);
            item.setUnitPrice(entry.unitPrice());
            items.add(item);
        }

        receipt.setItems(items);
        InventoryReceipt saved = receiptRepo.save(receipt);
        log.info("[Inventory] {} receipt {} created with {} items",
                request.type(), saved.getReceiptCode(), items.size());

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryReceiptDto> listReceipts(ReceiptType type, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<InventoryReceipt> receipts = (type != null)
                ? receiptRepo.findByTypeOrderByCreatedAtDesc(type, pageable)
                : receiptRepo.findAllByOrderByCreatedAtDesc(pageable);
        return receipts.map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryReceiptDto getReceipt(UUID id) {
        return toDto(receiptRepo.findByIdWithItems(id)
                .orElseThrow(() -> new AppException(CustomCode.INVENTORY_RECEIPT_NOT_FOUND)));
    }

    /**
     * Auto-generate EXPORT receipt when an order is DELIVERED.
     *
     * <p>java-pro: Runs within the caller's @Transactional boundary (Propagation.REQUIRED default).
     * Stock was already decremented at order creation — this method only creates the audit trail.
     * unitPrice is set to ZERO (no cost entry for automatic export receipts).
     */
    @Override
    @Transactional
    public void autoExportOnDelivery(Order order) {
        // Use SYSTEM user as creator for auto-generated receipts
        User systemUser = userRepo.findFirstByRoleOrderByCreatedAtAsc(UserRole.ADMIN)
                .orElse(null);
        // If no admin user found (edge case in dev), skip receipt creation
        if (systemUser == null) {
            log.warn("[Inventory] autoExportOnDelivery: no system user found, skipping receipt for order {}",
                    order.getOrderCode());
            return;
        }

        InventoryReceipt receipt = new InventoryReceipt();
        String code = String.format("EXP-AUTO-%05d", RECEIPT_COUNTER.incrementAndGet());
        receipt.setReceiptCode(code);
        receipt.setType(ReceiptType.EXPORT);
        receipt.setCreatedBy(systemUser);
        receipt.setOrder(order);
        receipt.setNote("Tự động tạo khi đơn hàng " + order.getOrderCode() + " chuyển sang DELIVERED");

        List<InventoryReceiptItem> items = new ArrayList<>();
        for (OrderItem oi : order.getItems()) {
            Product product = oi.getProduct();
            // Stock was already decremented at order creation — only record the audit
            InventoryReceiptItem item = new InventoryReceiptItem();
            item.setReceipt(receipt);
            item.setProduct(product);
            item.setQuantity(oi.getQuantity());
            item.setUnitPrice(BigDecimal.ZERO); // Cost not tracked for auto-export
            items.add(item);
        }

        receipt.setItems(items);
        receiptRepo.save(receipt);
        log.info("[Inventory] Auto-EXPORT receipt {} created for delivered order {}",
                code, order.getOrderCode());
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private InventoryReceiptDto toDto(InventoryReceipt r) {
        List<InventoryReceiptDto.ItemDto> itemDtos = r.getItems() == null
                ? List.of()
                : r.getItems().stream().map(i -> {
                    BigDecimal lineTotal = i.getUnitPrice()
                            .multiply(BigDecimal.valueOf(i.getQuantity()));
                    return new InventoryReceiptDto.ItemDto(
                            i.getProduct().getId(),
                            i.getProduct().getName(),
                            i.getProduct().getThumbnailUrl(),
                            i.getQuantity(),
                            i.getUnitPrice(),
                            lineTotal
                    );
                }).toList();

        String orderCode = (r.getOrder() != null) ? r.getOrder().getOrderCode() : null;

        return new InventoryReceiptDto(
                r.getId(),
                r.getReceiptCode(),
                r.getType(),
                r.getCreatedBy().getFullName(),
                orderCode,
                r.getNote(),
                itemDtos,
                r.getCreatedAt()
        );
    }
}
