package backend.repository;

import backend.model.entity.InventoryReceipt;
import backend.model.enums.ReceiptType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryReceiptRepository extends JpaRepository<InventoryReceipt, UUID> {

    Page<InventoryReceipt> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<InventoryReceipt> findByTypeOrderByCreatedAtDesc(ReceiptType type, Pageable pageable);

    /** Fetch receipt with items and products in one query — prevents N+1. */
    @Query("""
        SELECT DISTINCT r FROM InventoryReceipt r
        LEFT JOIN FETCH r.items i
        LEFT JOIN FETCH i.product
        WHERE r.id = :id
    """)
    Optional<InventoryReceipt> findByIdWithItems(UUID id);
}
